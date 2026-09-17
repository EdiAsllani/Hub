package com.edi.hub.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.ItemDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ItemDetailUiState(
    val groupKey: String = "",
    val entries: List<PantryItem> = emptyList(),
    val learnedShelfLifeDays: Int? = null,
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pantry: PantryDao,
    products: ProductDao,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ItemDetailRoute>()
    private val location = PantryLocation.valueOf(route.location)

    private val learned = flow {
        // A hand-entered row has no barcode and therefore no product to have learned anything.
        emit(route.groupKey.takeUnless { it.startsWith("id:") }?.let { products.find(it) }?.defaultShelfLifeDays)
    }

    val state = combine(
        pantry.observeGroup(location, route.groupKey),
        learned,
    ) { entries, learnedDays ->
        ItemDetailUiState(
            groupKey = route.groupKey,
            entries = entries,
            learnedShelfLifeDays = learnedDays,
            today = LocalDate.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState(route.groupKey))

    /** Same rule as the swipe: the entry with the earliest date goes, and the screen closes on the last one. */
    fun resolve(disposition: Disposition, onEmptied: () -> Unit) {
        viewModelScope.launch {
            pantry.resolveNext(location, route.groupKey, disposition)
            if (pantry.nextToResolve(location, route.groupKey) == null) onEmptied()
        }
    }
}
