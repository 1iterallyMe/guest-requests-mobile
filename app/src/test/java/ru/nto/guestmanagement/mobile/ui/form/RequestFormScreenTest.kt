package ru.nto.guestmanagement.mobile.ui.form

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * Локальные UI-проверки формы: ошибки только после отправки и вне полей, нормализованное тело запроса,
 * сообщение о дубликате и повторная доступность формы.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestFormScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var backend: FakeBackend
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        backend = FakeBackend()
        Constants.HOST = backend.url()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "form-test-${System.nanoTime()}").apply { mkdirs() }
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
    fun errorsAppearOnlyAfterSubmitAndBlockRequest() {
        showForm()
        compose.onAllNodesWithTag(TestIds.FORM_ERROR_FULL_NAME).assertCountEquals(0)
        compose.onAllNodesWithTag(TestIds.FORM_ERROR_DATE).assertCountEquals(0)

        setText(TestIds.FORM_DATE, "31.02.2026")
        setText(TestIds.FORM_TIME_FROM, "9:00")
        setText(TestIds.FORM_TIME_TO, "24:00")
        compose.onNodeWithTag(TestIds.FORM_SUBMIT).performClick()

        pumpUntil { compose.onAllNodesWithTag(TestIds.FORM_ERROR_DATE).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(TestIds.FORM_ERROR_FULL_NAME).assertExists()
        compose.onNodeWithTag(TestIds.FORM_ERROR_PHONE).assertExists()
        compose.onNodeWithTag(TestIds.FORM_ERROR_PURPOSE).assertExists()
        compose.onNodeWithTag(TestIds.FORM_ERROR_DATE).assertExists()
        compose.onNodeWithTag(TestIds.FORM_ERROR_VALID_FROM).assertExists()
        compose.onNodeWithTag(TestIds.FORM_ERROR_VALID_TO).assertExists()
        compose.onAllNodesWithTag(TestIds.FORM_ERROR_ORGANIZATION).assertCountEquals(0)
        assertTrue(backend.calls.none { it.startsWith("POST") })
    }

    @Test
    fun validFormSendsNormalizedBodyAndReportsSuccess() {
        backend.json("POST", "/api/$CODE/requests", CREATED_DETAILS, status = 201)
        val created = AtomicBoolean(false)
        showForm(onCreated = { created.set(true) })

        setText(TestIds.FORM_FULL_NAME, "  Сидоров Пётр Ильич  ")
        setText(TestIds.FORM_PHONE, " +79991234567 ")
        setText(TestIds.FORM_ORGANIZATION, "   ")
        setText(TestIds.FORM_PURPOSE, "Встреча с сотрудником")
        setText(TestIds.FORM_DATE, "14.10.2026")
        setText(TestIds.FORM_TIME_FROM, "10:00")
        setText(TestIds.FORM_TIME_TO, "16:00")

        compose.onNodeWithTag(TestIds.FORM_SUBMIT).performClick()
        pumpUntil { created.get() }

        val body = JSONObject(backend.bodies.last { it.isNotBlank() })
        assertEquals("Сидоров Пётр Ильич", body.getString("fullName"))
        assertEquals("+79991234567", body.getString("phone"))
        assertTrue(body.isNull("organization"))
        assertEquals("Встреча с сотрудником", body.getString("purpose"))
        assertEquals("2026-10-14T10:00:00", body.getString("validFrom"))
        assertEquals("2026-10-14T16:00:00", body.getString("validTo"))
        compose.onAllNodesWithTag(TestIds.FORM_ERROR_COMMON).assertCountEquals(0)
    }

    @Test
    fun duplicateConflictShowsCommonMessageAndKeepsInput() {
        backend.json(
            "POST",
            "/api/$CODE/requests",
            """{"error":"Duplicate request"}""",
            status = 409,
        )
        showForm()
        fillValidForm()
        compose.onNodeWithTag(TestIds.FORM_SUBMIT).performClick()

        pumpUntil { compose.onAllNodesWithTag(TestIds.FORM_ERROR_COMMON).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(TestIds.FORM_ERROR_COMMON)
            .assertIsDisplayed()
            .assertTextEquals("На это время уже есть заявка для указанного телефона")
        compose.onNodeWithTag(TestIds.FORM_FULL_NAME).assert(hasText("Сидоров Пётр Ильич"))
    }

    @Test
    fun loadingIsShownOutsideButtonAndOnlyOneRequestIsSent() {
        backend.handle("POST", "/api/$CODE/requests") {
            FakeBackend.Reply(status = 201, body = CREATED_DETAILS, delayMillis = 800)
        }
        showForm()
        fillValidForm()

        compose.onNodeWithTag(TestIds.FORM_SUBMIT).performClick()
        pumpUntil(15_000) { compose.onAllNodesWithTag(TestIds.FORM_LOADING).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(TestIds.FORM_LOADING)
            .assertIsDisplayed()
            .assert(hasAnyAncestor(hasTestTag(TestIds.FORM_SUBMIT)).not())
        compose.onNodeWithTag(TestIds.FORM_SUBMIT).assertIsNotEnabled()

        pumpUntil(15_000) { compose.onAllNodesWithTag(TestIds.FORM_LOADING).fetchSemanticsNodes().isEmpty() }
        assertEquals(1, backend.calls.count { it == "POST /api/$CODE/requests" })
    }

    private fun showForm(onCreated: () -> Unit = {}) {
        compose.setContent {
            GuestManagementTheme {
                RequestFormScreen(
                    container = container,
                    onBack = {},
                    onCreated = onCreated,
                    onSessionExpired = {},
                )
            }
        }
    }

    private fun fillValidForm() {
        setText(TestIds.FORM_FULL_NAME, "Сидоров Пётр Ильич")
        setText(TestIds.FORM_PHONE, "+79991234567")
        setText(TestIds.FORM_ORGANIZATION, "ООО НТО")
        setText(TestIds.FORM_PURPOSE, "Встреча с сотрудником")
        setText(TestIds.FORM_DATE, "14.10.2026")
        setText(TestIds.FORM_TIME_FROM, "10:00")
        setText(TestIds.FORM_TIME_TO, "16:00")
    }

    private fun setText(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(value)
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

        val CREATED_DETAILS = """
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
    }
}