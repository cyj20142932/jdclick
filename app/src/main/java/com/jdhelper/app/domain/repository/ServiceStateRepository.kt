package com.jdhelper.app.domain.repository

import com.jdhelper.app.domain.model.ServiceState
import kotlinx.coroutines.flow.Flow

interface ServiceStateRepository {
    fun getCurrentState(): ServiceState
    fun getStateFlow(): Flow<ServiceState>
    suspend fun updateState(state: ServiceState)
    suspend fun updateFloatingService(running: Boolean)
    suspend fun updateFloatingMenuService(running: Boolean)
    suspend fun updateAccessibilityService(running: Boolean)
    suspend fun updateOverlayPermission(granted: Boolean)
    suspend fun updateAccessibilityPermission(granted: Boolean)
    suspend fun checkAllPermissions(): Boolean
    suspend fun checkAllServices(): Boolean
    suspend fun getMissingPermissions(): List<String>
    suspend fun getMissingServices(): List<String>
}