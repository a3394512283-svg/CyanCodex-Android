package com.canglan.cyancodex.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.ime.CyanCodexImeSettings
import com.canglan.cyancodex.overlay.FloatWindowService
import com.canglan.cyancodex.ui.AppState
import kotlinx.coroutines.launch
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.safeApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var savedMsg by remember { mutableStateOf("") }
    var reduceMotion by remember { mutableStateOf(Session.reduceMotion) }
    var animScale by remember { mutableStateOf(Session.animationsScale) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var floatRunning by remember { mutableStateOf(FloatWindowService.running) }
    var imeEnabled by remember { mutableStateOf(isImeEnabled(ctx)) }
    var quickCount by remember { mutableStateOf(Session.quickCommands().size) }
    var notifEnabled by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var plugins by remember { mutableStateOf(com.canglan.cyancodex.plugin.PluginManager.list()) }

    fun checkOverlay() {
        canOverlay = Settings.canDrawOverlays(ctx)
        floatRunning = FloatWindowService.running
        notifEnabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        plugins = com.canglan.cyancodex.plugin.PluginManager.list()
    }

    // 申请消息推送权限
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifEnabled = it
    }

    // 从系统设置页返回时自动刷新权限/运行状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkOverlay()
                imeEnabled = isImeEnabled(ctx)
                quickCount = Session.quickCommands().size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "设置 / 优化", icon = Icons.Default.Settings, onBack = { nav.popBackStack() })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ===== 性能优化 =====
            SectionCard("性能优化") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("减少动画效果", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = reduceMotion, onCheckedChange = {
                        reduceMotion = it
                        Session.reduceMotion = it
                        // 全局动画缩放 (降低系统动画)
                        try {
                            Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (it) 0f else 1f)
                            Settings.Global.putFloat(ctx.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, if (it) 0f else 1f)
                            Settings.Global.putFloat(ctx.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, if (it) 0f else 1f)
                        } catch (e: Exception) { /* 无 WRITE_SETTINGS 权限时忽略 */ }
                    })
                }
                Text("动画缩放: ${"%.2f".format(animScale)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = animScale, onValueChange = {
                    animScale = it
                    Session.animationsScale = it
                }, valueRange = 0.25f..1f)
                Text("说明: 降低动画可减少 GPU 开销, 提升低端机流畅度。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        Session.clearCacheData()
                        quickCount = Session.quickCommands().size
                        savedMsg = "已清理本地缓存"
                    }, modifier = Modifier.weight(1f)) { Text("清除本地缓存") }
                    OutlinedButton(onClick = { nav.navigate(Routes.ABOUT) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("关于")
                    }
                }
                OutlinedButton(onClick = { nav.navigate(Routes.FEEDBACK) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Feedback, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("意见申诉")
                }
            }

            // ===== 外观 =====
            SectionCard("外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "跟随系统", "light" to "白天", "dark" to "夜晚").forEach { (k, v) ->
                        FilterChip(selected = AppState.themeMode == k, onClick = { AppState.applyThemeMode(k) },
                            label = { Text(v) })
                    }
                }
                Text("默认跟随系统。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 自定义背景图片 (意见 id=22): 优先于插件渐变背景
                var bgImg by remember { mutableStateOf(Session.backgroundImage) }
                val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val target = java.io.File(ctx.filesDir, "custom_bg.jpg")
                            val ok = runCatching {
                                ctx.contentResolver.openInputStream(uri)?.use { input ->
                                    target.outputStream().use { input.copyTo(it) }
                                }
                            }.isSuccess
                            if (ok) { Session.backgroundImage = target.absolutePath; bgImg = target.absolutePath; savedMsg = "背景图片已设置" }
                            else savedMsg = "图片保存失败"
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (bgImg.isNotBlank()) "背景图片已设置" else "自定义背景图片（主页）",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { bgPicker.launch("image/*") }, modifier = Modifier.padding(end = 6.dp)) {
                        Text(if (bgImg.isNotBlank()) "更换" else "选择图片")
                    }
                    if (bgImg.isNotBlank()) {
                        TextButton(onClick = { Session.backgroundImage = ""; bgImg = ""; savedMsg = "已恢复默认背景" }) {
                            Text("清除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // ===== 输入法模式 =====
            SectionCard("输入法模式") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (imeEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                        tint = if (imeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (imeEnabled) "CyanCodex 指令输入法已启用" else "输入法未启用, 需要在系统设置中开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (imeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Text("启用后, 游戏聊天框内切换到此输入法即可一键插入指令。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }, modifier = Modifier.weight(1f)) { Text("去系统设置启用") }
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(ctx, CyanCodexImeSettings::class.java))
                    }, modifier = Modifier.weight(1f)) { Text("管理快捷指令($quickCount)") }
                }
            }

            // ===== 悬浮窗 =====
            SectionCard("悬浮窗 (游戏内快捷复制)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (canOverlay) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                        tint = if (canOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (canOverlay) "悬浮窗权限已授予" else "需要「显示在其他应用上层」权限",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Text(if (floatRunning) "悬浮窗运行中" else "悬浮窗未运行", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!canOverlay) {
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")))
                        }, modifier = Modifier.weight(1f)) { Text("去授权") }
                    } else if (!floatRunning) {
                        Button(onClick = {
                            FloatWindowService.start(ctx)
                            floatRunning = true
                        }, modifier = Modifier.weight(1f)) { Text("开启悬浮窗") }
                    } else {
                        OutlinedButton(onClick = {
                            FloatWindowService.stop(ctx)
                            floatRunning = false
                        }, modifier = Modifier.weight(1f)) { Text("停止悬浮窗") }
                    }
                }
                Text("使用: 授权后开启悬浮窗, 游戏内点悬浮球即可复制指令。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("无法显示? 卓易通/双开等虚拟环境请到系统设置→应用→本应用→「显示在其他应用上层」再次确认授权; 若游戏全屏看不到, 可先在普通桌面开启悬浮窗再进游戏。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // ===== 悬浮窗调节 (对标 BNM) =====
                if (canOverlay) {
                    Spacer(Modifier.height(8.dp))
                    Text("悬浮窗调节", style = MaterialTheme.typography.titleSmall)
                    // 悬浮球大小
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("悬浮球大小", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        listOf("小" to 44, "中" to 52, "大" to 64).forEach { (label, dp) ->
                            FilterChip(
                                selected = Session.floatBallSizeDp == dp,
                                onClick = { Session.floatBallSizeDp = dp },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    // 悬浮窗大小
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("悬浮窗大小", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        listOf("小" to (220 to 380), "中" to (300 to 520), "大" to (400 to 680)).forEach { (label, wh) ->
                            FilterChip(
                                selected = Session.floatWidthDp == wh.first,
                                onClick = {
                                    Session.floatWidthDp = wh.first
                                    Session.floatHeightDp = wh.second
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    // 透明度
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("透明度 ${(Session.floatOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        Slider(
                            value = Session.floatOpacity,
                            onValueChange = { Session.floatOpacity = it },
                            valueRange = 0.2f..1f,
                            modifier = Modifier.weight(1.6f),
                        )
                    }
                    Text("下次开启悬浮窗时生效（运行中可在悬浮窗内 ⚙ 实时调节）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ===== 消息通知 =====
            SectionCard("消息通知") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (notifEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                        tint = if (notifEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (notifEnabled) "消息推送已开启" else "消息推送未开启，新公告/站内信将无法提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notifEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Text("后台每 15 分钟自动检查新公告与站内信并推送系统通知（App 在后台/手机待机也会检查）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!notifEnabled) {
                        Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.weight(1f)) { Text("申请消息推送权限") }
                    } else {
                        OutlinedButton(onClick = { openNotificationSettings(ctx) },
                            modifier = Modifier.weight(1f)) { Text("通知设置") }
                    }
                }
            }

            // ===== 插件 (开发平台) =====
            SectionCard("插件 (开发平台)") {
                Text("插件是独立打包的 JAR, 可扩展 App 功能; 插件默认关闭, 打开开关才生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                plugins.forEach { lp ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${lp.plugin.name} v${lp.plugin.version}", style = MaterialTheme.typography.bodyMedium)
                            Text(lp.plugin.description, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lp.enabled, onCheckedChange = {
                            com.canglan.cyancodex.plugin.PluginManager.setEnabled(ctx, lp.plugin.id, it)
                            plugins = com.canglan.cyancodex.plugin.PluginManager.list()
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { nav.navigate(Routes.PLUGIN_DEV) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Extension, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("开发平台")
                    }
                    OutlinedButton(onClick = {
                        com.canglan.cyancodex.plugin.PluginManager.reload(ctx)
                        plugins = com.canglan.cyancodex.plugin.PluginManager.list()
                        savedMsg = "插件已重新加载"
                    }, modifier = Modifier.weight(1f)) { Text("重新加载") }
                }
                Text("插件目录: ${com.canglan.cyancodex.plugin.PluginManager.pluginDir(ctx).absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ===== 两步验证 (身份验证器 TOTP) =====
            SectionCard("两步验证 (身份验证器)") {
                var totpEnabled by remember { mutableStateOf(false) }
                var setup by remember { mutableStateOf<com.canglan.cyancodex.data.TotpSetup?>(null) }
                var totpInput by remember { mutableStateOf("") }
                var totpMsg by remember { mutableStateOf("") }
                var totpBusy by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val r = safeApi { ApiClient.get().totpStatus() }
                    if (r.ok) totpEnabled = r.data?.enabled ?: false
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (totpEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                        tint = if (totpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (totpEnabled) "两步验证已开启" else "两步验证未开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (totpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Text("开启后登录需输入身份验证器（Google Authenticator 等）的 6 位验证码，秘钥由服务器密钥派生。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!totpEnabled) {
                    Button(onClick = {
                        scope.launch {
                            totpBusy = true; totpMsg = ""
                            val r = safeApi { ApiClient.get().totpSetup() }
                            totpBusy = false
                            if (r.ok) { setup = r.data; totpInput = "" } else totpMsg = r.error ?: "获取失败"
                        }
                    }, enabled = !totpBusy, modifier = Modifier.fillMaxWidth()) { Text("开启两步验证") }
                } else {
                    OutlinedButton(onClick = {
                        setup = com.canglan.cyancodex.data.TotpSetup(); totpInput = ""; totpMsg = "输入验证码后关闭"
                    }, modifier = Modifier.fillMaxWidth()) { Text("关闭两步验证") }
                }
                if (totpMsg.isNotBlank()) Text(totpMsg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                setup?.let { s ->
                    AlertDialog(
                        onDismissRequest = { setup = null },
                        title = { Text(if (totpEnabled) "关闭两步验证" else "开启两步验证") },
                        text = {
                            Column {
                                if (!totpEnabled) {
                                    Text("在身份验证器中添加以下密钥（或复制 otpauth 链接手动添加），然后输入验证码确认：",
                                        style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(6.dp))
                                    Text(s.secret, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(s.otpauthUri, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = totpInput, onValueChange = { totpInput = it },
                                    label = { Text("6 位验证码") }, singleLine = true)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    totpBusy = true
                                    val r = if (totpEnabled)
                                        safeApi { ApiClient.get().totpDisable(mapOf("code" to totpInput.trim())) }
                                    else safeApi { ApiClient.get().totpVerify(mapOf("code" to totpInput.trim())) }
                                    totpBusy = false
                                    if (r.ok) {
                                        totpEnabled = !totpEnabled
                                        setup = null; totpInput = ""; totpMsg = ""
                                        savedMsg = if (totpEnabled) "两步验证已开启" else "两步验证已关闭"
                                    } else totpMsg = r.error ?: "验证码错误"
                                }
                            }, enabled = !totpBusy) { Text(if (totpEnabled) "关闭" else "确认开启") }
                        },
                        dismissButton = { TextButton(onClick = { setup = null }) { Text("取消") } },
                    )
                }
            }

            if (savedMsg.isNotBlank()) {
                Text(savedMsg, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }

            Text("世纪集团旗下 · 沧蓝工坊工作室 · CyanCodex v${com.canglan.cyancodex.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private fun isImeEnabled(ctx: android.content.Context): Boolean {
    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val id = "${ctx.packageName}/.ime.CyanCodexIme"
    return imm.enabledInputMethodList.any { it.id == id }
}

private fun openNotificationSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    } catch (e: Exception) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
