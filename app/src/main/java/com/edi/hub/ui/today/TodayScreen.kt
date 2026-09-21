package com.edi.hub.ui.today

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.domain.Insight
import com.edi.hub.ui.components.UrgencyChip
import com.edi.hub.ui.components.expiryLabel
import com.edi.hub.ui.pantry.label
import com.edi.hub.ui.theme.HubTheme
import com.edi.hub.ui.theme.LocalUrgencyRamp
import com.edi.hub.ui.theme.numeric
import com.edi.hub.ui.theme.urgencyOf
import java.time.Instant
import java.time.LocalDate

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.reload() }
    TodayContent(
        state = viewModel.state,
        onResolve = viewModel::resolve,
        onGotIt = viewModel::gotIt,
        onSnooze = { viewModel.snooze() },
        onReveal = viewModel::revealSnoozed,
        modifier = modifier,
    )
}

@Composable
fun TodayContent(
    state: TodayUiState,
    onResolve: (PantryItem, Disposition) -> Unit,
    onGotIt: (RunOutCandidate) -> Unit,
    onSnooze: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) return

    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            // An empty queue is not an empty kitchen. Somebody with eight things and nothing due
            // today has cleared the board, not failed to start.
            state.pantryEmpty && state.current == null -> FirstLaunch()
            state.current == null -> Cleared(state)
            else -> {
                Counter(position = state.cleared + 1, total = state.total)
                // The card exits upward, the next one rises into its place, and the counter ticks.
                // Clearing is a visible event: a queue gets emptied, a feed gets forgotten.
                AnimatedContent(
                    targetState = state.current!!,
                    transitionSpec = {
                        (
                            fadeIn(tween(400)) +
                                scaleIn(tween(400), initialScale = 0.94f)
                            ) togetherWith (
                            fadeOut(tween(200)) +
                                scaleOut(tween(200), targetScale = 0.92f) +
                                slideOutVertically(tween(200)) { height -> -height / 4 }
                            )
                    },
                    label = "insightCard",
                ) { insight ->
                    when (insight) {
                        is Insight.ExpiringSoon -> ExpiryCard(
                            item = insight.item,
                            today = state.today,
                            onResolve = { onResolve(insight.item, it) },
                            onSnooze = onSnooze,
                        )
                        is Insight.RanOut -> RunOutCard(
                            candidate = insight.candidate,
                            onGotIt = { onGotIt(insight.candidate) },
                            onSnooze = onSnooze,
                        )
                    }
                }
                state.next?.let { Peek(it) }
            }
        }
        // Outside the branch above on purpose: snoozing the last card empties the queue, and a way
        // back that disappears exactly when it is needed is not a way back. It sits under the
        // cleared board just as readily as under a card.
        if (state.snoozed.isNotEmpty()) SnoozedRow(state.snoozed.size, onReveal)
    }
}

/**
 * Snoozed cards are out of the way rather than gone, and this row is what says so. A plain,
 * labelled affordance rather than a gesture: there is nothing on screen to teach a gesture with
 * once the queue is empty, and an invitation nobody finds is the same as no invitation.
 */
@Composable
private fun SnoozedRow(count: Int, onReveal: () -> Unit) {
    TextButton(
        onClick = onReveal,
        modifier = Modifier.semantics {
            contentDescription = if (count == 1) {
                "Show the card you snoozed until tomorrow"
            } else {
                "Show the $count cards you snoozed until tomorrow"
            }
        },
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (count == 1) "1 snoozed · Show" else "$count snoozed · Show",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Counter(position: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position of $total",
            style = MaterialTheme.typography.labelLarge.numeric(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(total.coerceAtMost(6)) { index ->
            Box(
                Modifier
                    .size(if (index == position - 1) 8.dp else 5.dp)
                    .background(
                        if (index < position) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** An expiry card takes its urgency band's colour, so the top of the queue looks like what it is. */
@Composable
private fun ExpiryCard(
    item: PantryItem,
    today: LocalDate,
    onResolve: (Disposition) -> Unit,
    onSnooze: () -> Unit,
) {
    val style = LocalUrgencyRamp.current[urgencyOf(item.expiresOn, today)]
    HeroCard(
        background = style.background,
        foreground = style.foreground,
        headline = item.name,
        support = listOfNotNull(item.brand, item.description, item.location.label).joinToString(" · "),
        chip = { UrgencyChip(item.expiresOn, today = today) },
        primary = "Used one" to { onResolve(Disposition.CONSUMED) },
        secondary = "Binned" to { onResolve(Disposition.DISCARDED) },
        primaryIcon = Icons.Filled.RestaurantMenu,
        secondaryIcon = Icons.Filled.DeleteOutline,
        onSnooze = onSnooze,
    )
}

/**
 * A run-out card takes the brand teal rather than a warning colour. Running out of eggs is not an
 * emergency, and colouring it like one would devalue the red on the card above it.
 */
@Composable
private fun RunOutCard(candidate: RunOutCandidate, onGotIt: () -> Unit, onSnooze: () -> Unit) {
    HeroCard(
        background = MaterialTheme.colorScheme.primaryContainer,
        foreground = MaterialTheme.colorScheme.onPrimaryContainer,
        headline = "No ${candidate.name.lowercase()} left",
        support = listOfNotNull(candidate.brand, "You finished the last one").joinToString(" · "),
        chip = null,
        primary = "Got it" to onGotIt,
        secondary = null,
        primaryIcon = Icons.Filled.Check,
        secondaryIcon = null,
        onSnooze = onSnooze,
    )
}

@Composable
private fun HeroCard(
    background: Color,
    foreground: Color,
    headline: String,
    support: String,
    chip: (@Composable () -> Unit)?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onSnooze: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background, contentColor = foreground),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            chip?.invoke()
            Text(headline, style = MaterialTheme.typography.headlineMedium)
            if (support.isNotBlank()) {
                Text(support, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = primary.second,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = foreground,
                        contentColor = background,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(primaryIcon, contentDescription = null)
                    Text(primary.first, Modifier.padding(start = 8.dp))
                }
                if (secondary != null && secondaryIcon != null) {
                    Button(
                        onClick = secondary.second,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(secondaryIcon, contentDescription = null)
                        Text(secondary.first, Modifier.padding(start = 8.dp))
                    }
                }
            }
            TextButton(onClick = onSnooze) { Text("Snooze", color = foreground) }
        }
    }
}

/** A peek of the next one, so clearing this one is obviously not the end of the road. */
@Composable
private fun Peek(insight: Insight) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = when (insight) {
                is Insight.ExpiringSoon ->
                    "Next: ${insight.item.name} · ${expiryLabel(insight.item.expiresOn)}"
                is Insight.RanOut -> "Next: no ${insight.candidate.name.lowercase()} left"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The end of the queue is a screen, not an absence. Two tallies so it is not a void. */
@Composable
private fun Cleared(state: TodayUiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Scales in from 0.8 on the same spring the last card left on.
            AnimatedContent(
                targetState = true,
                transitionSpec = {
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.8f) +
                        fadeIn() togetherWith fadeOut()
                },
                label = "clearedCheck",
            ) { _ ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(20.dp).size(40.dp),
                    )
                }
            }
            Text("Nothing needs you today", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (state.cleared > 0) {
                    "${state.cleared} cleared · nothing goes off this week"
                } else {
                    "Nothing goes off this week"
                },
                style = MaterialTheme.typography.bodyMedium.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Three dashed slots where the cards will be, one thing to do, and an honest line about the nudge. */
@Composable
private fun FirstLaunch() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val outline = MaterialTheme.colorScheme.outlineVariant
        val corner = with(LocalDensity.current) { 28.dp.toPx() }
        repeat(3) { index ->
            // Dashed rather than filled: an outline reads as a slot waiting to be filled, where a
            // solid block reads as a card that failed to load.
            Box(
                Modifier
                    .fillMaxWidth(1f - index * 0.08f)
                    .height(if (index == 0) 96.dp else 56.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = outline,
                            cornerRadius = CornerRadius(corner),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                                ),
                            ),
                        )
                    },
            )
        }
        Text("Today is empty", style = MaterialTheme.typography.titleLarge)
        Text(
            "When something is about to go off, or you finish the last of something, it shows up " +
                "here one card at a time. Hub can also send one summary at 08:00 if you switch it " +
                "on in settings — that is the only notification it will ever send.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "Scan something with the button below to start.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// -- Previews -----------------------------------------------------------------------------------

private val TODAY: LocalDate = LocalDate.of(2026, 9, 17)

private val sampleMilk = PantryItem(
    id = 1,
    barcode = "5449000000996",
    name = "Milk",
    brand = "Vita",
    description = "1 L",
    location = PantryLocation.FRIDGE,
    addedAt = Instant.ofEpochMilli(1_700_000_000_000),
    expiresOn = TODAY.plusDays(1),
)

private val sampleEggs = RunOutCandidate(
    barcode = "111",
    name = "Eggs",
    brand = "Rugove",
    lastResolvedAt = Instant.ofEpochMilli(1_700_000_000_000),
)

@Composable
private fun Board(state: TodayUiState) = HubTheme {
    Surface { TodayContent(state, { _, _ -> }, {}, {}, {}) }
}

@Preview(name = "Today, expiry card, light", showBackground = true, heightDp = 720)
@Preview(name = "Today, expiry card, dark", showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpiryCardPreview() = Board(
    TodayUiState(
        queue = listOf(Insight.ExpiringSoon(sampleMilk), Insight.RanOut(sampleEggs)),
        loaded = true,
        pantryEmpty = false,
        today = TODAY,
    ),
)

@Preview(name = "Today, run-out card", showBackground = true, heightDp = 720)
@Composable
private fun RunOutCardPreview() = Board(
    TodayUiState(
        queue = listOf(Insight.RanOut(sampleEggs)),
        cleared = 2,
        loaded = true,
        pantryEmpty = false,
        today = TODAY,
    ),
)

@Preview(name = "Today, one snoozed", showBackground = true, heightDp = 720)
@Composable
private fun SnoozedPreview() = Board(
    TodayUiState(
        queue = listOf(Insight.ExpiringSoon(sampleMilk)),
        snoozed = listOf(Insight.RanOut(sampleEggs)),
        loaded = true,
        pantryEmpty = false,
        today = TODAY,
    ),
)

@Preview(name = "Today, queue cleared", showBackground = true, heightDp = 720)
@Composable
private fun ClearedPreview() =
    Board(TodayUiState(cleared = 3, loaded = true, pantryEmpty = false, today = TODAY))

@Preview(name = "Today, first launch", showBackground = true, heightDp = 720)
@Composable
private fun FirstLaunchPreview() = Board(TodayUiState(loaded = true, today = TODAY))
