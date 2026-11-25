package ru.nto.guestmanagement.mobile.ui.form

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Локальные проверки клиентской валидации: строгий разбор даты/времени и граничные случаи полей.
 * Даты и время в тестах иллюстративные — «сейчас» задаётся фиксированным значением.
 */
class FormValidationTest {

    private val now = LocalDateTime.of(2026, 10, 14, 9, 0, 0)

    private fun validInput() = FormInput(
        fullName = "Сидоров Пётр Ильич",
        phone = "+79991234567",
        organization = "ООО НТО",
        purpose = "Встреча с сотрудником",
        date = "14.10.2026",
        timeFrom = "10:00",
        timeTo = "16:00",
    )

    @Test
    fun strictDateParserRejectsImpossibleDates() {
        assertNull(parseDateInput("31.02.2026"))
        assertNull(parseDateInput("29.02.2027"))
        assertNull(parseDateInput("14.10.26"))
        assertEquals(LocalDate.of(2026, 10, 14), parseDateInput("14.10.2026"))
        assertEquals(LocalDate.of(2028, 2, 29), parseDateInput("29.02.2028"))
    }

    @Test
    fun strictTimeParserRejects24AndSingleDigitHour() {
        assertNull(parseTimeInput("24:00"))
        assertNull(parseTimeInput("9:00"))
        assertEquals(LocalTime.of(0, 0), parseTimeInput("00:00"))
        assertEquals(LocalTime.of(23, 59), parseTimeInput("23:59"))
    }

    @Test
    fun validInputHasNoErrorsAndSendsNormalizedValues() {
        val result = validate(validInput(), now)

        assertTrue(result.errors.isEmpty())
        assertEquals("Сидоров Пётр Ильич", result.fullName)
        assertEquals("+79991234567", result.phone)
        assertEquals("ООО НТО", result.organization)
        assertEquals(LocalDateTime.of(2026, 10, 14, 10, 0, 0), result.validFrom)
        assertEquals(LocalDateTime.of(2026, 10, 14, 16, 0, 0), result.validTo)
    }

    @Test
    fun textFieldsAreTrimmedAndEmptyOrganizationBecomesNull() {
        val result = validate(
            validInput().copy(
                fullName = "  Сидоров Пётр Ильич  ",
                phone = " +79991234567 ",
                organization = "   ",
                purpose = "  Встреча с сотрудником  ",
            ),
            now,
        )

        assertTrue(result.errors.isEmpty())
        assertEquals("Сидоров Пётр Ильич", result.fullName)
        assertEquals("+79991234567", result.phone)
        assertNull(result.organization)
    }

    @Test
    fun fullNameAndPurposeLengthBoundaries() {
        assertTrue(validate(validInput().copy(fullName = "Иван"), now).errors.containsKey(FormErrorSlot.FULL_NAME))
        assertFalse(validate(validInput().copy(fullName = "Ивано"), now).errors.containsKey(FormErrorSlot.FULL_NAME))
        assertFalse(validate(validInput().copy(fullName = "А".repeat(150)), now).errors.containsKey(FormErrorSlot.FULL_NAME))
        assertTrue(validate(validInput().copy(fullName = "А".repeat(151)), now).errors.containsKey(FormErrorSlot.FULL_NAME))

        assertTrue(validate(validInput().copy(purpose = "Цель"), now).errors.containsKey(FormErrorSlot.PURPOSE))
        assertFalse(validate(validInput().copy(purpose = "Цель!"), now).errors.containsKey(FormErrorSlot.PURPOSE))
        assertFalse(validate(validInput().copy(purpose = "Ц".repeat(200)), now).errors.containsKey(FormErrorSlot.PURPOSE))
        assertTrue(validate(validInput().copy(purpose = "Ц".repeat(201)), now).errors.containsKey(FormErrorSlot.PURPOSE))
    }

    @Test
    fun organizationIsOptionalButLimitedTo150Characters() {
        assertFalse(validate(validInput().copy(organization = ""), now).errors.containsKey(FormErrorSlot.ORGANIZATION))
        assertFalse(validate(validInput().copy(organization = "О".repeat(150)), now).errors.containsKey(FormErrorSlot.ORGANIZATION))
        assertTrue(validate(validInput().copy(organization = "О".repeat(151)), now).errors.containsKey(FormErrorSlot.ORGANIZATION))
    }

    @Test
    fun phoneRequiresPlus7AndExactlyTenDigits() {
        assertFalse(validate(validInput().copy(phone = "+79991234567"), now).errors.containsKey(FormErrorSlot.PHONE))
        listOf("+7 9991234567", "+7(999)1234567", "89991234567", "+7999123456", "+799912345678", "79991234567")
            .forEach { phone ->
                assertTrue(
                    "phone=$phone",
                    validate(validInput().copy(phone = phone), now).errors.containsKey(FormErrorSlot.PHONE),
                )
            }
    }

    @Test
    fun startEqualToNowIsAllowedButEarlierIsNot() {
        val equal = validate(validInput().copy(timeFrom = "09:00"), now)
        assertFalse(equal.errors.containsKey(FormErrorSlot.VALID_FROM))

        val earlier = validate(validInput().copy(timeFrom = "08:59"), now)
        assertEquals(FormError.TIME_FROM_PAST, earlier.errors[FormErrorSlot.VALID_FROM])
    }

    @Test
    fun endMustBeStrictlyAfterStart() {
        assertEquals(
            FormError.TIME_TO_ORDER,
            validate(validInput().copy(timeFrom = "10:00", timeTo = "10:00"), now).errors[FormErrorSlot.VALID_TO],
        )
        assertEquals(
            FormError.TIME_TO_ORDER,
            validate(validInput().copy(timeFrom = "16:00", timeTo = "10:00"), now).errors[FormErrorSlot.VALID_TO],
        )
        assertFalse(
            validate(validInput().copy(timeFrom = "10:00", timeTo = "10:01"), now).errors.containsKey(FormErrorSlot.VALID_TO),
        )
    }

    @Test
    fun impossibleDateAndWrongOrderAreReportedTogether() {
        val result = validate(
            validInput().copy(date = "31.02.2026", timeFrom = "16:00", timeTo = "10:00"),
            now,
        )

        assertEquals(FormError.DATE_FORMAT, result.errors[FormErrorSlot.DATE])
        assertEquals(FormError.TIME_TO_ORDER, result.errors[FormErrorSlot.VALID_TO])
        assertNull(result.validFrom)
        assertNull(result.validTo)
    }

    @Test
    fun timeFormatErrorsGoToTheirOwnSlots() {
        val result = validate(validInput().copy(timeFrom = "9:00", timeTo = "24:00"), now)

        assertEquals(FormError.TIME_FROM_FORMAT, result.errors[FormErrorSlot.VALID_FROM])
        assertEquals(FormError.TIME_TO_FORMAT, result.errors[FormErrorSlot.VALID_TO])
        assertNull(result.validFrom)
        assertNull(result.validTo)
    }

    @Test
    fun allInvalidTextFieldsAreReportedAtOnce() {
        val result = validate(
            validInput().copy(fullName = "Иван", phone = "123", organization = "О".repeat(151), purpose = "Цель"),
            now,
        )

        assertEquals(
            setOf(
                FormErrorSlot.FULL_NAME,
                FormErrorSlot.PHONE,
                FormErrorSlot.ORGANIZATION,
                FormErrorSlot.PURPOSE,
            ),
            result.errors.keys,
        )
    }
}