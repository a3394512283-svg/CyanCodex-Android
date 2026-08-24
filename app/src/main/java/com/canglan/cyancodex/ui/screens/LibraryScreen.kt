package com.canglan.cyancodex.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Command
import com.canglan.cyancodex.data.ImportBody
import com.canglan.cyancodex.data.ImportCmdItem
import com.canglan.cyancodex.data.Tutorial
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.CommandCard
import com.canglan.cyancodex.ui.components.EmptyState
import com.canglan.cyancodex.ui.components.LoadingBox
import com.canglan.cyancodex.ui.components.parseCommandTxt
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.launch

private enum class LibTab(val label: String) {
    PLATFORM("平台"), HIGH("高质"), LOW("低质"), MINE("我的"), FAV("收藏"), TUTORIAL("教程"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(LibTab.PLATFORM) }
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Command>>(emptyList()) }
    var favIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var tuts by remember { mutableStateOf<List<Tutorial>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    val listState = rememberLazyListState()

    // 教程上传对话框
    var showTutDialog by remember { mutableStateOf(false) }
    var tutType by remember { mutableStateOf("post") }
    var tutTitle by remember { mutableStateOf("") }
    var tutCategory by remember { mutableStateOf("basic") }
    var tutContent by remember { mutableStateOf("") }
    var tutVideoUrl by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            if (reset) page = 1
            val p = if (reset) 1 else page + 1
            if (tab == LibTab.TUTORIAL) {
                val r = safeApi { ApiClient.get().tutorials(null, null, query.ifBlank { null }, p, 20) }
                if (r.ok && reset) tuts = r.data?.list ?: emptyList()
                if (r.ok) total = r.data?.total ?: 0
            } else {
                val (src, quality, mine) = when (tab) {
                    LibTab.PLATFORM -> Triple("all", null, null)
                    LibTab.HIGH -> Triple("all", "high", null)
                    LibTab.LOW -> Triple("all", "low", null)
                    LibTab.MINE -> Triple(null, null, "1")
                    else -> Triple(null, null, null)
                }
                if (tab == LibTab.FAV) {
                    val r = safeApi { ApiClient.get().favorites() }
                    if (r.ok) {
                        list = r.data ?: emptyList()
                        favIds = list.map { it.id }.toSet()
                        total = list.size
                    }
                } else {
                    val r = safeApi {
                        ApiClient.get().commands(null, src, quality, query.ifBlank { null }, mine, p, 20)
                    }
                    if (r.ok) {
                        val got = r.data?.list ?: emptyList()
                        list = if (reset) got else (list + got).distinctBy { it.id }
                        total = r.data?.total ?: 0
                        page = p
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    // TXT 导入 (意见 id=21: 支持合并为一条指令, 避免一条一条拆开)
    var pendingImport by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull() ?: ""
                val parsed = parseCommandTxt(text)
                if (parsed.isEmpty()) {
                    msg = "TXT 中没找到指令(每行一条 / 开头, # 为标题注释)"
                    return@launch
                }
                if (AppState.token == null) { msg = "请先登录"; return@launch }
                pendingImport = parsed
            }
        }
    }

    suspend fun doImport(parsed: List<Pair<String, String>>, merge: Boolean) {
        pendingImport = null
        var imported = 0
        if (merge) {
            // 合并为一条: 所有命令按行拼成一个多行命令块
            val title = parsed.firstOrNull { it.first.isNotBlank() }?.first ?: "TXT导入"
            val cmd = parsed.joinToString("\n") { it.second }
            val r = safeApi {
                ApiClient.get().importCommands(ImportBody(listOf(ImportCmdItem(title, cmd, "TXT导入"))))
            }
            if (r.ok) imported = r.data?.imported ?: 0 else { msg = r.error ?: "导入失败"; return }
            msg = "已合并为 1 条指令（${parsed.size} 行命令）"
        } else {
            parsed.chunked(100).forEach { chunk ->
                val body = ImportBody(chunk.map { (t, c) -> ImportCmdItem(t, c, "TXT导入") })
                val r = safeApi { ApiClient.get().importCommands(body) }
                if (r.ok) imported += r.data?.imported ?: 0 else { msg = r.error ?: "导入失败"; return }
            }
            msg = "导入成功: $imported 条指令"
        }
        load(reset = true)
    }

    LaunchedEffect(tab, query) {
        if (tab == LibTab.FAV && AppState.token == null) return@LaunchedEffect
        load(reset = true)
    }

    // 滚动到底加载更多
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= list.size - 3 && list.size < total && !loading && tab != LibTab.TUTORIAL) {
                    load(reset = false)
                }
            }
    }

    // 上传教程对话框
    if (showTutDialog) {
        AlertDialog(
            onDismissRequest = { showTutDialog = false },
            title = { Text("上传教程") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tutType == "post", onClick = { tutType = "post" }, label = { Text("帖子") })
                        FilterChip(selected = tutType == "video", onClick = { tutType = "video" }, label = { Text("视频") })
                    }
                    OutlinedTextField(value = tutTitle, onValueChange = { tutTitle = it },
                        label = { Text("标题") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("basic" to "入门", "execute" to "execute", "scoreboard" to "计分板", "redstone" to "红石").forEach { (k, v) ->
                            FilterChip(selected = tutCategory == k, onClick = { tutCategory = k }, label = { Text(v) })
                        }
                    }
                    if (tutType == "post") {
                        OutlinedTextField(value = tutContent, onValueChange = { tutContent = it },
                            label = { Text("内容") }, minLines = 4)
                    } else {
                        OutlinedTextField(value = tutVideoUrl, onValueChange = { tutVideoUrl = it },
                            label = { Text("视频链接(如B站地址)") }, singleLine = true)
                    }
                    if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        msg = ""
                        if (AppState.token == null) { msg = "请先登录"; return@launch }
                        val r = safeApi {
                            ApiClient.get().uploadTutorial(mapOf(
                                "title" to tutTitle, "content" to tutContent,
                                "category" to tutCategory, "type" to tutType, "video_url" to tutVideoUrl))
                        }
                        if (r.ok) {
                            showTutDialog = false
                            tutTitle = ""; tutContent = ""; tutVideoUrl = ""
                            msg = "教程已发布"
                            load(reset = true)
                        } else msg = r.error ?: "发布失败"
                    }
                }) { Text("发布") }
            },
            dismissButton = { TextButton(onClick = { showTutDialog = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "指令库",
                icon = Icons.Default.Book,
                onBack = { if (tab != LibTab.PLATFORM) tab = LibTab.PLATFORM },
                actions = {
                    if (tab != LibTab.TUTORIAL) {
                        IconButton(onClick = {
                            if (AppState.token == null) nav.navigate(Routes.LOGIN)
                            else txtLauncher.launch(arrayOf("text/plain"))
                        }) { Icon(Icons.Default.FileUpload, "导入TXT") }
                    } else {
                        IconButton(onClick = {
                            if (AppState.token == null) nav.navigate(Routes.LOGIN) else showTutDialog = true
                        }) { Icon(Icons.Default.Add, "上传教程") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (AppState.token == null) nav.navigate(Routes.LOGIN) else nav.navigate(Routes.editor())
            }) {
                Icon(Icons.Default.Add, "上传指令")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (msg.isNotBlank()) {
                Text(msg, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            // 搜索
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索指令…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // 标签
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background) {
                LibTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t },
                        text = { Text(t.label, style = MaterialTheme.typography.labelMedium) })
                }
            }

            when {
                tab == LibTab.TUTORIAL -> {
                    if (tuts.isEmpty() && !loading) EmptyState("暂无教程\n点右上角 + 上传教程")
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(tuts, key = { it.id }) { t ->
                            Card(onClick = { nav.navigate(Routes.tutorial(t.id)) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${t.categoryLabel()} · ${if (t.source == "platform") "平台教程" else "@${t.author ?: "用户"}"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (t.type == "video") {
                                        Icon(Icons.Default.PlayCircle, "视频教程",
                                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("帖子", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                tab == LibTab.FAV && AppState.token == null -> {
                    EmptyState("登录后查看收藏")
                    Button(onClick = { nav.navigate(Routes.LOGIN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("去登录")
                    }
                }
                list.isEmpty() && loading -> LoadingBox()
                list.isEmpty() -> EmptyState("没有找到指令\n可点右上角导入 TXT")
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.id }) { cmd ->
                            CommandCard(
                                cmd = cmd,
                                onClick = { nav.navigate(Routes.commandDetail(cmd.id)) },
                                favorited = cmd.id in favIds,
                                onAuthorClick = { if (cmd.userId != null) nav.navigate(Routes.user(cmd.userId)) },
                                onCopy = {
                                    com.canglan.cyancodex.ui.components.copyToClipboard(ctx, cmd.command)
                                },
                                onFavorite = {
                                    scope.launch {
                                        val r = safeApi { ApiClient.get().toggleFavorite(cmd.id) }
                                        if (r.ok) {
                                            val now = r.data?.favorited ?: false
                                            favIds = if (now) favIds + cmd.id else favIds - cmd.id
                                        }
                                    }
                                },
                            )
                        }
                        item {
                            if (loading) LoadingBox()
                            else if (list.size < total) Text("上拉加载更多…", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        }
                    }
                }
            }
        }
    }

    // 导入方式选择 (分开 / 合并为一条)
    pendingImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("选择导入方式") },
            text = { Text("识别到 ${parsed.size} 条指令。\n「合并为一条」会把所有命令按行拼成一个多行命令块（适合单条多命令的指令文件）。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scope.launch { doImport(parsed, merge = true) } }) {
                        Text("合并为一条", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { scope.launch { doImport(parsed, merge = false) } }) {
                        Text("分开导入", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } },
        )
    }
}

fun Tutorial.categoryLabel(): String = when (category) {
    "basic" -> "入门"; "execute" -> "execute"; "scoreboard" -> "计分板"; "redstone" -> "红石"; else -> category
}
