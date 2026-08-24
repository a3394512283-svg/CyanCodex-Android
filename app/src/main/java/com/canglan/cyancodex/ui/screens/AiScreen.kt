package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.ai.AiClient
import com.canglan.cyancodex.data.ChatMsg
import com.canglan.cyancodex.data.UiMsg
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.OutlinedTextField2
import com.canglan.cyancodex.ui.components.copyToClipboard
import com.canglan.cyancodex.ui.components.errText
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    // 会话内持久: 从本地恢复上次对话 (意见 id=12: 切页后内容消失)
    val gson = com.google.gson.Gson()
    val msgType = object : com.google.gson.reflect.TypeToken<List<UiMsg>>() {}.type
    fun loadMessages(): List<UiMsg> = try {
        gson.fromJson(Session.aiMessages, msgType) ?: emptyList()
    } catch (e: Exception) { emptyList() }
    var messages by remember { mutableStateOf(loadMessages()) }
    fun updateMessages(new: List<UiMsg>) {
        messages = new
        Session.aiMessages = gson.toJson(new)
    }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(true) }
    var points by remember { mutableStateOf(0) }
    var pointsNeeded by remember { mutableStateOf(200) }
    var statusMsg by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }
    var apiBase by remember { mutableStateOf(Session.aiApiBase) }
    var apiKey by remember { mutableStateOf(Session.aiApiKey) }
    var model by remember { mutableStateOf(Session.aiModel) }

    suspend fun refreshStatus() {
        if (AppState.token == null) return
        val r = safeApi { ApiClient.get().aiStatus() }
        if (r.ok) {
            unlocked = r.data?.unlocked ?: true
            points = r.data?.points ?: 0
            pointsNeeded = r.data?.pointsNeeded ?: 200
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    val hasKey = Session.aiApiKey.isNotBlank()

    Scaffold(
        topBar = {
            AppTopBar(title = "AI Agent", icon = Icons.Default.AutoAwesome, actions = {
                // 新对话: 清空当前会话 (意见 id=16)
                IconButton(onClick = {
                    updateMessages(emptyList())
                    android.widget.Toast.makeText(ctx, "已开启新对话", android.widget.Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.DeleteSweep, "新对话") }
                IconButton(onClick = { if (AppState.token == null) nav.navigate(Routes.LOGIN) else nav.navigate(Routes.AI_HISTORY) }) {
                    Icon(Icons.Default.History, "AI 对话历史")
                }
                IconButton(onClick = { showConfig = !showConfig }) { Icon(Icons.Default.Settings, "AI 设置") }
            })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // 状态栏
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ok = hasKey || (unlocked && points >= 5)
                    val (statusIcon, statusText) = when {
                        hasKey -> Icons.Default.CheckCircle to "已配置自备 API (${Session.aiModel})"
                        unlocked -> Icons.Default.CheckCircle to "平台 AI · 基础5积分+按Tokens计费(余额 $points 分)"
                        AppState.token != null -> Icons.Default.Lock to "AI 未开放 (当前 $points 分)"
                        else -> Icons.Default.Lock to "登录后可用 AI"
                    }
                    Icon(statusIcon, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (statusMsg.isNotBlank()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (showConfig) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI 接口配置 (OpenAI 兼容, 支持 DeepSeek/通义/Kimi 等)", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField2(apiBase, { apiBase = it }, "API 地址", placeholder = "https://api.openai.com/v1/")
                        OutlinedTextField2(apiKey, { apiKey = it }, "API Key")
                        OutlinedTextField2(model, { model = it }, "模型", placeholder = "gpt-4o-mini / deepseek-chat")
                        Button(onClick = {
                            Session.aiApiBase = apiBase
                            Session.aiApiKey = apiKey.trim()
                            Session.aiModel = model.trim().ifBlank { "gpt-4o-mini" }
                            showConfig = false
                            statusMsg = "已保存 AI 配置"
                        }, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
                        Text("说明: 使用你自己提供的 API Key, 可直接调用, 无需积分。", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (AppState.token == null && !hasKey) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("登录后可用积分解锁 AI, 或直接配置自备 API", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { nav.navigate(Routes.LOGIN) }) { Text("去登录") }
                    }
                }
            } else {
                // 聊天列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text("我是 CyanCodex AI 助手, 精通我的世界指令。\n\n你可以问我:\n· 生成一个无限TNT大炮的指令\n· 怎么做自动刷怪塔(命令方块方案)\n· 帮我写一个传送门切换系统\n· 解释 execute 指令\n\n(需配置自备 API 或用积分解锁)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    items(messages) { m ->
                        val mine = m.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface),
                            ) {
                                Row(Modifier.padding(12.dp).widthIn(max = 280.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!mine && m.err) {
                                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    // 长按可选择复制; AI 回复带复制按钮 (意见反馈: 长按复制不了)
                                    SelectionContainer {
                                        Text(m.content, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (!mine && !m.err) {
                                        IconButton(onClick = { copyToClipboard(ctx, m.content, "已复制 AI 回复") },
                                            modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.ContentCopy, "复制",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (busy) {
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("思考中…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } }
                    }
                }

                // 输入行
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text(if (hasKey || unlocked) "输入你的 MC 指令需求…" else "登录后可用 AI") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(
                        enabled = !busy && input.isNotBlank() && (hasKey || unlocked),
                        onClick = {
                            val q = input.trim()
                            input = ""
                            updateMessages(messages + UiMsg("user", q))
                            scope.launch {
                                busy = true
                                try {
                                    // 所有对话走服务器代理(自定义 Key 或平台 AI), 服务器统一记录历史+计费
                                    val history = messages.map { ChatMsg(it.role, it.content) }
                                    val json = gson.toJson(history)
                                    val r = if (hasKey) {
                                        safeApi {
                                            ApiClient.get().aiProxy(mapOf(
                                                "apiBase" to Session.aiApiBase,
                                                "apiKey" to Session.aiApiKey,
                                                "model" to Session.aiModel.ifBlank { "gpt-4o-mini" },
                                                "messages" to json))
                                        }
                                    } else {
                                        safeApi { ApiClient.get().aiProxy(mapOf("messages" to json)) }
                                    }
                                    if (r.ok && r.data?.content != null) {
                                        // 平台 AI: 显示本次扣费与剩余积分
                                        val cost = r.data.cost
                                        val bal = r.data.balance
                                        val reply = if (cost > 0) {
                                            "${r.data.content}\n\n— 本次消耗 ${cost} 积分(基础5+按Tokens)，剩余 ${bal} 积分"
                                        } else r.data.content
                                        if (bal != null) points = bal
                                        updateMessages(messages + UiMsg("assistant", reply))
                                    } else {
                                        updateMessages(messages + UiMsg("assistant", r.error ?: "AI 请求失败", err = true))
                                    }
                                } catch (e: Exception) {
                                    updateMessages(messages + UiMsg("assistant", errText(e), err = true))
                                } finally {
                                    busy = false
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        },
                    ) { Icon(Icons.Default.Send, "发送") }
                }
            }
        }
    }
}
