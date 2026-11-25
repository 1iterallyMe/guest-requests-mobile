package ru.nto.guestmanagement.mobile.ui.list

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Локальные UI-проверки списка на настоящем HTTP к локальному серверу: один `GET` при открытии,
 * доступность вложенных элементов карточки, фильтры, ошибки и выход.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var backend: FakeBackend
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        backend = FakeBackend()
        Constants.HOST = backend.url()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "list-test-${System.nanoTime()}").apply { mkdirs() }
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
    fun openingSendsSingleGetAndCardFieldsAreReadableFromMergedTree() {
        backend.json("GET", "/api/$CODE/requests", TWO_ITEMS)
        showList()

        awaitTag(TestIds.requestsPos(1))
        compose.waitForIdle()
        Thread.sleep(300)

        assertEquals(1, backend.calls.count { it == "GET /api/$CODE/requests" })
        compose.onNode(hasTestTag(TestIds.REQUEST_NAME) and hasAnyAncestor(hasTestTag(TestIds.requestsPos(0))))
            .assertTextEquals("Сидоров Пётр Ильич")
        compose.onNode(hasTestTag(TestIds.REQUEST_DATE) and hasAnyAncestor(hasTestTag(TestIds.requestsPos(0))))
            .assertTextEquals("14.10.2026")
        compose.onNode(hasTestTag(TestIds.REQUEST_TIME) and hasAnyAncestor(hasTestTag(TestIds.requestsPos(0))))
            .assertTextEquals("10:00\u201316:00")
        compose.onNode(hasTestTag(TestIds.REQUEST_STATUS) and hasAnyAncestor(hasTestTag(TestIds.requestsPos(0))))
            .assertTextEquals("На рассмотрении")
    }

    @Test
    fun cardClickOpensDetailsWithRequestId() {
        backend.json("GET", "/api/$CODE/requests", ONE_PENDING)
        val opened = AtomicLong(-1)
        showList(onOpenDetails = { opened.set(it) })
        awaitTag(TestIds.requestsPos(0))

        compose.onNodeWithTag(TestIds.requestsPos(0)).performClick()
        pumpUntil { opened.get() == 12L }
        assertEquals(12L, opened.get())
    }

    @Test
    fun returningToScreenTriggersOneMoreLoad() {
        backend.json("GET", "/api/$CODE/requests", ONE_PENDING)
        val visible = mutableStateOf(true)
        compose.setContent {
            GuestManagementTheme {
                if (visible.value) {
                    RequestListScreen(
                        container = container,
                        onOpenDetails = {},
                        onAddRequest = {},
                        onLogout = {},
                        onSessionExpired = {},
                    )
                }
            }
        }
        awaitTag(TestIds.requestsPos(0))
        pumpUntil { backend.calls.count { it == "GET /api/$CODE/requests" } == 1 }

        // Уход с экрана и возврат: композиция создаётся заново, ViewModel сохраняет состояние.
        visible.value = false
        compose.waitForIdle()
        visible.value = true
        compose.waitForIdle()

        pumpUntil { backend.calls.count { it == "GET /api/$CODE/requests" } == 2 }
    }

    private fun showList(
        onOpenDetails: (Long) -> Unit = {},
        onAddRequest: () -> Unit = {},
        onLogout: () -> Unit = {},
        onSessionExpired: () -> Unit = {},
    ) {
        compose.setContent {
            GuestManagementTheme {
                RequestListScreen(
                    container = container,
                    onOpenDetails = onOpenDetails,
                    onAddRequest = onAddRequest,
                    onLogout = onLogout,
                    onSessionExpired = onSessionExpired,
                )
            }
        }
    }

    private fun awaitTag(tag: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            println("TIMEOUT tag=$tag calls=${backend.calls}")
            println(compose.onRoot().printToString())
            throw e
        }
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

    @Test
    fun filterSendsStatusParameterAndKeepsSelection() {
        backend.handle("GET", "/api/$CODE/requests") { query ->
            if (query.contains("status=PENDING")) {
                FakeBackend.Reply(status = 200, body = ONE_PENDING)
            } else {
                FakeBackend.Reply(status = 200, body = TWO_ITEMS)
            }
        }
        showList()
        awaitTag(TestIds.requestsPos(1))

        compose.onNodeWithTag(TestIds.REQUESTS_FILTER_PENDING).performClick()
        pumpUntil { backend.calls.any { it == "GET /api/$CODE/requests?status=PENDING" } }
        pumpUntil {
            compose.onAllNodesWithText("Петров Пётр Петрович").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(TestIds.REQUESTS_FILTER_PENDING).assertIsSelected()
        compose.onNodeWithTag(TestIds.REQUESTS_FILTER_ALL).assertIsNotSelected()
    }

    @Test
    fun initialErrorShowsMessageAndRefreshRetries() {
        backend.json("GET", "/api/$CODE/requests", """{"error":"Internal"}""", status = 500)
        showList()

        awaitTag(TestIds.REQUESTS_ERROR)
        compose.onAllNodesWithTag(TestIds.REQUESTS_OFFLINE).assertCountEquals(0)
        compose.onNodeWithTag(TestIds.REQUESTS_REFRESH).assertExists()

        backend.json("GET", "/api/$CODE/requests", ONE_PENDING)
        compose.onNodeWithTag(TestIds.REQUESTS_REFRESH).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(TestIds.REQUESTS_ERROR).fetchSemanticsNodes().isEmpty()
        }
        awaitTag(TestIds.requestsPos(0))
    }

    @Test
    fun reloadErrorKeepsLoadedListAndShowsOffline() {
        backend.json("GET", "/api/$CODE/requests", ONE_PENDING)
        showList()
        awaitTag(TestIds.requestsPos(0))

        backend.json("GET", "/api/$CODE/requests", """{"error":"Internal"}""", status = 500)
        compose.onNodeWithTag(TestIds.REQUESTS_REFRESH).performClick()
        awaitTag(TestIds.REQUESTS_OFFLINE)

        compose.onNodeWithTag(TestIds.requestsPos(0)).assertExists()
        compose.onAllNodesWithTag(TestIds.REQUESTS_ERROR).assertCountEquals(0)
    }

    @Test
    fun logoutClearsSavedCode() {
        backend.json("GET", "/api/$CODE/requests", "[]")
        val loggedOut = AtomicBoolean(false)
        showList(onLogout = { loggedOut.set(true) })
        awaitTag(TestIds.REQUESTS_EMPTY)

        compose.onNodeWithTag(TestIds.REQUESTS_LOGOUT).performClick()
        pumpUntil { loggedOut.get() }
        assertNull(runBlocking { container.session.current() })
    }

    private companion object {
        const val CODE = "1234"

        val TWO_ITEMS = """
            [
              {"id":12,"fullName":"Сидоров Пётр Ильич","validFrom":"2026-10-14T10:00:00","validTo":"2026-10-14T16:00:00","status":"PENDING"},
              {"id":13,"fullName":"Иванов Иван Иванович","validFrom":"2026-10-15T09:00:00","validTo":"2026-10-15T11:00:00","status":"APPROVED"}
            ]
        """.trimIndent()

        val ONE_PENDING = """
            [
              {"id":12,"fullName":"Петров Пётр Петрович","validFrom":"2026-10-14T10:00:00","validTo":"2026-10-14T16:00:00","status":"PENDING"}
            ]
        """.trimIndent()
    }
}