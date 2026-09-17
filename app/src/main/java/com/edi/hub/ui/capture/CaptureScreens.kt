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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.components.UrgencyChip
import com.edi.hub.ui.pantry.label
import com.edi.hub.ui.theme.numeric
import kotlinx.coroutines.delay
import java.time.LocalDate

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (viewModel.identified is Identified.Scanning) {
            val barcode = scanBarcode(context)
            haptics.performHapticFeedback(
                if (barcode == null) HapticFeedbackType.Reject else HapticFeedbackType.Confirm,
            )
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

/** The quick-picks a first scan gets, before the field. Four chips cover most of a kitchen. */
private val QUICK_PICKS = listOf(
    "+3 d" to 3L,
    "+1 wk" to 7L,
    "+1 mo" to 30L,
    "+6 mo" to 182L,
)

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
        } else {
            Text("Or pick one of these", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_PICKS.forEach { (label, days) ->
                    FilterChip(
                        selected = draft.expiresOn == today.plusDays(days),
                        onClick = { viewModel.edit { copy(expiresOn = today.plusDays(days)) } },
                        label = { Text(label) },
                    )
                }
            }
        }

        DateField(
            expiresOn = draft.expiresOn,
            today = today,
            onChange = { value -> viewModel.edit { copy(expiresOn = value) } },
        )

        Button(onClick = { viewModel.save(today) }, modifier = Modifier.fillMaxWidth()) {
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
 * The date itself, as days from today rather than a calendar: in a shop the pack says a date, and
 * counting forward is the thing a person can actually do one-handed. `No date` is a real answer.
 */
@Composable
private fun DateField(expiresOn: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = expiresOn?.toString() ?: "No date",
                style = MaterialTheme.typography.titleMedium.numeric(),
            )
            UrgencyChip(expiresOn, today = today)
        }
        TextButton(onClick = { onChange(expiresOn?.minusDays(1) ?: today) }) { Text("−1 d") }
        TextButton(onClick = { onChange((expiresOn ?: today).plusDays(1)) }) { Text("+1 d") }
        TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
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
