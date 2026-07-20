package com.gratia.music.ui.onboarding

import android.Manifest
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gratia.music.data.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PermissionViewModel : ViewModel() {

    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    fun initialize(context: Context) {
        if (_permissions.value.isNotEmpty()) return

        val required = PermissionManager.getRequiredPermissions()
        val items = required.mapNotNull { perm ->
            when (perm) {
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE -> PermissionItem(
                    permission = perm,
                    title = "Music Library",
                    description = "Read music stored on your device.",
                    icon = Icons.Default.LibraryMusic,
                    status = if (PermissionManager.isPermissionGranted(context, perm)) PermissionStatus.GRANTED else PermissionStatus.PENDING
                )
                Manifest.permission.POST_NOTIFICATIONS -> PermissionItem(
                    permission = perm,
                    title = "Notifications",
                    description = "Display playback controls and lock-screen media controls.",
                    icon = Icons.Default.Notifications,
                    status = if (PermissionManager.isPermissionGranted(context, perm)) PermissionStatus.GRANTED else PermissionStatus.PENDING
                )
                Manifest.permission.BLUETOOTH_CONNECT -> PermissionItem(
                    permission = perm,
                    title = "Bluetooth",
                    description = "Automatically detect Bluetooth headphones and speakers.",
                    icon = Icons.Default.Bluetooth,
                    status = if (PermissionManager.isPermissionGranted(context, perm)) PermissionStatus.GRANTED else PermissionStatus.PENDING
                )
                else -> null
            }
        }
        _permissions.value = items

        // Advance past already granted permissions
        advanceToNextPending()
    }

    private fun advanceToNextPending() {
        val nextIndex = _permissions.value.indexOfFirst { it.status == PermissionStatus.PENDING || it.status == PermissionStatus.DENIED }
        if (nextIndex == -1) {
            _isFinished.value = true
        } else {
            _currentIndex.value = nextIndex
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean, shouldShowRationale: Boolean) {
        _permissions.value = _permissions.value.map { item ->
            if (item.permission == permission) {
                val newStatus = when {
                    isGranted -> PermissionStatus.GRANTED
                    !shouldShowRationale -> PermissionStatus.PERMANENTLY_DENIED
                    else -> PermissionStatus.DENIED
                }
                item.copy(status = newStatus)
            } else {
                item
            }
        }
        advanceToNextPending()
    }

    fun markRequesting() {
        val current = _permissions.value.getOrNull(_currentIndex.value) ?: return
        _permissions.value = _permissions.value.map {
            if (it.permission == current.permission) it.copy(status = PermissionStatus.REQUESTING) else it
        }
    }

    fun finishOnboarding(dataStore: SettingsDataStore) {
        viewModelScope.launch {
            dataStore.setOnboardingCompleted(true)
        }
    }
}
