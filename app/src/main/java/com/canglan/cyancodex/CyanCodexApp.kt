package com.canglan.cyancodex

import android.app.Application
import com.canglan.cyancodex.data.AnnouncementNotifier
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.data.TamperDetector
import com.canglan.cyancodex.plugin.PluginManager

class CyanCodexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        // 客户端检测: 启动即计算当前 APK 签名, 后续所有请求携带 X-App-Signature
        TamperDetector.init(this)
        // 插件系统: 复制内置插件 + 加载 (设置→插件 可管理)
        PluginManager.init(this)
        // 新公告系统通知: 建渠道 + 立即检查一次 + 注册周期检查
        AnnouncementNotifier.ensureChannel(this)
        AnnouncementNotifier.checkNow(this)
        AnnouncementNotifier.schedule(this)
    }
}
