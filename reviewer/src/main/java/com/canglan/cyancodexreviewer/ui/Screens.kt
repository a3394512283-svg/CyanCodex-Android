package com.canglan.cyancodexreviewer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.canglan.cyancodexreviewer.data.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReviewerSession.init(this)
        setContent { ReviewerApp() }
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFF86EFAC),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
)

@Composable
private fun ReviewerApp() {
    var loggedIn by remember { mutableStateOf(ReviewerSession.isLoggedIn()) }
    MaterialTheme(colorScheme = DarkColors) {
        if (loggedIn) MainScreen(onLogout = { ReviewerSession.logout(); loggedIn = false })
        else LoginScreen(onLogin = { loggedIn = true })
    }
}

// ================= 登录页: 邀请码 =================
@Composable
private fun LoginScreen(onLogin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text("CyanCodex 审核员", style = MaterialTheme.typography.headlineSmall)
            Text("沧蓝工坊 · 指令库审核专用", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("请输入管理端发放的邀请码登录\n（一码一设备，绑定后无法在其他设备使用）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = code, onValueChange = { code = it.uppercase().trim() },
                label = { Text("邀请码") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("格式如 CY-XXXX-XXXX-XXXX") })
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    busy = true; error = ""
                    try {
                        val r = ReviewerApiClient.get(ctx).login(mapOf("code" to code, "device_id" to ReviewerDevice.id(ctx)))
                        val d = r.data
                        if (r.ok && d?.token != null) {
                            ReviewerSession.token = d.token
                            ReviewerSession.code = d.code ?: code
                            onLogin()
                        } else {
                            error = r.error ?: "登录失败"
                        }
                    } catch (e: Exception) {
                        error = "无法连接服务器: ${e.message}"
                    } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "登录中…" else "登录")
            }
        }
    }
}

// ================= 主界面: 待审列表 (分页浏览全部) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }   // 0=指令 1=插件
    var list by remember { mutableStateOf<List<ReviewCommand>>(emptyList()) }
    var stats by remember { mutableStateOf<ReviewStats?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    // 插件审核状态
    var pluginList by remember { mutableStateOf<List<ReviewPlugin>>(emptyList()) }
    var pluginMsg by remember { mutableStateOf("") }
    var pluginBusy by remember { mutableStateOf(false) }
    // 驳回理由弹窗 (指令)
    var rejectTarget by remember { mutableStateOf<ReviewCommand?>(null) }
    var rejectReason by remember { mutableStateOf("") }
    // 驳回理由弹窗 (插件)
    var pluginRejectTarget by remember { mutableStateOf<ReviewPlugin?>(null) }
    var pluginRejectReason by remember { mutableStateOf("") }

    suspend fun loadPlugins() {
        val r = safeApi { ReviewerApiClient.get(ctx).pendingPlugins() }
        if (r.ok) { pluginList = r.data ?: emptyList(); pluginMsg = "" }
        else pluginMsg = r.error ?: "加载失败"
    }

    suspend fun load(reset: Boolean) {
        val target = if (reset) 1 else page + 1
        val r = safeApi { ReviewerApiClient.get(ctx).pending(target, 20) }
        if (r.ok) {
            val data = r.data
            if (data != null) {
                total = data.total
                list = if (reset) data.list else (list + data.list).distinctBy { it.id }
                page = target
            }
            if (reset) msg = ""
        } else {
            msg = r.error ?: "加载失败"
        }
        val s = safeApi { ReviewerApiClient.get(ctx).stats() }
        if (s.ok) stats = s.data
    }

    suspend fun loadMore() {
        if (loadingMore || list.size >= total) return
        loadingMore = true
        load(false)
        loadingMore = false
    }

    LaunchedEffect(Unit) { load(true) }
    LaunchedEffect(tab) { if (tab == 1) loadPlugins() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (tab == 0) "指令审核 · ${stats?.pending ?: "?"} 条待审" else "插件审核 · ${pluginList.size} 个待审") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { scope.launch { if (tab == 0) load(true) else loadPlugins() } }) { Icon(Icons.Default.Refresh, "刷新") }
                    TextButton(onClick = onLogout) { Text("退出", color = MaterialTheme.colorScheme.error) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Tab: 指令 / 插件
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("指令") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("插件") })
            }
            if (tab == 0) {
                // ===== 指令审核 =====
                Box(Modifier.fillMaxSize()) {
                    if (msg.isNotBlank() && list.isEmpty()) {
                        Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    if (list.isEmpty() && msg.isBlank()) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无待审核指令 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("已审核 ${stats?.reviewed ?: 0} 条", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.id }) { c ->
                            CommandCard(c, busy,
                                onApprove = {
                                    scope.launch {
                                        busy = true; msg = ""
                                        try {
                                            val r = ReviewerApiClient.get(ctx).review(c.id, mapOf("status" to "active"))
                                            if (r.ok) load(true)
                                            else msg = r.error ?: "操作失败"
                                        } catch (e: Exception) {
                                            msg = "网络错误: ${e.message}"
                                        } finally { busy = false }
                                    }
                                },
                                onReject = {
                                    rejectTarget = c
                                    rejectReason = ""
                                },
                            )
                        }
                        // 加载更多 (浏览全部)
                        if (list.isNotEmpty() && list.size < total) {
                            item {
                                OutlinedButton(
                                    onClick = { scope.launch { loadMore() } },
                                    enabled = !loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (loadingMore) "加载中…" else "加载更多（已看 ${list.size}/${total}）")
                                }
                            }
                        }
                    }
                }
            } else {
                // ===== 插件审核 =====
                Box(Modifier.fillMaxSize()) {
                    if (pluginMsg.isNotBlank() && pluginList.isEmpty()) {
                        Text(pluginMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    if (pluginList.isEmpty() && pluginMsg.isBlank()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无待审插件 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pluginList, key = { it.id }) { pl ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${pl.id} · ${pl.title}", style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f))
                                        CategoryBadge("插件")
                                    }
                                    Text("作者：${pl.author ?: "?"} · v${pl.version} · ${(pl.size / 1024)}KB · ${pl.createdAt?.take(16) ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (pl.description.isNotBlank()) {
                                        Text(pl.description, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                                    }
                                    Text("通过前将执行恶意代码安全扫描",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            scope.launch {
                                                pluginBusy = true; pluginMsg = ""
                                                try {
                                                    val r = ReviewerApiClient.get(ctx).reviewPlugin(pl.id, mapOf("status" to "active"))
                                                    if (r.ok) loadPlugins()
                                                    else pluginMsg = r.error ?: "操作失败"
                                                } catch (e: Exception) {
                                                    pluginMsg = "网络错误: ${e.message}"
                                                } finally { pluginBusy = false }
                                            }
                                        }, enabled = !pluginBusy, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("通过")
                                        }
                                        OutlinedButton(onClick = {
                                            pluginRejectTarget = pl
                                            pluginRejectReason = ""
                                        }, enabled = !pluginBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("驳回")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 驳回理由弹窗
    rejectTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("驳回指令 #${c.id}") },
            text = {
                Column {
                    Text("驳回理由/改进建议（必填，会通知作者）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason, onValueChange = { rejectReason = it },
                        placeholder = { Text("例如：指令缺少 / 开头，请补充后重新提交") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = rejectReason.isNotBlank() && !busy, onClick = {
                    val reason = rejectReason.trim()
                    scope.launch {
                        busy = true; msg = ""
                        try {
                            val r = ReviewerApiClient.get(ctx).review(c.id, mapOf("status" to "rejected", "reason" to reason))
                            if (r.ok) { rejectTarget = null; load(true) }
                            else msg = r.error ?: "操作失败"
                        } catch (e: Exception) {
                            msg = "网络错误: ${e.message}"
                        } finally { busy = false }
                    }
                }) { Text("确认驳回") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("取消") } },
        )
    }

    // 插件驳回理由弹窗
    pluginRejectTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { pluginRejectTarget = null },
            title = { Text("驳回插件 #${pl.id}") },
            text = {
                Column {
                    Text("驳回理由（必填，会通知作者）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pluginRejectReason, onValueChange = { pluginRejectReason = it },
                        placeholder = { Text("例如：包含恶意代码 / 功能说明不完整") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = pluginRejectReason.isNotBlank() && !pluginBusy, onClick = {
                    val reason = pluginRejectReason.trim()
                    scope.launch {
                        pluginBusy = true; pluginMsg = ""
                        try {
                            val r = ReviewerApiClient.get(ctx).reviewPlugin(pl.id, mapOf("status" to "rejected", "reason" to reason))
                            if (r.ok) { pluginRejectTarget = null; loadPlugins() }
                            else pluginMsg = r.error ?: "操作失败"
                        } catch (e: Exception) {
                            pluginMsg = "网络错误: ${e.message}"
                        } finally { pluginBusy = false }
                    }
                }) { Text("确认驳回") }
            },
            dismissButton = { TextButton(onClick = { pluginRejectTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CommandCard(
    c: ReviewCommand,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    // 展开状态: 默认收起, 点击「查看全部」展开完整内容
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${c.id} · ${c.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                CategoryBadge(c.category)
            }
            Text("作者：${c.author ?: "?"} · ${c.createdAt?.take(16) ?: ""} · ${c.mcVersion.ifBlank { "基岩版" }}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.description.isNotBlank()) {
                Text(c.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                // 完整指令内容 (可展开查看全部, 不再只显示前几条)
                Text(c.command, style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis)
            }
            // 展开/收起
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(if (expanded) "收起" else "查看全部内容")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !busy,
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("通过")
                }
                OutlinedButton(onClick = onReject, enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("驳回")
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val (label, color) = when (category) {
        "survival" -> "生存" to Color(0xFF4ADE80)
        "adventure" -> "冒险" to Color(0xFF38BDF8)
        "redstone" -> "红石" to Color(0xFFF87171)
        "scoreboard" -> "计分板" to Color(0xFFFBBF24)
        "gamerule" -> "游戏规则" to Color(0xFFC084FC)
        else -> "其他" to Color(0xFF94A3B8)
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.18f)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

private suspend fun <T> safeApi(block: suspend () -> ApiResp<T>): ApiResp<T> =
    try { block() } catch (e: Exception) {
        android.util.Log.e("ReviewerApi", "safeApi error: ${e.message}", e)
        ApiResp(ok = false, error = e.message)
    }
