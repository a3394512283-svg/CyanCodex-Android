package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Conversation
import com.canglan.cyancodex.data.DmMessage
import com.canglan.cyancodex.data.FriendItem
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.AvatarIcon
import com.canglan.cyancodex.ui.components.EmptyState
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FriendsTab(val label: String) { FRIENDS("好友"), REQUESTS("申请"), CHATS("私聊") }

/** 好友/私聊中心 (意见 #38) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nav: NavHostController) {
    var tab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    Scaffold(
        topBar = {
            AppTopBar(title = "好友", icon = Icons.Default.Person, onBack = { nav.popBackStack() })
        },
        bottomBar = {
            NavigationBar {
                FriendsTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        label = {},
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                FriendsTab.FRIENDS -> FriendsListScreen(nav)
                FriendsTab.REQUESTS -> RequestsScreen()
                FriendsTab.CHATS -> ChatsListScreen(nav)
            }
        }
    }
}

// ============ 好友列表 ============
@Composable
private fun FriendsListScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    suspend fun load() {
        val r = safeApi { ApiClient.get().friends("accepted") }
        if (r.ok) friends = r.data ?: emptyList() else msg = r.error ?: "加载失败"
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (friends.isEmpty() && msg.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有好友\n在用户主页点「+ 关注/加好友」发送申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(friends, key = { it.peerId ?: it.id }) { f ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarIcon(f.avatar ?: "", 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.nickname ?: "用户", style = MaterialTheme.typography.titleSmall)
                            Text("@${f.username ?: "?"}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { nav.navigate(Routes.user(f.peerId ?: 0)) }) { Text("主页") }
                        TextButton(onClick = {
                            nav.navigate(Routes.dm(f.peerId ?: 0, f.nickname ?: ""))
                        }) { Text("私聊") }
                    }
                }
            }
        }
    }
}

// ============ 好友申请 ============
@Composable
private fun RequestsScreen() {
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    suspend fun load() {
        val r = safeApi { ApiClient.get().friends("pending") }
        if (r.ok) requests = r.data ?: emptyList()
    }
    LaunchedEffect(Unit) { load() }

    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无好友申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(requests, key = { it.id }) { f ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarIcon(f.avatar ?: "", 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.nickname ?: "用户", style = MaterialTheme.typography.titleSmall)
                        Text("@${f.username ?: "?"} 请求添加你为好友", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        scope.launch {
                            safeApi { ApiClient.get().friendHandle(mapOf("friend_id" to (f.userId ?: 0).toString(), "accept" to "true")) }
                            load()
                        }
                    }) { Text("接受") }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        scope.launch {
                            safeApi { ApiClient.get().friendHandle(mapOf("friend_id" to (f.userId ?: 0).toString(), "accept" to "false")) }
                            load()
                        }
                    }) { Text("拒绝", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ============ 会话列表 ============
@Composable
private fun ChatsListScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var convs by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    suspend fun load() {
        val r = safeApi { ApiClient.get().dmConversations() }
        if (r.ok) convs = r.data ?: emptyList()
    }
    LaunchedEffect(Unit) { load() }

    if (convs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无私聊\n和好友点「私聊」开始对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(convs, key = { it.id }) { c ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable {
                    nav.navigate(Routes.dm(c.peerId ?: 0, c.nickname ?: ""))
                }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarIcon(c.avatar ?: "", 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.nickname ?: "用户", style = MaterialTheme.typography.titleSmall)
                        Text(c.content, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(c.createdAt?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============ 私聊窗口 ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmScreen(nav: NavHostController, peerId: Long, peerName: String) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    suspend fun loadInitial() {
        val r = safeApi { ApiClient.get().dmMessages(peerId, 0) }
        if (r.ok) messages = r.data ?: emptyList() else err = r.error ?: "加载失败"
    }

    LaunchedEffect(Unit) {
        loadInitial()
        listState.scrollToItem(messages.size - 1)
        // 轮询新消息
        while (true) {
            delay(4000)
            val lastId = messages.lastOrNull()?.id ?: 0
            val r = safeApi { ApiClient.get().dmAfter(peerId, lastId) }
            if (r.ok) {
                val fresh = r.data ?: emptyList()
                if (fresh.isNotEmpty()) {
                    messages = messages + fresh
                    listState.scrollToItem(messages.size - 1)
                }
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = peerName, onBack = { nav.popBackStack() }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text("发送私聊…") },
                        modifier = Modifier.weight(1f), maxLines = 3,
                    )
                    IconButton(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            input = ""
                            scope.launch {
                                sending = true
                                val r = safeApi { ApiClient.get().sendDm(mapOf("peer_id" to peerId.toString(), "content" to text)) }
                                sending = false
                                if (r.ok) {
                                    messages = messages + DmMessage(id = r.data?.id ?: 0, fromId = AppState.user?.id, toId = peerId, content = text)
                                    listState.scrollToItem(messages.size - 1)
                                } else err = r.error ?: "发送失败"
                            }
                        },
                    ) { Icon(Icons.Default.Send, "发送") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (err.isNotBlank() && messages.isEmpty()) {
                Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    val mine = m.fromId != null && m.fromId == AppState.user?.id
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ) {
                            Text(m.content, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
