package ru.nto.guestmanagement.mobile.ui.form

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import ru.nto.guestmanagement.mobile.R

/** Поле формы, под которым показывается ошибка (совпадает с тегом `form_error_*`). */
internal enum class FormErrorSlot {
    FULL_NAME,
    PHONE,
    ORGANIZATION,
    PURPOSE,
    DATE,
    VALID_FROM,
    VALID_TO,
}

internal enum class FormError(val messageRes: Int) {
    FULL_NAME(R.string.form_error_full_name),
    PHONE(R.string.form_error_phone),
    ORGANIZATION(R.string.form_error_organization),
    PURPOSE(R.string.form_error_purpose),
    DATE_FORMAT(R.string.form_error_date),
    TIME_FROM_FORMAT(R.string.form_error_time_from_format),
    TIME_FROM_PAST(R.string.form_error_time_from_past),
    TIME_TO_FORMAT(R.string.form_error_time_to_format),
    TIME_TO_ORDER(R.string.form_error_time_to_order),
}

internal data class FormInput(
    val fullName: String,
    val phone: String,
    val organization: String,
    val purpose: String,
    val date: String,
    val timeFrom: String,
    val timeTo: String,
)

internal data class FormValidationResult(
    val errors: Map<FormErrorSlot, FormError>,
    val fullName: String,
    val phone: String,
    val organization: String?,
    val purpose: String,
    val validFrom: LocalDateTime?,
    val validTo: LocalDateTime?,
)

/**
 * Разбор даты и времени ввода. `ResolverStyle.STRICT` обязателен: SMART-резолвер молча превращает
 * `31.02.2026` в `28.02.2026`, а `24:00` — в `00:00`, хотя такие значения недопустимы. Год — `uuuu`,
 * потому что STRICT с `yyyy` (year-of-era) не разбирает дату без эры.
 */
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
private val PHONE_PATTERN = Regex("\\+7\\d{10}")

internal fun parseDateInput(raw: String): LocalDate? = try {
    LocalDate.parse(raw, DATE_FORMAT)
} catch (e: DateTimeException) {
    null
}

internal fun parseTimeInput(raw: String): LocalTime? = try {
    LocalTime.parse(raw, TIME_FORMAT)
} catch (e: DateTimeException) {
    null
}

/** Проверяет поля заявки и возвращает нормализованные значения и ошибки по полям. */
internal fun validate(input: FormInput, now: LocalDateTime): FormValidationResult {
    val errors = LinkedHashMap<FormErrorSlot, FormError>()

    val fullName = input.fullName.trim()
    if (fullName.length !in 5..150) errors[FormErrorSlot.FULL_NAME] = FormError.FULL_NAME

    val phone = input.phone.trim()
    if (!PHONE_PATTERN.matches(phone)) errors[FormErrorSlot.PHONE] = FormError.PHONE

    val organization = input.organization.trim()
    if (organization.length > 150) errors[FormErrorSlot.ORGANIZATION] = FormError.ORGANIZATION

    val purpose = input.purpose.trim()
    if (purpose.length !in 5..200) errors[FormErrorSlot.PURPOSE] = FormError.PURPOSE

    val date = parseDateInput(input.date)
    if (date == null) errors[FormErrorSlot.DATE] = FormError.DATE_FORMAT

    val timeFrom = parseTimeInput(input.timeFrom)
    if (timeFrom == null) errors[FormErrorSlot.VALID_FROM] = FormError.TIME_FROM_FORMAT

    val timeTo = parseTimeInput(input.timeTo)
    if (timeTo == null) errors[FormErrorSlot.VALID_TO] = FormError.TIME_TO_FORMAT

    var validFrom: LocalDateTime? = null
    if (date != null && timeFrom != null) {
        validFrom = LocalDateTime.of(date, timeFrom)
        // Ровно текущее время допустимо, поэтому сравнение — строгое «раньше».
        if (validFrom.isBefore(now)) errors[FormErrorSlot.VALID_FROM] = FormError.TIME_FROM_PAST
    }

    var validTo: LocalDateTime? = null
    if (date != null && timeTo != null) {
        validTo = LocalDateTime.of(date, timeTo)
    }

    // Дата у начала и окончания общая, поэтому порядок можно проверить и при неверной дате.
    if (timeFrom != null && timeTo != null && !timeTo.isAfter(timeFrom)) {
        errors[FormErrorSlot.VALID_TO] = FormError.TIME_TO_ORDER
    }

    return FormValidationResult(
        errors = errors,
        fullName = fullName,
        phone = phone,
        organization = organization.ifEmpty { null },
        purpose = purpose,
        validFrom = validFrom,
        validTo = validTo,
    )
}