package com.jdhelper.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.data.local.GiftClickHistory
import com.jdhelper.data.local.GiftClickHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val PREFS_NAME = "click_settings"
private const val KEY_DELAY_MILLIS = "delay_millis"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val giftClickHistoryDao: GiftClickHistoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val historyList: StateFlow<List<GiftClickHistory>> = giftClickHistoryDao.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateHistorySuccess(id: Long, success: Boolean) {
        viewModelScope.launch {
            giftClickHistoryDao.updateSuccess(id, success)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            giftClickHistoryDao.deleteById(id)
        }
    }

    fun restoreDelayFromHistory(delayMillis: Double) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putFloat(KEY_DELAY_MILLIS, delayMillis.toFloat()).apply()
            }
        }
    }
}