package com.edi.hub.domain

import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.ui.theme.Urgency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

fun nextOccurrenceAfter(dueOn: LocalDate, repeatDays: Int, today: LocalDate): LocalDate {
    require(repeatDays > 0) { "Repeat interval must be at least one day" }
    if (dueOn.isAfter(today)) return dueOn
    val elapsed = ChronoUnit.DAYS.between(dueOn, today)
    return dueOn.plusDays((elapsed / repeatDays + 1) * repeatDays)
}

fun deadlineUrgency(kind: DeadlineKind, dueOn: LocalDate, today: LocalDate = LocalDate.now()): Urgency {
    if (dueOn.isBefore(today)) return Urgency.EXPIRED
    val days = ChronoUnit.DAYS.between(today, dueOn)
    val (critical, soon) = when (kind) {
        DeadlineKind.WARRANTY -> 7L to 30L
        DeadlineKind.DOCUMENT -> 30L to 90L
        DeadlineKind.BILL -> 2L to 7L
        DeadlineKind.UPKEEP, DeadlineKind.VEHICLE, DeadlineKind.LENDING -> 2L to 7L
    }
    return when {
        days <= critical -> Urgency.CRITICAL
        days <= soon -> Urgency.SOON
        else -> Urgency.OK
    }
}

fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("EUR")

fun parseMinorUnits(text: String, currencyCode: String): Long? {
    val cleaned = text.trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val fractionDigits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
        .getOrDefault(2).coerceAtLeast(0)
    return runCatching {
        BigDecimal(cleaned)
            .setScale(fractionDigits, RoundingMode.UNNECESSARY)
            .movePointRight(fractionDigits)
            .longValueExact()
    }.getOrNull()
}

fun formatMinorUnits(value: Long, currencyCode: String): String {
    val fractionDigits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
        .getOrDefault(2).coerceAtLeast(0)
    return BigDecimal.valueOf(value, fractionDigits).toPlainString()
}

fun Deadline.validationError(): String? = when {
    name.isBlank() -> "Name is required"
    repeatDays != null && repeatDays <= 0 -> "Repeat interval must be at least one day"
    costMinor != null && costMinor < 0 -> "Cost cannot be negative"
    else -> null
}
