package ru.nto.guestmanagement.mobile.ui.details

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import ru.nto.guestmanagement.mobile.Constants
import ru.nto.guestmanagement.mobile.data.TimeProvider
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.di.DataStorePaths
import ru.nto.guestmanagement.mobile.testing.FakeBackend
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.theme.GuestManagementTheme

/**
 * Локальные UI-проверки деталей: тексты полей без подписей, условные блоки, конфликт отмены
 * (сообщение переживает перечитывание, кнопка исчезает) и повтор после `404`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var backend: FakeBackend
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        backend = FakeBackend()
        Constants.HOST = backend.url()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "details-test-${System.nanoTime()}").apply { mkdirs() }
        DataStorePaths.dir = dir
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, DataStorePaths.SESSION_FILE) }
        container = AppContainer(
            engine = CIO.create(),
            dataStore = dataStore,
            timeProvider = object : TimeProvider {
                override fun now(): LocalDateTime = LocalDateTime.of(2026, 10, 14, 9, 0, 0)
            },
        )
        runBlocking { container.session.save(CODE) }
    }

    @After
    fun tearDown() {
        backend.close()
    }

    @Test
    fun showsAllFieldsAndCommentForRejected() {
        backend.json("GET", "/api/$CODE/requests/12", REJECTED_DETAILS)
        showDetails()
        awaitTag(TestIds.DETAILS_COMMENT)

        assertText(TestIds.DETAILS_ID, "12")
        assertText(TestIds.DETAILS_NAME, "Сидоров Пётр Ильич")
        assertText(TestIds.DETAILS_PHONE, "+79991234567")
        assertText(TestIds.DETAILS_ORGANIZATION, "ООО НТО")
        assertText(TestIds.DETAILS_PURPOSE, "Встреча с сотрудником")
        assertText(TestIds.DETAILS_VALID_FROM, "14.10.2026 10:00")
        assertText(TestIds.DETAILS_VALID_TO, "14.10.2026 16:00")
        assertText(TestIds.DETAILS_STATUS, "Отклонена")
        assertText(TestIds.DETAILS_CREATED_AT, "10.10.2026 12:31")
        assertText(TestIds.DETAILS_UPDATED_AT, "10.10.2026 15:02")
        assertText(TestIds.DETAILS_COMMENT, "Сотрудник отсутствует в указанный день")
        compose.onAllNodesWithTag(TestIds.DETAILS_CANCEL).assertCountEquals(0)
    }

    @Test
    fun organizationIsAbsentWhenNull() {
        backend.json("GET", "/api/$CODE/requests/12", PENDING_WITHOUT_ORGANIZATION)
        showDetails()
        awaitTag(TestIds.DETAILS_NAME)

        compose.onAllNodesWithTag(TestIds.DETAILS_ORGANIZATION).assertCountEquals(0)
        compose.onAllNodesWithTag(TestIds.DETAILS_COMMENT).assertCountEquals(0)
    }

    @Test
    fun cancelConflictShowsMessageKeepsItAfterReloadAndHidesButton() {
        val getCount = AtomicInteger(0)
        backend.handle("GET", "/api/$CODE/requests/12") {
            if (getCount.getAndIncrement() == 0) {
                FakeBackend.Reply(status = 200, body = PENDING_DETAILS)
            } else {
                FakeBackend.Reply(status = 200, body = APPROVED_DETAILS)
            }
        }
        backend.json(
            "POST",
            "/api/$CODE/requests/12/cancel",
            """{"error":"Invalid status transition"}""",
            status = 409,
        )
        showDetails()
        awaitTag(TestIds.DETAILS_CANCEL)

        compose.onNodeWithTag(TestIds.DETAILS_CANCEL).performClick()
        compose.onNodeWithTag(TestIds.DETAILS_CANCEL_CONFIRM).performClick()

        pumpUntil { compose.onAllNodesWithTag(TestIds.DETAILS_CANCEL_ERROR).fetchSemanticsNodes().isNotEmpty() }
        pumpUntil { compose.onAllNodesWithText("Одобрена").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(TestIds.DETAILS_CANCEL_ERROR).assertTextEquals("Заявка уже рассмотрена")
        compose.onNodeWithTag(TestIds.DETAILS_STATUS).assertTextEquals("Одобрена")
        compose.onAllNodesWithTag(TestIds.DETAILS_CANCEL).assertCountEquals(0)
    }

    @Test
    fun initialNotFoundShowsErrorAndRetryLoadsDetails() {
        backend.json("GET", "/api/$CODE/requests/12", """{"error":"Request not found"}""", status = 404)
        showDetails()
        awaitTag(TestIds.DETAILS_ERROR)

        backend.json("GET", "/api/$CODE/requests/12", PENDING_DETAILS)
        compose.onNodeWithTag(TestIds.DETAILS_RETRY).performClick()
        pumpUntil { compose.onAllNodesWithTag(TestIds.DETAILS_NAME).fetchSemanticsNodes().isNotEmpty() }

        compose.onAllNodesWithTag(TestIds.DETAILS_ERROR).assertCountEquals(0)
        assertText(TestIds.DETAILS_STATUS, "На рассмотрении")
    }

    private fun showDetails() {
        compose.setContent {
            GuestManagementTheme {
                RequestDetailsScreen(
                    container = container,
                    requestId = 12,
                    onBack = {},
                    onSessionExpired = {},
                )
            }
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertText(tag: String, expected: String) {
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }

    /** Ждёт условие, попутно прокручивая и Compose-часы, и очередь главного Looper (Robolectric PAUSED). */
    private fun pumpUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeByFrame()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Условие не выполнено за $timeoutMillis мс; calls=${backend.calls}")
    }

    private companion object {
        const val CODE = "1234"

        val PENDING_DETAILS = """
            {
              "id": 12,
              "fullName": "Сидоров Пётр Ильич",
              "phone": "+79991234567",
              "organization": "ООО НТО",
              "purpose": "Встреча с сотрудником",
              "validFrom": "2026-10-14T10:00:00",
              "validTo": "2026-10-14T16:00:00",
              "status": "PENDING",
              "comment": null,
              "createdAt": "2026-10-10T12:31:05",
              "updatedAt": "2026-10-10T12:31:05"
            }
        """.trimIndent()

        val PENDING_WITHOUT_ORGANIZATION = """
            {
              "id": 12,
              "fullName": "Сидоров Пётр Ильич",
              "phone": "+79991234567",
              "organization": null,
              "purpose": "Встреча с сотрудником",
              "validFrom": "2026-10-14T10:00:00",
              "validTo": "2026-10-14T16:00:00",
              "status": "PENDING",
              "comment": null,
              "createdAt": "2026-10-10T12:31:05",
              "updatedAt": "2026-10-10T12:31:05"
            }
        """.trimIndent()

        val APPROVED_DETAILS = """
            {
              "id": 12,
              "fullName": "Сидоров Пётр Ильич",
              "phone": "+79991234567",
              "organization": "ООО НТО",
              "purpose": "Встреча с сотрудником",
              "validFrom": "2026-10-14T10:00:00",
              "validTo": "2026-10-14T16:00:00",
              "status": "APPROVED",
              "comment": null,
              "createdAt": "2026-10-10T12:31:05",
              "updatedAt": "2026-10-10T12:31:05"
            }
        """.trimIndent()

        val REJECTED_DETAILS = """
            {
              "id": 12,
              "fullName": "Сидоров Пётр Ильич",
              "phone": "+79991234567",
              "organization": "ООО НТО",
              "purpose": "Встреча с сотрудником",
              "validFrom": "2026-10-14T10:00:00",
              "validTo": "2026-10-14T16:00:00",
              "status": "REJECTED",
              "comment": "Сотрудник отсутствует в указанный день",
              "createdAt": "2026-10-10T12:31:05",
              "updatedAt": "2026-10-10T15:02:44"
            }
        """.trimIndent()
    }
}