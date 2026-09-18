package com.edi.hub.ui.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.components.UrgencyChip
import com.edi.hub.ui.pantry.label
import com.edi.hub.ui.theme.numeric
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A hit spends no tap on confirmation; it shows what it found and moves on by itself. */
private const val AUTO_ADVANCE_MILLIS = 600L

// -- Step 1 -------------------------------------------------------------------------------------

/**
 * Scan, then branch three ways. Two of the three move on without being asked to; only the third —
 * the barcode already on a shelf — stops and waits, because that one is a decision rather than a
 * confirmation.
 */
@Composable
fun CaptureIdentifyScreen(
    viewModel: CaptureViewModel,
    onNameIt: () -> Unit,
    onFinish: (Saved) -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (viewModel.identified is Identified.Scanning) {
            val barcode = scanBarcode(context)
            if (barcode == null) {
                // Backed out of the scanner. That is a no, not a failed read, so there is no reject
                // haptic and nothing to name — capture closes.
                onAbandon()
                return@LaunchedEffect
            }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.identify(barcode)
        }
    }

    val saved = viewModel.saved
    LaunchedEffect(saved) { if (saved != null) onFinish(saved) }

    val identified = viewModel.identified
    LaunchedEffect(identified) {
        when (identified) {
            is Identified.Found -> {
                delay(AUTO_ADVANCE_MILLIS)
                onNameIt()
            }
            // A miss or a timeout is a question, not a failure: it just moves on.
            is Identified.Unknown -> onNameIt()
            else -> Unit
        }
    }

    CaptureStep(step = 1, title = "What is it?", modifier = modifier) {
        AnimatedContent(
            targetState = identified,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "lookup",
        ) { state ->
            when (state) {
                is Identified.Scanning, is Identified.LookingUp -> SkeletonCard()
                is Identified.Found -> ProductCard(state.name, state.brand, state.imageUrl)
                is Identified.AlreadyHere -> AlreadyHereCard(
                    state = state,
                    onAdd = { viewModel.restock(state.card, state.learnedShelfLifeDays) },
                )
                is Identified.Unknown -> SkeletonCard()
            }
        }

        if (identified is Identified.LookingUp) {
            TextButton(onClick = viewModel::skipLookup) { Text("Skip, I will name it") }
        }
    }
}

/** The shape of the card that is coming, so the wait reads as progress rather than as nothing. */
@Composable
private fun SkeletonCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Placeholder(0.7f)
                Placeholder(0.4f)
            }
        }
    }
}

@Composable
private fun Placeholder(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(14.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
    )
}

@Composable
private fun ProductCard(name: String, brand: String?, imageUrl: String?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductPhoto(imageUrl)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!brand.isNullOrBlank()) {
                    Text(
                        brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The three-tap restock, and the tap that ends it. No date step: the new box takes the learned
 * shelf life, or no date where nothing has been learned yet.
 */
@Composable
private fun AlreadyHereCard(state: Identified.AlreadyHere, onAdd: () -> Unit) {
    val card = state.card
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "You already have this in the ${card.location.label.lowercase()}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onAdd),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                        if (card.entryCount > 1) {
                            Text("×${card.entryCount}", style = MaterialTheme.typography.titleMedium.numeric())
                        }
                    }
                    val meta = listOfNotNull(card.brand, card.description, card.location.label)
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UrgencyChip(card.expiresOn)
                Text("+1", style = MaterialTheme.typography.titleLarge.numeric())
            }
        }
        Text(
            "Tap it to add another box. Nothing else to fill in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProductPhoto(imageUrl: String?) {
    Box(
        Modifier
            .size(58.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

// -- Step 2 -------------------------------------------------------------------------------------

/**
 * Three free-text fields and one line that teaches what description is for. Without that line
 * people type a sentence, or expect Hub to add the numbers up — and it never will.
 */
@Composable
fun CaptureNameScreen(
    viewModel: CaptureViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = viewModel.draft
    CaptureStep(step = 2, title = "Name it", modifier = modifier) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.edit { copy(name = value) } },
            label = { Text("Name") },
            placeholder = { Text("Greek yogurt") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.brand,
            onValueChange = { value -> viewModel.edit { copy(brand = value) } },
            label = { Text("Brand") },
            placeholder = { Text("Rugove") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { value -> viewModel.edit { copy(description = value) } },
            label = { Text("Description") },
            placeholder = { Text("400 g") },
            singleLine = true,
            supportingText = {
                Text("Whatever the pack says — \"400 g\", \"24 cope\", \"1 L\". Hub only ever shows it back to you.")
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onNext,
            enabled = draft.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

// -- Step 3 -------------------------------------------------------------------------------------

@Composable
fun CaptureDateScreen(
    viewModel: CaptureViewModel,
    onSaved: (Saved) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    LaunchedEffect(Unit) { viewModel.prepareDateStep(today) }
    val draft = viewModel.draft
    val saved = viewModel.saved

    LaunchedEffect(saved) { if (saved != null) onSaved(saved) }

    CaptureStep(step = 3, title = "When does it go off?", modifier = modifier) {
        Text("Which shelf?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PantryLocation.entries.forEach { location ->
                FilterChip(
                    selected = location == draft.location,
                    onClick = { viewModel.edit { copy(location = location) } },
                    label = { Text(location.label) },
                )
            }
        }

        if (draft.prefilledExpiresOn != null) {
            LearnedCallout(
                days = draft.knownShelfLifeDays,
                acknowledged = viewModel.learnedFromCorrection,
                corrected = draft.expiresOn != draft.prefilledExpiresOn,
            )
        }

        // Typed text lives here rather than in the field so Save can refuse a half-finished date.
        var typed by remember(draft.expiresOn) { mutableStateOf(draft.expiresOn?.toString().orEmpty()) }
        val unreadable = typed.isNotEmpty() && parseDate(typed) == null

        DateField(
            expiresOn = draft.expiresOn,
            today = today,
            typed = typed,
            unreadable = unreadable,
            onTyped = { value ->
                typed = value
                if (value.isEmpty()) viewModel.edit { copy(expiresOn = null) }
                parseDate(value)?.let { date -> viewModel.edit { copy(expiresOn = date) } }
            },
            onChange = { value -> viewModel.edit { copy(expiresOn = value) } },
        )

        Button(
            onClick = { viewModel.save(today) },
            enabled = !unreadable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}

/**
 * The only thing in Hub that changes its behaviour behind the user's back, so it says so. A
 * correction replaces the badge in place — nothing to dismiss, no snackbar, no dialog.
 */
@Composable
private fun LearnedCallout(days: Int?, acknowledged: Boolean, corrected: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = acknowledged || corrected,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "learned",
        ) { changed ->
            Text(
                text = if (changed) {
                    "Noted. Hub will use this date the next time you scan it."
                } else {
                    "Filled in from the last time you scanned this — ${days ?: 0} days. Change it if it is wrong."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * The date itself: a calendar that opens under the row, and the same date as text beside it, so a
 * date read off the pack can be typed straight in. `No date` is a real answer, and Clear is how you
 * say it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    expiresOn: LocalDate?,
    today: LocalDate,
    typed: String,
    unreadable: Boolean,
    onTyped: (String) -> Unit,
    onChange: (LocalDate?) -> Unit,
) {
    var calendarOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { calendarOpen = !calendarOpen }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick the date on a calendar")
            }
            OutlinedTextField(
                value = typed,
                onValueChange = onTyped,
                label = { Text("Date") },
                placeholder = { Text("YYYY-MM-DD") },
                supportingText = if (unreadable) {
                    { Text("Write it as 2026-10-01.") }
                } else {
                    null
                },
                isError = unreadable,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.numeric(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onTyped("") }) { Text("Clear") }
        }

        UrgencyChip(expiresOn, today = today)

        if (calendarOpen) {
            Popup(
                onDismissRequest = { calendarOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                val state = rememberDatePickerState(
                    initialSelectedDateMillis = expiresOn?.toEpochDay()?.times(MILLIS_PER_DAY),
                )
                // Picking is the whole interaction, so it applies and closes rather than asking for OK.
                LaunchedEffect(state.selectedDateMillis) {
                    val picked = state.selectedDateMillis?.let { LocalDate.ofEpochDay(it / MILLIS_PER_DAY) }
                    if (picked != null && picked != expiresOn) {
                        onChange(picked)
                        calendarOpen = false
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    DatePicker(state = state, title = null, headline = null, showModeToggle = false)
                }
            }
        }
    }
}

/** `DatePickerState` counts UTC midnights, which is exactly an epoch day. No zone, no `Instant`. */
private const val MILLIS_PER_DAY = 86_400_000L

private fun parseDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    null
}

// -- Shared chrome ------------------------------------------------------------------------------

/** Three steps, and the header says which one this is. Back reverses exactly one of them. */
@Composable
private fun CaptureStep(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..3).forEach { index ->
                Box(
                    Modifier
                        .size(if (index == step) 10.dp else 6.dp)
                        .background(
                            if (index <= step) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape,
                        ),
                )
            }
            Text(
                "Step $step of 3",
                style = MaterialTheme.typography.labelMedium.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}
