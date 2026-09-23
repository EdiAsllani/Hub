package com.edi.hub.ui.deadlines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.edi.hub.data.HubPrefs
import com.edi.hub.data.dao.DeadlineDao
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.domain.parseMinorUnits
import com.edi.hub.domain.validationError
import com.edi.hub.ui.DeadlineDetailRoute
import com.edi.hub.ui.DeadlineEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class DeadlineSort { DATE, NAME }

data class DeadlinesUiState(
    val all: List<Deadline> = emptyList(),
    val kind: DeadlineKind? = null,
    val sort: DeadlineSort = DeadlineSort.DATE,
) {
    val rows: List<Deadline> get() {
        val filtered = all.filter { kind == null || it.kind == kind }
        return when (sort) {
            DeadlineSort.DATE -> filtered.sortedWith(compareBy<Deadline> { it.dueOn }.thenBy { it.name.lowercase() })
            DeadlineSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}

@HiltViewModel
class DeadlinesViewModel @Inject constructor(private val dao: DeadlineDao) : ViewModel() {
    var state by mutableStateOf(DeadlinesUiState())
        private set

    init {
        viewModelScope.launch { dao.observeActive().collect { state = state.copy(all = it) } }
    }

    fun filter(kind: DeadlineKind?) { state = state.copy(kind = kind) }
    fun sort(sort: DeadlineSort) { state = state.copy(sort = sort) }
}

@HiltViewModel
class DeadlineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: DeadlineDao,
    prefs: HubPrefs,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<DeadlineDetailRoute>().id
    val deadline = dao.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currencyCode: String = prefs.currencyCode

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            dao.complete(id, LocalDate.now(), Instant.now())
            onDone()
        }
    }
}

data class DeadlineForm(
    val kind: DeadlineKind = DeadlineKind.WARRANTY,
    val name: String = "",
    val dueOn: LocalDate = LocalDate.now(),
    val repeatDays: String = "",
    val counterparty: String = "",
    val cost: String = "",
    val note: String = "",
)

@HiltViewModel
class DeadlineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: DeadlineDao,
    prefs: HubPrefs,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<DeadlineEditorRoute>()
    private var original: Deadline? = null
    val currencyCode: String = prefs.currencyCode

    var form by mutableStateOf(
        DeadlineForm(kind = route.kind?.let { runCatching { DeadlineKind.valueOf(it) }.getOrNull() }
            ?: DeadlineKind.WARRANTY),
    )
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(route.id == 0L)
        private set

    init {
        if (route.id != 0L) viewModelScope.launch {
            original = dao.find(route.id)
            original?.let { row ->
                form = DeadlineForm(
                    kind = row.kind,
                    name = row.name,
                    dueOn = row.dueOn,
                    repeatDays = row.repeatDays?.toString().orEmpty(),
                    counterparty = row.counterparty.orEmpty(),
                    cost = row.costMinor?.let { com.edi.hub.domain.formatMinorUnits(it, currencyCode) }.orEmpty(),
                    note = row.note.orEmpty(),
                )
            }
            loaded = true
        }
    }

    fun change(value: DeadlineForm) { form = value; error = null }

    fun save(onSaved: (Long) -> Unit) {
        val repeat = form.repeatDays.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        if (form.repeatDays.isNotBlank() && repeat == null) { error = "Repeat interval must be a whole number"; return }
        val cost = parseMinorUnits(form.cost, currencyCode)
        if (form.cost.isNotBlank() && cost == null) { error = "Enter a valid $currencyCode amount"; return }
        val row = Deadline(
            id = original?.id ?: 0,
            kind = form.kind,
            name = form.name.trim(),
            dueOn = form.dueOn,
            repeatDays = repeat,
            counterparty = form.counterparty.trim().ifBlank { null },
            costMinor = cost,
            note = form.note.trim().ifBlank { null },
            completedAt = original?.completedAt,
            snoozedUntil = original?.snoozedUntil,
        )
        row.validationError()?.let { error = it; return }
        viewModelScope.launch {
            val id = if (row.id == 0L) dao.insert(row) else { dao.update(row); row.id }
            onSaved(id)
        }
    }
}
