package com.edi.hub.ui.deadlines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.domain.deadlineUrgency
import com.edi.hub.domain.formatMinorUnits
import com.edi.hub.domain.nextOccurrenceAfter
import com.edi.hub.ui.theme.LocalUrgencyRamp
import com.edi.hub.ui.theme.Urgency
import com.edi.hub.ui.theme.numeric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private const val MILLIS_PER_DAY = 86_400_000L

val DeadlineKind.label: String get() = when (this) {
    DeadlineKind.WARRANTY -> "Warranty"
    DeadlineKind.DOCUMENT -> "Document"
    DeadlineKind.UPKEEP -> "Upkeep"
    DeadlineKind.BILL -> "Bill"
    DeadlineKind.VEHICLE -> "Vehicle"
    DeadlineKind.LENDING -> "Lent"
}

@Composable
fun DeadlinesScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeadlinesViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.kind == null, onClick = { viewModel.filter(null) }, label = { Text("Everything") })
            DeadlineKind.entries.forEach { kind ->
                FilterChip(selected = state.kind == kind, onClick = { viewModel.filter(kind) }, label = { Text(kind.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.sort == DeadlineSort.DATE, { viewModel.sort(DeadlineSort.DATE) }, label = { Text("By date") })
            FilterChip(state.sort == DeadlineSort.NAME, { viewModel.sort(DeadlineSort.NAME) }, label = { Text("By name") })
        }
        when {
            state.all.isEmpty() -> EmptyDeadlines(onAdd)
            state.rows.isEmpty() -> Text(
                "Nothing matches this filter.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 32.dp),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rows, key = Deadline::id) { deadline ->
                    DeadlineCard(deadline, onOpen, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun EmptyDeadlines(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nothing is waiting", style = MaterialTheme.typography.headlineSmall)
        Text("Add a warranty, document, bill, service date, vehicle renewal or something you lent.")
        Button(onClick = onAdd) { Text("Add a deadline") }
    }
}

@Composable
private fun DeadlineCard(deadline: Deadline, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().clickable { onOpen(deadline.id) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(deadline.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(deadline.kind.label, deadline.counterparty).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DeadlineUrgencyChip(deadline)
            }
        }
    }
}

@Composable
fun DeadlineUrgencyChip(deadline: Deadline, today: LocalDate = LocalDate.now()) {
    val urgency = deadlineUrgency(deadline.kind, deadline.dueOn, today)
    val style = LocalUrgencyRamp.current[urgency]
    val text = when (urgency) {
        Urgency.EXPIRED -> "Overdue · ${dateFormat.format(deadline.dueOn)}"
        else -> dateFormat.format(deadline.dueOn)
    }
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelMedium.numeric()) },
        leadingIcon = { Icon(style.icon, contentDescription = null) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = style.background,
            labelColor = style.foreground,
            leadingIconContentColor = style.foreground,
        ),
    )
}

@Composable
fun DeadlineDetailScreen(
    onEdit: (Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeadlineDetailViewModel = hiltViewModel(),
) {
    val deadline by viewModel.deadline.collectAsState()
    val row = deadline ?: return
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DeadlineUrgencyChip(row)
        Text(row.name, style = MaterialTheme.typography.headlineMedium)
        DetailLine("Kind", row.kind.label)
        DetailLine(if (row.kind == DeadlineKind.LENDING) "Expected back" else "Due", dateFormat.format(row.dueOn))
        row.repeatDays?.let { DetailLine("Repeats", "Every $it days") }
        row.counterparty?.let { DetailLine("With", it) }
        row.costMinor?.let { DetailLine("Cost", "${formatMinorUnits(it, viewModel.currencyCode)} ${viewModel.currencyCode}") }
        row.note?.let { DetailLine("Note", it) }
        if (row.repeatDays != null) {
            Text("Completing this moves it to ${dateFormat.format(nextOccurrenceAfter(row.dueOn, row.repeatDays, LocalDate.now()))}.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEdit(row.id) }) { Icon(Icons.Outlined.Edit, null); Text("Edit") }
            Button(onClick = { viewModel.complete(onDone) }) {
                Icon(Icons.Filled.Check, null)
                Text(if (row.kind == DeadlineKind.LENDING) "Mark returned" else "Complete")
            }
        }
    }
}

@Composable private fun DetailLine(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineEditorScreen(
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeadlineEditorViewModel = hiltViewModel(),
) {
    if (!viewModel.loaded) return
    val form = viewModel.form
    var calendarOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (form.name.isBlank()) "Add a deadline" else "Edit deadline", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeadlineKind.entries.forEach { kind ->
                FilterChip(form.kind == kind, { viewModel.change(form.copy(kind = kind)) }, label = { Text(kind.label) })
            }
        }
        OutlinedTextField(form.name, { viewModel.change(form.copy(name = it)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { calendarOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, null)
            Text("Due ${dateFormat.format(form.dueOn)}", Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            form.repeatDays,
            { viewModel.change(form.copy(repeatDays = it.filter(Char::isDigit))) },
            label = { Text("Repeat every number of days (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(form.counterparty, { viewModel.change(form.copy(counterparty = it)) }, label = { Text(if (form.kind == DeadlineKind.LENDING) "Who has it" else "Counterparty (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            form.cost,
            { viewModel.change(form.copy(cost = it)) },
            label = { Text("Cost in ${viewModel.currencyCode} (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(form.note, { viewModel.change(form.copy(note = it)) }, label = { Text("Note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { viewModel.save(onSaved) }, modifier = Modifier.fillMaxWidth()) { Text("Save deadline") }
    }
    if (calendarOpen) {
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = form.dueOn.toEpochDay() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { viewModel.change(form.copy(dueOn = LocalDate.ofEpochDay(it / MILLIS_PER_DAY))) }
                    calendarOpen = false
                }) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}
