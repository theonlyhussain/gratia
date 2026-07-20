package com.gratia.music.ui.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Composable-scoped selection state holder for multi-selection mode.
 *
 * Manages a set of selected IDs and provides selection operations.
 * Should be remembered at the screen level and passed down to child composables.
 */
class SelectionManager {

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** Number of currently selected items */
    val selectedCount: Int
        get() = _selectedIds.value.size

    /**
     * Toggle selection of a single item.
     * If selection mode is not active, activates it first.
     */
    fun toggle(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current

        // Auto-exit selection mode when nothing is selected
        _isSelectionMode.value = current.isNotEmpty()
    }

    /**
     * Enter selection mode and select a single item (e.g., from long-press).
     */
    fun startSelection(id: String) {
        _selectedIds.value = setOf(id)
        _isSelectionMode.value = true
    }

    /**
     * Select all items from the given list of IDs.
     */
    fun selectAll(ids: List<String>) {
        _selectedIds.value = ids.toSet()
        _isSelectionMode.value = ids.isNotEmpty()
    }

    /**
     * Clear all selections and exit selection mode.
     */
    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * Check if a specific item is selected.
     */
    fun isSelected(id: String): Boolean = _selectedIds.value.contains(id)
}
