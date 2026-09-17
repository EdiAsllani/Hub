package com.edi.hub.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

/**
 * How close an item is to its expiry. Four buckets, plus [NONE] for an item with no date at all —
 * which is not an urgency so much as the absence of one, and reuses the neutral treatment.
 *
 * `OK` is neutral grey rather than green: green would compete with the brand teal and would imply
 * "good" where the honest meaning is "nothing to say". See `design/spec.md` §1.
 */
enum class Urgency { EXPIRED, CRITICAL, SOON, OK, NONE }

/** Every urgency carries an icon and a label alongside the colour. Never colour alone. */
data class UrgencyStyle(
    val foreground: Color,
    val background: Color,
    val icon: ImageVector,
)

class UrgencyRamp(
    val expired: UrgencyStyle,
    val critical: UrgencyStyle,
    val soon: UrgencyStyle,
    val ok: UrgencyStyle,
) {
    operator fun get(urgency: Urgency): UrgencyStyle = when (urgency) {
        Urgency.EXPIRED -> expired
        Urgency.CRITICAL -> critical
        Urgency.SOON -> soon
        Urgency.OK, Urgency.NONE -> ok
    }
}

internal val LightRamp = UrgencyRamp(
    expired = UrgencyStyle(Color(0xFF8E1710), Color(0xFFFFDAD5), Icons.Outlined.Cancel),
    critical = UrgencyStyle(Color(0xFF8A3A00), Color(0xFFFFDCC2), Icons.Filled.Warning),
    soon = UrgencyStyle(Color(0xFF66500A), Color(0xFFFBE8A6), Icons.Filled.Schedule),
    ok = UrgencyStyle(Color(0xFF3E4948), Color(0xFFE3EBEA), Icons.Filled.CheckCircle),
)

internal val DarkRamp = UrgencyRamp(
    expired = UrgencyStyle(Color(0xFFFFB4AB), Color(0xFF4E110C), Icons.Outlined.Cancel),
    critical = UrgencyStyle(Color(0xFFFFB77C), Color(0xFF4A2400), Icons.Filled.Warning),
    soon = UrgencyStyle(Color(0xFFEACD6C), Color(0xFF3C3000), Icons.Filled.Schedule),
    ok = UrgencyStyle(Color(0xFFBEC9C7), Color(0xFF2E3635), Icons.Filled.CheckCircle),
)

val LocalUrgencyRamp = staticCompositionLocalOf { LightRamp }

/**
 * Today and tomorrow are critical, the rest of the week is soon. These are calendar days apart,
 * never hours, so nothing shifts across midnight or a DST boundary.
 */
fun urgencyOf(expiresOn: LocalDate?, today: LocalDate = LocalDate.now()): Urgency = when {
    expiresOn == null -> Urgency.NONE
    expiresOn.isBefore(today) -> Urgency.EXPIRED
    expiresOn <= today.plusDays(1) -> Urgency.CRITICAL
    expiresOn <= today.plusDays(7) -> Urgency.SOON
    else -> Urgency.OK
}
