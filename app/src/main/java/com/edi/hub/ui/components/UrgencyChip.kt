package com.edi.hub.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.edi.hub.ui.theme.HubTheme
import com.edi.hub.ui.theme.LocalUrgencyRamp
import com.edi.hub.ui.theme.numeric
import com.edi.hub.ui.theme.urgencyOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Icon, text and colour, always all three. Colour alone would leave the whole urgency model
 * invisible to a third of colour-blind users and to anyone reading the screen in bright sun.
 */
@Composable
fun UrgencyChip(
    expiresOn: LocalDate?,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val style = LocalUrgencyRamp.current[urgencyOf(expiresOn, today)]
    val label = expiryLabel(expiresOn, today)
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spokenExpiry(expiresOn, today)
        },
        shape = CircleShape,
        color = style.background,
        contentColor = style.foreground,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            // Tabular figures: proportional digits jitter visibly as rows reorder.
            Text(label, style = MaterialTheme.typography.labelMedium.numeric())
        }
    }
}

/** Short enough to sit on a card at the end of a name. The reader is scanning, not studying. */
fun expiryLabel(expiresOn: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (expiresOn == null) return "No date"
    val days = ChronoUnit.DAYS.between(today, expiresOn)
    return when {
        days < -1 -> "${-days}d ago"
        days == -1L -> "Yesterday"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 31 -> "${days}d"
        days < 365 -> "${ChronoUnit.MONTHS.between(today, expiresOn).coerceAtLeast(1)}mo"
        else -> "${ChronoUnit.YEARS.between(today, expiresOn).coerceAtLeast(1)}y"
    }
}

/** TalkBack gets the whole sentence rather than the abbreviation on the chip. */
private fun spokenExpiry(expiresOn: LocalDate?, today: LocalDate): String {
    if (expiresOn == null) return "No expiry date"
    val days = ChronoUnit.DAYS.between(today, expiresOn)
    return when {
        days < 0 -> "Expired ${-days} days ago"
        days == 0L -> "Expires today"
        days == 1L -> "Expires tomorrow"
        else -> "Expires in $days days"
    }
}

@Preview(name = "Urgency chips, light", showBackground = true)
@Preview(name = "Urgency chips, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UrgencyChipPreview() {
    val today = LocalDate.of(2026, 9, 17)
    HubTheme {
        Surface {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                UrgencyChip(today.minusDays(4), today = today)
                UrgencyChip(today, today = today)
                UrgencyChip(today.plusDays(5), today = today)
                UrgencyChip(today.plusMonths(8), today = today)
                UrgencyChip(null, today = today)
            }
        }
    }
}
