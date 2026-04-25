package com.jdhelper.app.data.repository

import android.content.Context
import android.provider.Settings
import com.jdhelper.app.domain.model.ServiceState
import com.jdhelper.app.domain.repository.ServiceStateRepository
import com.jdhelper.app.service.AccessibilityClickService
import com.jdhelper.app.service.FloatingMenuService
import com.jdhelper.app.service.FloatingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceStateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ServiceStateRepository {

    private val _serviceState = MutableStateFlow(ServiceState.INITIAL)

    override fun getCurrentState(): ServiceState = _serviceState.value

    override fun getStateFlow(): Flow<ServiceState> = _serviceState.asStateFlow()

    override suspend fun updateState(state: ServiceState) {
        _serviceState.value = state
    }

    override suspend fun updateFloatingService(running: Boolean) {
        _serviceState.value = _serviceState.value.withFloatingService(running)
    }

    override suspend fun updateFloatingMenuService(running: Boolean) {
        _serviceState.value = _serviceState.value.withFloatingMenuService(running)
    }

    override suspend fun updateAccessibilityService(running: Boolean) {
        _serviceState.value = _serviceState.value.withAccessibilityService(running)
    }

    override suspend fun updateOverlayPermission(granted: Boolean) {
        _serviceState.value = _serviceState.value.withOverlayPermission(granted)
    }

    override suspend fun updateAccessibilityPermission(granted: Boolean) {
        _serviceState.value = _serviceState.value.withAccessibilityPermission(granted)
    }

    override suspend fun checkAllPermissions(): Boolean {
        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityGranted = checkAccessibilityEnabled()
        updateOverlayPermission(overlayGranted)
        updateAccessibilityPermission(accessibilityGranted)
        return overlayGranted && accessibilityGranted
    }

    override suspend fun checkAllServices(): Boolean {
        val floatingRunning = FloatingService.isRunning()
        val menuRunning = FloatingMenuService.isRunning()
        val accessibilityRunning = AccessibilityClickService.getInstance() != null
        updateFloatingService(floatingRunning)
        updateFloatingMenuService(menuRunning)
        updateAccessibilityService(accessibilityRunning)
        return floatingRunning && menuRunning && accessibilityRunning
    }

    override suspend fun getMissingPermissions(): List<String> {
        val state = _serviceState.value
        return buildList {
            if (!state.overlayPermissionGranted) add("悬浮窗权限")
            if (!state.accessibilityPermissionGranted) add("无障碍服务权限")
        }
    }

    override suspend fun getMissingServices(): List<String> {
        val state = _serviceState.value
        return buildList {
            if (!state.floatingServiceRunning) add("悬浮时钟服务")
            if (!state.floatingMenuServiceRunning) add("悬浮菜单服务")
            if (!state.accessibilityServiceRunning) add("无障碍点击服务")
        }
    }

    private fun checkAccessibilityEnabled(): Boolean {
        return AccessibilityClickService.getInstance() != null
    }
}