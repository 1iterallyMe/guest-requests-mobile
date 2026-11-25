package ru.nto.guestmanagement.mobile.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestDetails
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate

/**
 * Состояние экрана деталей и отмены. Сообщение о конфликте отмены живёт отдельно от деталей и не сбрасывается
 * при перечитывании: после `409` заявка уже не `PENDING`, кнопка отмены исчезает, а сообщение остаётся.
 */
internal class RequestDetailsViewModel(
    private val container: AppContainer,
    private val requestId: Long,
) : ViewModel() {

    private val api = RequestsApi(container.httpClient)
    private val refreshGate = RefreshGate()

    var details by mutableStateOf<RequestDetails?>(null)
        private set
    var initialError by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set
    var cancelErrorRes by mutableStateOf<Int?>(null)
        private set
    var confirmationVisible by mutableStateOf(false)
        private set
    var cancelling by mutableStateOf(false)
        private set
    var sessionExpired by mutableStateOf(false)
        private set

    fun refresh() {
        refreshGate.launch(viewModelScope) { load() }
    }

    fun askCancel() {
        if (cancelling) return
        confirmationVisible = true
    }

    fun dismissCancel() {
        confirmationVisible = false
    }

    fun confirmCancel() {
        if (cancelling) return
        confirmationVisible = false
        cancelling = true
        cancelErrorRes = null
        viewModelScope.launch {
            val code = container.session.current()
            if (code == null) {
                sessionExpired = true
                cancelling = false
                return@launch
            }
            when (val result = api.cancel(code, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    initialError = false
                    offline = false
                    cancelErrorRes = null
                }
                ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpired = true
                }
                is ApiResult.Conflict -> {
                    cancelErrorRes = R.string.details_cancel_conflict
                    // Перечитываем детали; сообщение о конфликте при этом сохраняется.
                    load()
                }
                ApiResult.Network -> cancelErrorRes = R.string.details_cancel_network
                else -> cancelErrorRes = R.string.details_cancel_other
            }
            cancelling = false
        }
    }

    private suspend fun load() {
        val code = container.session.current()
        if (code == null) {
            sessionExpired = true
            return
        }
        when (val result = api.get(code, requestId)) {
            is ApiResult.Success -> {
                details = result.value
                initialError = false
                offline = false
            }
            ApiResult.Unauthorized -> {
                container.session.clear()
                sessionExpired = true
            }
            else -> {
                // Первичная ошибка (в том числе 404) — сообщение и повтор; ошибка перезагрузки — «Нет связи».
                if (details == null) {
                    initialError = true
                    offline = false
                } else {
                    offline = true
                    initialError = false
                }
            }
        }
    }
}