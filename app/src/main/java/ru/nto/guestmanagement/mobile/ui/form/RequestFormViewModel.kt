package ru.nto.guestmanagement.mobile.ui.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.CreateRequestBody
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.toApi
import ru.nto.guestmanagement.mobile.di.AppContainer

/**
 * Состояние формы новой заявки. Все значения хранятся в ViewModel: она переживает поворот экрана,
 * поэтому введённые данные не теряются.
 */
internal class RequestFormViewModel(private val container: AppContainer) : ViewModel() {

    private val api = RequestsApi(container.httpClient)

    var fullName by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var organization by mutableStateOf("")
        private set
    var purpose by mutableStateOf("")
        private set
    var date by mutableStateOf("")
        private set
    var timeFrom by mutableStateOf("")
        private set
    var timeTo by mutableStateOf("")
        private set

    var clientErrors by mutableStateOf<Map<FormErrorSlot, FormError>>(emptyMap())
        private set
    var serverErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var commonErrorRes by mutableStateOf<Int?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set
    var sessionExpired by mutableStateOf(false)
        private set

    /** Ошибки показываются только после первой попытки отправки. */
    private var attempted = false

    val canSubmit: Boolean get() = !submitting

    fun onFullNameChange(value: String) {
        fullName = value
        clearServerError("fullName")
        onEdited()
    }

    fun onPhoneChange(value: String) {
        phone = value
        clearServerError("phone")
        onEdited()
    }

    fun onOrganizationChange(value: String) {
        organization = value
        clearServerError("organization")
        onEdited()
    }

    fun onPurposeChange(value: String) {
        purpose = value
        clearServerError("purpose")
        onEdited()
    }

    /** Правка даты не сбрасывает серверные ошибки `validFrom`/`validTo`: они относятся ко времени. */
    fun onDateChange(value: String) {
        date = value
        onEdited()
    }

    fun onTimeFromChange(value: String) {
        timeFrom = value
        clearServerError("validFrom")
        onEdited()
    }

    fun onTimeToChange(value: String) {
        timeTo = value
        clearServerError("validTo")
        onEdited()
    }

    fun submit(onCreated: () -> Unit) {
        if (submitting) return
        val validation = validate(currentInput(), container.timeProvider.now())
        attempted = true
        clientErrors = validation.errors
        if (validation.errors.isNotEmpty()) return

        val validFrom = validation.validFrom ?: return
        val validTo = validation.validTo ?: return

        submitting = true
        commonErrorRes = null
        viewModelScope.launch {
            val code = container.session.current()
            if (code == null) {
                sessionExpired = true
                submitting = false
                return@launch
            }
            val body = CreateRequestBody(
                fullName = validation.fullName,
                phone = validation.phone,
                organization = validation.organization,
                purpose = validation.purpose,
                validFrom = validFrom.toApi(),
                validTo = validTo.toApi(),
            )
            when (val result = api.create(code, body)) {
                is ApiResult.Success -> {
                    submitting = false
                    onCreated()
                }
                ApiResult.Unauthorized -> {
                    // Сообщения об истёкшей сессии на форме нет: код очищается, экран уходит на вход.
                    container.session.clear()
                    sessionExpired = true
                    submitting = false
                }
                is ApiResult.FieldErrors -> {
                    serverErrors = result.errors
                    submitting = false
                }
                is ApiResult.Conflict -> {
                    // Единственный 409 на создание — дубликат.
                    commonErrorRes = R.string.form_error_duplicate
                    submitting = false
                }
                ApiResult.Network -> {
                    commonErrorRes = R.string.form_error_network
                    submitting = false
                }
                else -> {
                    commonErrorRes = R.string.form_error_other
                    submitting = false
                }
            }
        }
    }

    private fun onEdited() {
        commonErrorRes = null
        if (attempted) {
            clientErrors = validate(currentInput(), container.timeProvider.now()).errors
        }
    }

    private fun clearServerError(key: String) {
        if (serverErrors.containsKey(key)) {
            serverErrors = serverErrors - key
        }
    }

    private fun currentInput() = FormInput(
        fullName = fullName,
        phone = phone,
        organization = organization,
        purpose = purpose,
        date = date,
        timeFrom = timeFrom,
        timeTo = timeTo,
    )
}