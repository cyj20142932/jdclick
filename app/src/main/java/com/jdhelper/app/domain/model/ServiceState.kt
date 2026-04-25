package com.jdhelper.app.domain.model

data class ServiceState(
    val floatingServiceRunning: Boolean = false,
    val floatingMenuServiceRunning: Boolean = false,
    val accessibilityServiceRunning: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val accessibilityPermissionGranted: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        val INITIAL = ServiceState()
    }

    fun withFloatingService(running: Boolean): ServiceState = copy(
        floatingServiceRunning = running,
        lastUpdated = System.currentTimeMillis()
    )

    fun withFloatingMenuService(running: Boolean): ServiceState = copy(
        floatingMenuServiceRunning = running,
        lastUpdated = System.currentTimeMillis()
    )

    fun withAccessibilityService(running: Boolean): ServiceState = copy(
        accessibilityServiceRunning = running,
        lastUpdated = System.currentTimeMillis()
    )

    fun withOverlayPermission(granted: Boolean): ServiceState = copy(
        overlayPermissionGranted = granted,
        lastUpdated = System.currentTimeMillis()
    )

    fun withAccessibilityPermission(granted: Boolean): ServiceState = copy(
        accessibilityPermissionGranted = granted,
        lastUpdated = System.currentTimeMillis()
    )

    fun allPermissionsGranted(): Boolean = overlayPermissionGranted && accessibilityPermissionGranted
    fun allServicesRunning(): Boolean = floatingServiceRunning && floatingMenuServiceRunning && accessibilityServiceRunning
}