package com.canglan.cyancodex.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Plugin
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.EmptyState
import com.canglan.cyancodex.ui.components.LoadingBox
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 插件库 (意见 #37): 浏览/上传/下载插件 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Plugin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("active") }
    var showUpload by remember { mutableStateOf(false) }
    var upTitle by remember { mutableStateOf("") }
    var upDesc by remember { mutableStateOf("") }
    var upVersion by remember { mutableStateOf("1.0.0") }
    var upFile by remember { mutableStateOf<Uri?>(null) }
    var upFileName by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        val r = safeApi { ApiClient.get().plugins(if (tab == "active") "active" else null, 1, 100) }
        loading = false
        if (r.ok) { list = r.data?.list ?: emptyList(); err = "" }
        else err = r.error ?: "加载失败"
    }
    LaunchedEffect(tab) { load() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            upFile = uri
            upFileName = (ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i) else "plugin.jar"
            }) ?: "plugin.jar"
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "插件库", icon = Icons.Default.Extension, onBack = { nav.popBackStack() },
                actions = {
                    TextButton(onClick = {
                        if (AppState.token == null) nav.navigate(Routes.LOGIN) else showUpload = true
                    }) { Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp)) }
                })
        },
        bottomBar = {
            NavigationBar {
                listOf("active" to "插件", "mine" to "我的").forEach { (k, v) ->
                    NavigationBarItem(
                        selected = tab == k,
                        onClick = { tab = k },
                        icon = { Text(v, style = MaterialTheme.typography.labelSmall) },
                        label = {},
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                loading -> LoadingBox()
                list.isEmpty() -> EmptyState(
                    if (err.isNotBlank()) err else "还没有插件\n上传你的 .jar 插件分享给大家（需审核）")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { pl ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pl.title, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (pl.status == "pending") {
                                        Surface(shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)) {
                                            Text("待审核", style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    } else if (pl.status == "rejected") {
                                        Surface(shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                                            Text("已驳回", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                if (pl.description.isNotBlank()) {
                                    Text(pl.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                Text("v${pl.version} · ${pl.author ?: "?"} · 下载 ${pl.downloads} · ${pl.createdAt?.take(10) ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                pl.reviewNote?.takeIf { it.isNotBlank() }?.let {
                                    Text("驳回理由：$it", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                if (pl.status == "active") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val r = safeApi { ApiClient.get().pluginDetail(pl.id) }
                                            // 下载: 直接打开浏览器下载
                                            if (r.ok) {
                                                val base = com.canglan.cyancodex.data.Session.baseUrl.trimEnd('/')
                                                com.canglan.cyancodex.ui.components.copyToClipboard(ctx,
                                                    "$base/api/plugins/${pl.id}/download", "下载链接已复制，粘贴到浏览器下载")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("下载 JAR")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 上传对话框
    if (showUpload) {
        AlertDialog(
            onDismissRequest = { showUpload = false },
            title = { Text("上传插件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("上传 .jar 插件（通过安全扫描后需审核上架）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = upTitle, onValueChange = { upTitle = it },
                        label = { Text("插件名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = upDesc, onValueChange = { upDesc = it },
                        label = { Text("描述(可选)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = upVersion, onValueChange = { upVersion = it },
                        label = { Text("版本") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("application/java-archive", "application/zip", "*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (upFileName.isNotBlank()) "已选择: $upFileName" else "选择 .jar 文件")
                    }
                    if (upFileName.endsWith(".jar", ignoreCase = true).not() && upFileName.isNotBlank()) {
                        Text("请选择 .jar 文件", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = upTitle.isNotBlank() && upFile != null &&
                        upFileName.endsWith(".jar", ignoreCase = true) && !uploading,
                    onClick = {
                        scope.launch {
                            uploading = true
                            val bytes = upFile?.let { uri ->
                                withContext(Dispatchers.IO) {
                                    runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                                }
                            }
                            if (bytes == null) { err = "文件读取失败"; uploading = false; return@launch }
                            val r = safeApi {
                                ApiClient.get().uploadPlugin(mapOf(
                                    "title" to upTitle, "description" to upDesc,
                                    "version" to upVersion.ifBlank { "1.0.0" },
                                    "data" to android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)))
                            }
                            uploading = false
                            if (r.ok) {
                                showUpload = false
                                upTitle = ""; upDesc = ""; upVersion = "1.0.0"; upFileName = ""; upFile = null
                                android.widget.Toast.makeText(ctx, "上传成功，等待审核", android.widget.Toast.LENGTH_SHORT).show()
                                load()
                            } else {
                                err = r.error ?: "上传失败"
                                android.widget.Toast.makeText(ctx, r.error ?: "上传失败", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text(if (uploading) "上传中…" else "上传") }
            },
            dismissButton = { TextButton(onClick = { showUpload = false }) { Text("取消") } },
        )
    }
}
