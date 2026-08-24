package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.ChatMessage
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.AvatarIcon
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 聊天室 (意见 #36): 公共频道, 3 秒限流, 5 秒轮询新消息 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    suspend fun loadInitial() {
        val r = safeApi { ApiClient.get().chatMessages(50) }
        if (r.ok) {
            messages = r.data ?: emptyList()
            err = ""
        } else if (messages.isEmpty()) {
            err = r.error ?: "加载失败"
        }
    }

    // 轮询新消息 (5 秒)
    LaunchedEffect(Unit) {
        loadInitial()
        while (true) {
            delay(5000)
            val lastId = messages.lastOrNull()?.id ?: 0
            val r = safeApi { ApiClient.get().chatAfter(lastId) }
            if (r.ok) {
                val fresh = r.data ?: emptyList()
                if (fresh.isNotEmpty()) {
                    messages = messages + fresh
                    // 若接近底部则跟随滚动
                    val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?.let { it >= (messages.size - fresh.size - 2) } ?: true
                    if (nearBottom) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "聊天室", icon = Icons.Default.Forum, onBack = { nav.popBackStack() }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text("和大家聊点 MC 指令…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            input = ""
                            scope.launch {
                                sending = true
                                val r = safeApi { ApiClient.get().sendChat(mapOf("content" to text)) }
                                sending = false
                                if (r.ok) {
                                    r.data?.let { messages = messages + it }
                                    listState.animateScrollToItem(messages.size - 1)
                                } else {
                                    err = r.error ?: "发送失败"
                                }
                            }
                        },
                    ) { Icon(Icons.Default.Send, "发送") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (err.isNotBlank() && messages.isEmpty()) {
                Text(err, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp))
            }
            if (messages.isEmpty() && err.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有消息，来发第一条吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    ChatBubble(m)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessage) {
    val mine = m.userId != null && m.userId == AppState.user?.id
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            AvatarIcon(m.avatar ?: "", 30.dp)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            if (!mine) {
                Text(
                    m.nickname ?: "用户",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.role == "admin") Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (m.role == "admin") FontWeight.Bold else FontWeight.Normal,
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (mine) 12.dp else 2.dp,
                    bottomEnd = if (mine) 2.dp else 12.dp,
                ),
                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    m.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                m.createdAt?.take(16)?.replace("T", " ") ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
