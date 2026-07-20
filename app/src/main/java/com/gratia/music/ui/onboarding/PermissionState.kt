package com.gratia.music.ui.onboarding

import androidx.compose.ui.graphics.vector.ImageVector

enum class PermissionStatus {
    PENDING,
    REQUESTING,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED
}

data class PermissionItem(
    val permission: String, // Android permission string
    val title: String,
    val description: String,
    val icon: ImageVector,
    val status: PermissionStatus = PermissionStatus.PENDING,
    val isRequired: Boolean = true
)
