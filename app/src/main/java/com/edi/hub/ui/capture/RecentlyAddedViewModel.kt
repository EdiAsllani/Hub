package com.edi.hub.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.dao.PantryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the item capture just saved, so its undo can outlive the sequence that made it. The capture
 * ViewModel is scoped to the capture graph and is cleared the moment the sequence pops — which is
 * exactly when the snackbar needs to appear.
 */
@HiltViewModel
class RecentlyAddedViewModel @Inject constructor(
    private val pantry: PantryDao,
) : ViewModel() {

    var saved by mutableStateOf<Saved?>(null)
        private set

    fun record(saved: Saved) {
        this.saved = saved
    }

    fun clear() {
        saved = null
    }

    /** A hard delete: the row was never consumed, so a soft delete would leave a phantom behind. */
    fun undo(saved: Saved) {
        viewModelScope.launch { pantry.delete(saved.id) }
        clear()
    }
}
