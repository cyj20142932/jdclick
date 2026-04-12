package com.jdhelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jdhelper.service.FloatingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机自启逻辑，可以根据配置决定是否启动
            // FloatingService.startService(context)
        }
    }
}