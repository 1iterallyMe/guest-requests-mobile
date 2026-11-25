package ru.nto.guestmanagement.mobile.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestSummary
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate

/**
 * Состояние списка заявок. Фильтр хранится здесь, а не в `remember`: при возврате из деталей композиция
 * списка создаётся заново, а ViewModel переживает переход и сохраняет выбранный фильтр.
 */
internal class RequestListViewModel(private val container: AppContainer) : ViewModel() {

    private val api = RequestsApi(container.httpClient)
    private val refreshGate = RefreshGate()

    /** `null` — фильтр «Все»: параметр `status` в запрос не добавляется. */
    var filter by mutableStateOf<RequestStatus?>(null)
        private set
    var requests by mutableStateOf<List<RequestSummary>>(emptyList())
        private set

    /** Получен хотя бы один успешный ответ: до него пустой список ещё не означает «Заявок нет». */
    var hasResponse by mutableStateOf(false)
        private set

    /** Ошибка первичной загрузки (данных ещё нет). Показывается вместо списка. */
    var initialError by mutableStateOf(false)
        private set

    /** Ошибка повторной загрузки: уже загруженный список остаётся, сверху «Нет связи». */
    var offline by mutableStateOf(false)
        private set
    var sessionExpired by mutableStateOf(false)
        private set

    fun selectFilter(status: RequestStatus?) {
        if (status == filter) return
        filter = status
        // Прерванное обновление не должно ни «догнать» новый фильтр, ни заблокировать гейт.
        refreshGate.cancel()
        refresh()
    }

    /** Единственная точка загрузки: и `ResumeEffect`, и кнопка обновления, и смена фильтра. */
    fun refresh() {
        // Фильтр фиксируется на момент запуска: ответ устаревшего запроса не должен перезаписать новый.
        val status = filter
        refreshGate.launch(viewModelScope) { load(status) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            // Код очищается до навигации: иначе экран входа увидит сохранённый код и войдёт сам.
            container.session.clear()
            onLoggedOut()
        }
    }

    private suspend fun load(status: RequestStatus?) {
        val code = container.session.current()
        if (code == null) {
            sessionExpired = true
            return
        }
        val result = api.list(code, status)
        // Пока запрос летел, фильтр мог смениться: такой ответ уже неактуален.
        if (status != filter) return
        when (result) {
            is ApiResult.Success -> {
                requests = result.value
                hasResponse = true
                initialError = false
                offline = false
            }
            ApiResult.Unauthorized -> {
                container.session.clear()
                sessionExpired = true
            }
            else -> {
                // Ошибка первичной загрузки и ошибка обновления при уже загруженных данных
                // взаимоисключающие: одновременно они не показываются.
                if (hasResponse) {
                    offline = true
                    initialError = false
                } else {
                    initialError = true
                    offline = false
                }
            }
        }
    }
}