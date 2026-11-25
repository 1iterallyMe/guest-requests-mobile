package ru.nto.guestmanagement.mobile.ui.login

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.nto.guestmanagement.mobile.Constants
import ru.nto.guestmanagement.mobile.data.TimeProvider
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.di.DataStorePaths
import ru.nto.guestmanagement.mobile.testing.FakeBackend
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.theme.GuestManagementTheme

/**
 * Локальная UI-проверка входа под Robolectric (API 35):
 * доступность кнопки по формату кода, пропуск входа по сохранённому коду и сообщение об истёкшей сессии.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var backend: FakeBackend

    @Before
    fun setUp() {
        backend = FakeBackend()
        Constants.HOST = backend.url()
    }

    @After
    fun tearDown() {
        backend.close()
    }

    @Test
    fun submitEnabledOnlyForFourAlphanumericCharacters() {
        val container = newContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            GuestManagementTheme {
                LoginScreen(container, sessionExpired = false, onLoggedIn = {})
            }
        }
        awaitLoginForm()

        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextInput("abc")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsNotEnabled()

        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextInput("d")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsEnabled()

        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextInput("e")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsNotEnabled()

        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextClearance()
        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextInput("ab c")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsNotEnabled()

        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextClearance()
        compose.onNodeWithTag(TestIds.AUTH_CODE).performTextInput("ABCD")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsEnabled()
    }

    @Test
    fun savedCodeSkipsLoginWithoutAuthRequest() {
        val container = newContainer(ApplicationProvider.getApplicationContext())
        runBlocking { container.session.save("1234") }

        val loggedIn = AtomicBoolean(false)
        compose.setContent {
            GuestManagementTheme {
                LoginScreen(container, sessionExpired = false, onLoggedIn = { loggedIn.set(true) })
            }
        }

        compose.waitUntil(10_000) { loggedIn.get() }
        compose.onAllNodesWithTag(TestIds.AUTH_CODE).assertCountEquals(0)
        assertTrue(backend.calls.isEmpty())
    }

    @Test
    fun sessionExpiredMessageIsShownOnLogin() {
        val container = newContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            GuestManagementTheme {
                LoginScreen(container, sessionExpired = true, onLoggedIn = {})
            }
        }
        awaitLoginForm()

        compose.onNodeWithTag(TestIds.AUTH_SESSION_EXPIRED).assertTextEquals("Сессия завершена")
        compose.onNodeWithTag(TestIds.AUTH_SUBMIT).assertIsNotEnabled()
    }

    private fun awaitLoginForm() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(TestIds.AUTH_CODE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun newContainer(context: Context): AppContainer {
        // Уникальный каталог на тест: AppContainer кэширует DataStore по пути файла.
        val dir = File(context.filesDir, "session-test-${System.nanoTime()}").apply { mkdirs() }
        DataStorePaths.dir = dir
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, DataStorePaths.SESSION_FILE) }
        return AppContainer(
            engine = CIO.create(),
            dataStore = dataStore,
            timeProvider = object : TimeProvider {
                override fun now(): LocalDateTime = LocalDateTime.of(2026, 10, 14, 9, 0, 0)
            },
        )
    }
}