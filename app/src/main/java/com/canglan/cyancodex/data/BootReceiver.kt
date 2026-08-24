package com.canglan.cyancodex.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后重排新消息周期检查:
 * AlarmManager 闹钟不跨重启保留, 手机重启后由本接收器重新注册。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = ctx.applicationContext
        AnnouncementNotifier.ensureChannel(app)
        AnnouncementNotifier.schedule(app)
    }
}
