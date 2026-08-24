package com.canglan.cyancodex.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.canglan.cyancodex.BuildConfig
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Announcement
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.data.TamperDetector
import com.canglan.cyancodex.data.UpdateInfo
import com.canglan.cyancodex.data.Updater
import com.canglan.cyancodex.ui.components.safeApi
import com.canglan.cyancodex.ui.components.versionBelow
import com.canglan.cyancodex.ui.components.versionNewer
import com.canglan.cyancodex.ui.screens.*
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val FEED = "feed"
    const val GENERATORS = "generators"
    const val AI = "ai"
    const val PROFILE = "profile"
    const val PROFILE_EDIT = "profile_edit"
    const val COMMAND_DETAIL = "command/{id}"
    const val EDITOR = "editor?cmd={cmd}&id={id}"
    const val SCOREBOARD = "scoreboard"
    const val ENUMERATE = "enumerate"
    const val SCROLLER = "scroller"
    const val MUSIC = "music"
    const val HISTORY = "history"
    const val LOGIN = "login"
    const val CHECKIN = "checkin"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val TUTORIAL = "tutorial/{id}"
    const val USER = "user/{id}"
    const val ANNOUNCEMENTS = "announcements"
    const val POINTS_LOG = "points_log"
    const val MESSAGES = "messages"
    const val CHANGE_PASSWORD = "change_password"
    const val CHANGE_EMAIL = "change_email"
    const val TITLERAW_JSON = "titleraw_json"
    const val FEEDBACK = "feedback"
    const val AI_HISTORY = "ai_history"
    const val PLUGIN_DEV = "plugin_dev"
    const val CHAT = "chat"
    const val FRIENDS = "friends"
    const val PLUGINS = "plugins"
    const val DM = "dm/{peerId}/{peerName}"

    fun dm(peerId: Long, peerName: String): String =
        "dm/$peerId/${java.net.URLEncoder.encode(peerName, "UTF-8")}"

    fun commandDetail(id: Long) = "command/$id"
    fun tutorial(id: Long) = "tutorial/$id"
    fun user(id: Long) = "user/$id"
    fun editor(cmd: String = "", id: Long? = null): String =
        "editor?cmd=${java.net.URLEncoder.encode(cmd, "UTF-8")}&id=${id ?: -1}"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Default.Home),
    TabItem(Routes.LIBRARY, "指令库", Icons.Default.Book),
    TabItem(Routes.FEED, "广场", Icons.Default.Forum),
    TabItem(Routes.GENERATORS, "生成器", Icons.Default.Build),
    TabItem(Routes.AI, "AI", Icons.Default.AutoAwesome),
    TabItem(Routes.PROFILE, "我的", Icons.Default.Person),
)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // ===== 客户端检测: 第三方客户端/篡改 -> 上报服务器封禁账号, 全功能阻断 =====
    val activity = (LocalContext.current as? android.app.Activity)
    val tampered = !TamperDetector.isOfficial()
    var tamperReported by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (tampered && !tamperReported && Session.token != null) {
            tamperReported = true
            runCatching {
                ApiClient.get().reportTamper(mapOf("reason" to "tampered-client"))
            }
        }
    }
    if (tampered) {
        AlertDialog(
            onDismissRequest = {},   // 篡改客户端: 不可关闭
            title = { Text("检测到第三方客户端") },
            text = {
                Column {
                    Text("当前客户端签名与官方版本不符，可能为第三方客户端或已被篡改。")
                    Spacer(Modifier.height(4.dp))
                    Text("为保护您的账号安全，本账号已被封禁，无法继续使用。")
                    if (tamperReported || Session.token == null) {
                        Spacer(Modifier.height(8.dp))
                        Text("请卸载当前客户端，从官方渠道重新下载安装。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("退出应用") }
            },
        )
        return
    }

    // ===== 强制更新拦截: 版本低于服务器最低支持版本时不可使用 =====
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var forceUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var fuProgress by remember { mutableStateOf(-1f) }   // -1=未下载, 0..1=进度, -2=未知大小
    LaunchedEffect(Unit) {
        val r = runCatching { ApiClient.get().latestUpdate() }.getOrNull()
        val d = r?.data
        if (d != null && d.minVersion.isNotBlank() && versionBelow(BuildConfig.VERSION_NAME, d.minVersion)) {
            forceUpdate = d
        }
    }
    forceUpdate?.let { u ->
        AlertDialog(
            onDismissRequest = {},   // 强制更新: 不可关闭
            title = { Text("版本过低，无法使用") },
            text = {
                Column {
                    Text("当前版本 ${BuildConfig.VERSION_NAME} 已停止支持（最低要求 ${u.minVersion}）。")
                    Spacer(Modifier.height(4.dp))
                    Text("请下载安装最新版后继续使用。")
                    if (fuProgress >= 0f) {
                        Spacer(Modifier.height(8.dp))
                        Text(if (fuProgress == -2f) "下载中…" else "下载中 ${(fuProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                if (fuProgress < 0f) {
                    TextButton(onClick = {
                        scope.launch {
                            val url = Updater.resolveUrl(null)
                            if (url == null) { Updater.openInBrowser(ctx, Updater.apkUrl(u.version)); return@launch }
                            val file = Updater.download(ctx, url) { done, total ->
                                fuProgress = if (total > 0) done.toFloat() / total else -2f
                            }
                            if (file == null || !Updater.install(ctx, file)) {
                                Updater.openInBrowser(ctx, url)
                            }
                        }
                    }) { Text("下载安装", color = MaterialTheme.colorScheme.primary) }
                }
            },
            dismissButton = {
                TextButton(onClick = { activity?.finish() }) { Text("退出应用") }
            },
        )
    }

    // ===== 登录门禁: 未登录/未注册不可使用 App, 首次进入引导注册 =====
    if (AppState.token == null) {
        AuthScreen(navController, asGate = true)
        return
    }

    // 系统通知点击跳转: 公告 / 站内信 (intent extra "goto")
    LaunchedEffect(Unit) {
        val goto = activity?.intent?.getStringExtra("goto") ?: return@LaunchedEffect
        activity.intent.removeExtra("goto")
        when (goto) {
            "messages" -> navController.navigate(Routes.MESSAGES)
            "announcements" -> navController.navigate(Routes.ANNOUNCEMENTS)
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = {
                                if (currentRoute != t.route) {
                                    // 简单可靠: 清掉首页之上的页面, 再进入目标 Tab
                                    // (saveState/restoreState 状态恢复在本机有错乱问题, 弃用)
                                    navController.navigate(t.route) {
                                        popUpTo(Routes.HOME)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            // 过渡动画统一 0.3s, 观感更流畅
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) },
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.LIBRARY) { LibraryScreen(navController) }
            composable(Routes.FEED) { FeedScreen(navController) }
            composable(Routes.GENERATORS) { GeneratorsScreen(navController) }
            composable(Routes.AI) { AiScreen(navController) }
            composable(Routes.PROFILE) { ProfileScreen(navController) }
            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(navController) }

            composable(
                Routes.USER,
                arguments = listOf(navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) { UserProfileScreen(navController, it.arguments?.getLong("id") ?: 0L) }

            composable(
                Routes.COMMAND_DETAIL,
                arguments = listOf(navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) { CommandDetailScreen(navController, it.arguments?.getLong("id") ?: 0L) }

            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("cmd") { defaultValue = "" },
                    navArgument("id") { defaultValue = -1L },
                ),
            ) { entry ->
                val cmd = entry.arguments?.getString("cmd") ?: ""
                val id = entry.arguments?.getLong("id") ?: -1L
                CommandEditorScreen(navController, cmd, id)
            }

            composable(Routes.SCOREBOARD) { ScoreboardScreen(navController) }
            composable(Routes.ENUMERATE) { EnumerateScreen(navController) }
            composable(Routes.SCROLLER) { ScrollerScreen(navController) }
            composable(Routes.MUSIC) { MusicScreen(navController) }
            composable(Routes.HISTORY) { HistoryScreen(navController) }
            composable(Routes.LOGIN) { AuthScreen(navController) }
            composable(Routes.CHECKIN) { CheckInScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.ABOUT) { AboutScreen(navController) }
            composable(Routes.ANNOUNCEMENTS) { AnnouncementsScreen(navController) }
            composable(Routes.POINTS_LOG) { PointsLogScreen(navController) }
            composable(Routes.MESSAGES) { MessagesScreen(navController) }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(navController) }
            composable(Routes.CHANGE_EMAIL) { ChangeEmailScreen(navController) }
            composable(Routes.TITLERAW_JSON) { TitlerawJsonScreen(navController) }
            composable(Routes.FEEDBACK) { FeedbackScreen(navController) }
            composable(Routes.AI_HISTORY) { AiHistoryScreen(navController) }
            composable(Routes.PLUGIN_DEV) { PluginDevScreen(navController) }
            composable(Routes.CHAT) { ChatScreen(navController) }
            composable(Routes.FRIENDS) { FriendsScreen(navController) }
            composable(Routes.PLUGINS) { PluginScreen(navController) }
            composable(
                Routes.DM,
                arguments = listOf(
                    navArgument("peerId") { type = androidx.navigation.NavType.LongType },
                    navArgument("peerName") { defaultValue = "" },
                ),
            ) { entry ->
                val peerId = entry.arguments?.getLong("peerId") ?: 0L
                val peerName = entry.arguments?.getString("peerName") ?: ""
                DmScreen(navController, peerId, peerName)
            }
            composable(
                Routes.TUTORIAL,
                arguments = listOf(navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) { TutorialScreen(navController, it.arguments?.getLong("id") ?: 0L) }
        }
    }

    // ===== 启动检查: 新公告弹窗 (含更新公告的下载安装) =====
    var annDialog by remember { mutableStateOf<Announcement?>(null) }
    var dlProgress by remember { mutableStateOf(-1f) }   // -1=未下载, 0..1=进度, -2=未知大小
    LaunchedEffect(Unit) {
        val r = safeApi { ApiClient.get().announcements("all") }
        if (r.ok) {
            val anns = r.data ?: emptyList()
            val unseen = anns.filter { it.id > Session.lastSeenAnnouncementId }
            if (unseen.isNotEmpty()) annDialog = unseen.first()
            anns.maxOfOrNull { it.id }?.let { Session.lastSeenAnnouncementId = it }
        }
    }
    annDialog?.let { a ->
        AlertDialog(
            onDismissRequest = { annDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnnTypeBadge(a.type)
                    Spacer(Modifier.width(8.dp))
                    Text(a.title)
                }
            },
            text = { Text(a.content) },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isUpdate = a.type == "update" && versionNewer(a.version)
                    if (isUpdate && dlProgress >= 0f) {
                        Text(if (dlProgress == -2f) "下载中…" else "下载中 ${(dlProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { annDialog = null }) { Text("知道了") }
                    if (isUpdate && dlProgress < 0f) {
                        TextButton(onClick = {
                            scope.launch {
                                val url = Updater.resolveUrl(a.version)
                                if (url == null) { annDialog = null; return@launch }
                                val file = Updater.download(ctx, url) { done, total ->
                                    dlProgress = if (total > 0) done.toFloat() / total else -2f
                                }
                                annDialog = null
                                if (file == null || !Updater.install(ctx, file)) {
                                    Updater.openInBrowser(ctx, url)
                                }
                            }
                        }) { Text("下载安装", color = MaterialTheme.colorScheme.primary) }
                    }
                }
            },
        )
    }
}

/** 公告类型徽章 */
@Composable
private fun AnnTypeBadge(type: String) {
    val (label, color) = when (type) {
        "update" -> "更新" to Color(0xFF4ADE80)
        "punish" -> "处罚" to Color(0xFFF87171)
        else -> "公告" to Color(0xFF38BDF8)
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.18f)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
