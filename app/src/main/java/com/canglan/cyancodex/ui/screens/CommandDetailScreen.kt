package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Command
import com.canglan.cyancodex.data.Comment
import com.canglan.cyancodex.data.QuickCommand
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.AvatarIcon
import com.canglan.cyancodex.ui.components.BedrockHighlight
import com.canglan.cyancodex.ui.components.CommandCard
import com.canglan.cyancodex.ui.components.LoadingBox
import com.canglan.cyancodex.ui.components.RemoteImage
import com.canglan.cyancodex.ui.components.categoryLabel
import com.canglan.cyancodex.ui.components.copyToClipboard
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandDetailScreen(nav: NavHostController, id: Long) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cmd by remember { mutableStateOf<Command?>(null) }
    var fav by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf("") }
    val user = AppState.user

    fun loadComments() {
        scope.launch {
            val r = safeApi { ApiClient.get().comments(id) }
            if (r.ok) comments = r.data ?: emptyList()
        }
    }

    fun sendComment() {
        val t = commentText.trim()
        if (t.isBlank()) return
        scope.launch {
            sending = true
            commentError = ""
            val r = safeApi { ApiClient.get().addComment(id, mapOf("content" to t)) }
            sending = false
            if (r.ok) {
                commentText = ""
                loadComments()
                cmd?.let { c -> cmd = c.copy(commentCount = c.commentCount + 1) }
            } else {
                commentError = r.error ?: "发表失败"
            }
        }
    }

    fun deleteComment(cid: Long) {
        scope.launch {
            val r = safeApi { ApiClient.get().deleteComment(cid) }
            if (r.ok) {
                comments = comments.filter { it.id != cid }
                cmd?.let { c -> cmd = c.copy(commentCount = (c.commentCount - 1).coerceAtLeast(0)) }
            }
        }
    }

    LaunchedEffect(id) {
        val r = safeApi { ApiClient.get().commandDetail(id) }
        if (r.ok) cmd = r.data
        loadComments()
    }

    if (deleted) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(title = cmd?.title ?: "指令详情", icon = Icons.Default.Description, onBack = { nav.popBackStack() })
        },
    ) { pad ->
        val c = cmd
        if (c == null) {
            LoadingBox(Modifier.padding(pad))
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 元信息
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(c.categoryLabel()) })
                    Spacer(Modifier.width(8.dp))
                    if (c.source == "platform") {
                        AssistChip(onClick = {}, label = { Text(if (c.quality == "high") "平台·高质" else "平台·低质") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (c.quality == "high") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant))
                    } else {
                        AssistChip(onClick = { if (c.userId != null) nav.navigate(Routes.user(c.userId)) },
                            label = { Text("@${c.author ?: "用户"}") })
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${c.likeCount}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${c.viewCount}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (c.description.isNotBlank()) {
                    Text(c.description, style = MaterialTheme.typography.bodyMedium)
                }

                // 指令效果图
                if (!c.image.isNullOrBlank()) {
                    RemoteImage(c.image, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), corner = 12.dp)
                }

                // 指令内容: 语法高亮 + 按命令块分段, 每段可单独复制 (参考 CHelper/BNM)
                val lines = c.command.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F0C))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lines.forEachIndexed { idx, ln ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4ADE80), modifier = Modifier.width(22.dp))
                                Text(
                                    BedrockHighlight.highlight(ln, dark = true),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE7F2EA),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { copyToClipboard(ctx, ln, "已复制第 ${idx + 1} 段") },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, "复制第${idx + 1}段",
                                        tint = Color(0xFF9DB4A5), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // 操作行
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { copyToClipboard(ctx, c.command) },
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (lines.size > 1) "复制全部(${lines.size}段)" else "复制指令")
                    }
                    OutlinedButton(onClick = {
                        if (AppState.token == null) nav.navigate(Routes.LOGIN)
                        else scope.launch {
                            val r = safeApi { ApiClient.get().toggleFavorite(c.id) }
                            if (r.ok) fav = r.data?.favorited ?: !fav
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (fav) "已收藏" else "收藏")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        Session.addQuickCommand(QuickCommand(c.title, c.command))
                        Session.addRecent(c.command)
                        android.widget.Toast.makeText(ctx, "已加入快捷指令(输入法/悬浮窗可用)", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("加入快捷")
                    }
                    OutlinedButton(onClick = {
                        if (AppState.token == null) { nav.navigate(Routes.LOGIN); return@OutlinedButton }
                        scope.launch {
                            val r = safeApi { ApiClient.get().likeCommand(c.id) }
                            if (r.ok && r.data != null) {
                                cmd = c.copy(likeCount = r.data.likeCount, liked = r.data.liked)
                            } else {
                                // 失败时重新拉取详情同步状态
                                val rr = safeApi { ApiClient.get().commandDetail(c.id) }
                                if (rr.ok) cmd = rr.data
                            }
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(if (c.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                            Modifier.size(16.dp),
                            tint = if (c.liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(if (c.liked) "已赞" else "点赞")
                    }
                }

                // 评论区
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("评论 (${comments.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { loadComments() }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, "刷新评论", modifier = Modifier.size(16.dp))
                    }
                }
                if (commentError.isNotBlank()) {
                    Text(commentError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (comments.isEmpty()) {
                    Text("暂无评论，来抢沙发~", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                }
                comments.forEach { cm ->
                    CommentRow(
                        cm = cm,
                        mine = user != null && cm.userId == user.id,
                        onDelete = { deleteComment(cm.id) },
                        onAuthor = { nav.navigate(Routes.user(cm.userId)) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText, onValueChange = { commentText = it },
                        placeholder = { Text(if (AppState.token == null) "登录后可评论" else "说点什么…") },
                        enabled = AppState.token != null,
                        modifier = Modifier.weight(1f), maxLines = 2,
                        shape = RoundedCornerShape(10.dp),
                    )
                    IconButton(
                        onClick = {
                            if (AppState.token == null) nav.navigate(Routes.LOGIN)
                            else sendComment()
                        },
                        enabled = !sending && (AppState.token != null) && commentText.isNotBlank(),
                    ) {
                        if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, "发表评论")
                    }
                }

                // 本人上传: 编辑/删除
                if (c.source == "user" && user != null && c.userId == user.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { nav.navigate(Routes.editor(c.command, c.id)) },
                            modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("编辑")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val r = safeApi { ApiClient.get().deleteCommand(c.id) }
                                if (r.ok) deleted = true
                            }
                        }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CommentRow(
    cm: Comment,
    mine: Boolean,
    onDelete: () -> Unit,
    onAuthor: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        AvatarIcon(cm.avatar, 34.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cm.nickname.ifBlank { cm.username },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAuthor),
                )
                // 管理员徽章 (意见 #43)
                if (cm.userRole == "admin") {
                    Spacer(Modifier.width(5.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = Color(0xFFFBBF24).copy(alpha = 0.18f)) {
                        Text("管理员", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(cm.createdAt ?: "", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(cm.content, style = MaterialTheme.typography.bodyMedium)
        }
        if (mine) {
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Delete, "删除评论",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
