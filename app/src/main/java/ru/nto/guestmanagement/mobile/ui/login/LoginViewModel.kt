package ru.nto.guestmanagement.mobile.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer

/**
 * Состояние экрана входа. Ввод не нормализуется: кнопка активна, только если строка целиком
 * состоит из четырёх латинских букв или цифр.
 */
internal class LoginViewModel(
    private val container: AppContainer,
    sessionExpired: Boolean,
) : ViewModel() {

    private val api = RequestsApi(container.httpClient)

    /** После `401` код уже очищен вызывающим экраном; сохранённый код в этом случае игнорируется. */
    private val skipSavedCode = sessionExpired

    var code by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var errorRes by mutableStateOf<Int?>(null)
        private set
    var checkingSavedCode by mutableStateOf(!sessionExpired)
        private set
    var loggedIn by mutableStateOf(false)
        private set

    val canSubmit: Boolean get() = !loading && CODE_PATTERN.matches(code)

    init {
        if (!skipSavedCode) {
            viewModelScope.launch {
                val saved = container.session.current()
                checkingSavedCode = false
                // Сохранённый код используется как есть: повторный /auth не выполняется.
                if (saved != null) loggedIn = true
            }
        }
    }

    fun onCodeChange(value: String) {
        code = value
        errorRes = null
    }

    fun submit() {
        if (!canSubmit) return
        loading = true
        errorRes = null
        viewModelScope.launch {
            when (val result = api.auth(code)) {
                is ApiResult.Success -> {
                    container.session.save(code)
                    loading = false
                    loggedIn = true
                }
                ApiResult.Unauthorized -> {
                    errorRes = R.string.login_error_unknown_code
                    loading = false
                }
                ApiResult.Network -> {
                    errorRes = R.string.login_error_network
                    loading = false
                }
                else -> {
                    errorRes = R.string.login_error_other
                    loading = false
                }
            }
        }
    }

    private companion object {
        val CODE_PATTERN = Regex("[A-Za-z0-9]{4}")
    }
}