package com.canglan.cyancodex.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.BuildConfig
import com.canglan.cyancodex.data.Announcement
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Updater
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.safeApi
import com.canglan.cyancodex.ui.components.versionNewer
import kotlinx.coroutines.launch

const val QQ_GROUP_NUMBER = "1065025874"
const val QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=hwdWfjhcrT2gyREXBglanWfdHTMjrjUvEvVSSg8%2FVGx8E10%2FEVdfp3H0OJPPTPdi&busi_data=eyJncm91cENvZGUiOiIxMDY1MDI1ODc0IiwidG9rZW4iOiJiZ0JFVG9Uakc3ZDcrZkZyK0cxVStZOEErYU1Ja1o5S1ZjUFlYM0padVdsVUNGKytrZ1lidvkrakZ5YjhmbWR4IiwidWluIjoiMzYxNTc4NTc2OSJ9&data=ZezSPJyBUEcAwSLrXEOkeiTVTvEZK8FRMEkY2BQdfpP_GIPfHokXrTLjcvEzf7ZehLL-nD00FXXW5z7Z2chqZA&svctype=4&tempid=h5_group_info"
// 赞助链接 (爱发电)
const val SPONSOR_URL = "https://ifdian.net/a/canglanworkshop"

/** 打开 QQ 群: 直接唤起 QQ 并在其内置浏览器加载群邀请链接 (不经过外部浏览器) */
fun joinQqGroup(context: android.content.Context) {
    // mqqapi://forward/url 让 QQ 内置浏览器直接打开群邀请链接
    val encoded = Uri.encode(QQ_GROUP_URL)   // ≈ encodeURIComponent
    val qq = Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://forward/url?url_prefix=$encoded&src_type=web&version=1"))
    qq.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (qq.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(qq) }
            .onFailure { openWebFallback(context) }
    } else {
        openWebFallback(context)
    }
}

private fun openWebFallback(context: android.content.Context) {
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL))
    runCatching { context.startActivity(web) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

/** QQ 群卡片 (首页/关于页共用) */
@Composable
fun QqGroupCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Card(onClick = { joinQqGroup(ctx) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("加入我们的QQ群", style = MaterialTheme.typography.titleSmall)
                Text("群号: $QQ_GROUP_NUMBER · 点击加入，一起交流指令", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.canglan.cyancodex.data.UpdateInfo?>(null) }
    var checkMsg by remember { mutableStateOf("") }

    fun checkUpdate() {
        if (checking) return
        scope.launch {
            checking = true
            checkMsg = ""
            val r = safeApi { ApiClient.get().latestUpdate() }
            checking = false
            if (!r.ok) { checkMsg = r.error ?: "检查失败, 请稍后重试"; return@launch }
            val up = r.data
            if (up != null && versionNewer(up.version)) {
                updateInfo = up
            } else {
                checkMsg = "当前已是最新版本 v${BuildConfig.VERSION_NAME}"
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "关于 CyanCodex", icon = Icons.Default.Info, onBack = { nav.popBackStack() })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.canglan.cyancodex.R.drawable.usericon),
                contentDescription = "CyanCodex",
                modifier = Modifier.size(80.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
            )
            Text("CyanCodex", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            // 世纪集团旗下 (收录合作方)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(com.canglan.cyancodex.R.drawable.shiji),
                    contentDescription = "世纪集团",
                    modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text("世纪集团旗下", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            // 矩阵方块协助开发
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(com.canglan.cyancodex.R.drawable.jzfk),
                    contentDescription = "矩阵方块",
                    modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text("矩阵方块协助开发", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            // 沧蓝工坊 (logo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(com.canglan.cyancodex.R.drawable.logo_canglan),
                    contentDescription = "沧蓝工坊",
                    modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text("沧蓝工坊", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text("由 沧蓝工坊工作室 开发", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { checkUpdate() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (checking) "检查中…" else "检查更新")
            }
            if (checkMsg.isNotBlank()) {
                Text(checkMsg, style = MaterialTheme.typography.labelMedium,
                    color = if (checkMsg.startsWith("当前")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            QqGroupCard()
            Spacer(Modifier.height(8.dp))
            // 赞助支持 (爱发电)
            Card(onClick = {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_URL))
                runCatching { ctx.startActivity(web) }
                    .onFailure { Toast.makeText(ctx, "无法打开赞助链接", Toast.LENGTH_SHORT).show() }
            }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFF87171))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("赞助支持", style = MaterialTheme.typography.titleSmall)
                        Text("请开发团队喝杯奶茶，支持项目持续更新", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("产品定位", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("一站式我的世界指令创作、资源社区、快捷调用工具。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("功能模块", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("""· 指令库: 用户上传 + 平台资源(高质/低质分级, 不定时更新)
· 指令编辑器: 快捷编辑修改游戏指令
· 输入法模式: 指令接入输入法, 游戏内快捷调用
· 优化: App 性能优化
· T显生成器: 计分板/T显示指令
· 悬浮窗: 全局悬浮窗, 游戏内快速复制
· 穷举: 批量生成同构不同数据指令
· AI Agent: 智能编写复杂指令(付费/积分解锁, 支持自备 API)
· 音乐生成器: 命令方块音乐指令
· 滚动字幕: 公告/滚动字幕指令""", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text("高级能力支持付费或积分解锁。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("© 2026 世纪集团 · 沧蓝工坊工作室 · CyanCodex", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // 发现新版本弹窗
    updateInfo?.let { up ->
        var progress by remember { mutableStateOf(-1f) }   // -1=未下载, 0..1=进度, -2=未知大小
        LaunchedEffect(up.url) { progress = -1f }
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${up.version}") },
            text = { Text("最新版本 v${up.version} 已发布，点击「下载安装」一键更新。") },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress >= 0f) {
                        Text(if (progress == -2f) "下载中…" else "下载中 ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { updateInfo = null }) { Text("知道了") }
                    if (progress < 0f) {
                        TextButton(onClick = {
                            val url = com.canglan.cyancodex.data.Session.baseUrl.trimEnd('/') + up.url
                            scope.launch {
                                val file = Updater.download(ctx, url) { done, total ->
                                    progress = if (total > 0) done.toFloat() / total else -2f
                                }
                                updateInfo = null
                                if (file == null || !Updater.install(ctx, file)) {
                                    checkMsg = "下载失败，已尝试用浏览器打开下载页面"
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
