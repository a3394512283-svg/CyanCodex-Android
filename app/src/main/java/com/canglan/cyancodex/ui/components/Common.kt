package com.canglan.cyancodex.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.canglan.cyancodex.BuildConfig
import com.canglan.cyancodex.data.ApiResp
import com.canglan.cyancodex.data.Command
import com.canglan.cyancodex.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 远程图片加载 (指令效果图等): 内存缓存 + 原始 OkHttp (无 Coil 依赖) */
private val remoteImageCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}
private val remoteImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val fullUrl = if (url.isNullOrBlank()) null
        else if (url.startsWith("/")) Session.baseUrl.trimEnd('/') + url else url
    var bmp by remember(fullUrl) { mutableStateOf(remoteImageCache.get(fullUrl)) }
    LaunchedEffect(fullUrl) {
        if (fullUrl == null) return@LaunchedEffect
        if (bmp == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val req = okhttp3.Request.Builder().url(fullUrl).build()
                    remoteImageClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                } catch (e: Exception) { null }
            }
            if (loaded != null) { remoteImageCache.put(fullUrl, loaded); bmp = loaded }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            b.asImageBitmap(), null,
            modifier = modifier.clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 头像素材库 (Material 图标 + 颜色, 替代 emoji) */
val avatarMap: Map<String, Pair<ImageVector, Color>> = mapOf(
    "person" to (Icons.Default.AccountCircle to Color(0xFF4ADE80)),
    "face" to (Icons.Default.Face to Color(0xFF38BDF8)),
    "smile" to (Icons.Default.EmojiEmotions to Color(0xFFFBBF24)),
    "pets" to (Icons.Default.Pets to Color(0xFFA78BFA)),
    "rocket" to (Icons.Default.Rocket to Color(0xFFF472B6)),
    "star" to (Icons.Default.Star to Color(0xFFF59E0B)),
    "bolt" to (Icons.Default.Bolt to Color(0xFFFBBF24)),
    "fire" to (Icons.Default.LocalFireDepartment to Color(0xFFF87171)),
    "moon" to (Icons.Default.Nightlight to Color(0xFF818CF8)),
    "game" to (Icons.Default.SportsEsports to Color(0xFF34D399)),
    "launch" to (Icons.Default.RocketLaunch to Color(0xFF60A5FA)),
    "diamond" to (Icons.Default.Diamond to Color(0xFF2DD4BF)),
    "heart" to (Icons.Default.Favorite to Color(0xFFF472B6)),
    "thumbs" to (Icons.Default.ThumbUp to Color(0xFF4ADE80)),
    "cake" to (Icons.Default.Cake to Color(0xFFFB923C)),
    "bug" to (Icons.Default.BugReport to Color(0xFFA3E635)),
)

/** 头像内存缓存 (10MB) */
private val avatarCache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** 加载网络图片的小客户端 */
private val imageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * 服务器图片头像: code 以 "/" 开头表示服务器路径(/uploads/...), 否则按内置图标库渲染。
 */
@Composable
fun AvatarIcon(code: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val url = if (code.startsWith("/")) Session.baseUrl.trimEnd('/') + code else null
    if (url != null) {
        ServerImage(url, size)
    } else {
        val (icon, color) = avatarMap[code] ?: (Icons.Default.AccountCircle to Color(0xFF4ADE80))
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.62f))
        }
    }
}

/** 网络图片加载 (圆形容器, 带缓存), 失败时显示占位人像 */
@Composable
fun ServerImage(url: String, size: androidx.compose.ui.unit.Dp, shape: Shape = CircleShape) {
    var bmp by remember(url) { mutableStateOf(avatarCache.get(url)) }
    LaunchedEffect(url) {
        if (bmp == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val req = okhttp3.Request.Builder().url(url).build()
                    imageClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                } catch (e: Exception) { null }
            }
            if (loaded != null) {
                avatarCache.put(url, loaded)
                bmp = loaded
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f))
        }
    }
}

/**
 * 统一装饰顶栏: 顶部渐变底色 + 标题左侧图标 + 可选返回键。
 * 替代各界面裸的 CenterAlignedTopAppBar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    icon: ImageVector? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                    )
                )
            )
    ) {
        CenterAlignedTopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, maxLines = 1)
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )
    }
}

/** 版本号比较: 公告版本 > 当前安装版本(BuildConfig.VERSION_NAME) */
fun versionNewer(v: String?): Boolean {
    if (v.isNullOrBlank()) return false
    val cur = BuildConfig.VERSION_NAME.split('.').mapNotNull { it.toIntOrNull() }
    val nv = v.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(cur.size, nv.size)) {
        val c = cur.getOrElse(i) { 0 }
        val n = nv.getOrElse(i) { 0 }
        if (n != c) return n > c
    }
    return false
}

/** 版本号比较: 当前版本 < 最低版本 (用于强制更新判断) */
fun versionBelow(cur: String?, min: String?): Boolean {
    if (min.isNullOrBlank()) return false
    val cv = cur?.trim()?.removePrefix("v")?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
    val mv = min.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(cv.size, mv.size)) {
        val c = cv.getOrElse(i) { 0 }
        val m = mv.getOrElse(i) { 0 }
        if (c != m) return c < m
    }
    return false
}

/** 公告类型徽章 (首页/公告列表共用) */
@Composable
fun AnnBadge(type: String) {
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

/**
 * 计算从注册日期(yyyy-MM-dd[ HH:mm:ss])到今天的自然天数; 解析失败返回 -1。
 * 用于「已陪伴 CyanCodex N 天」。
 */
fun daysSince(dateStr: String): Int = try {
    val d = dateStr.trim().take(10).split("-")
    val reg = java.time.LocalDate.of(d[0].toInt(), d[1].toInt(), d[2].toInt())
    java.time.temporal.ChronoUnit.DAYS.between(reg, java.time.LocalDate.now()).toInt()
} catch (e: Exception) { -1 }

fun copyToClipboard(context: Context, text: String, hint: String = "已复制") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("command", text))
    Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
}

/**
 * 网络错误文案: 连接类错误给出中文提示; 其他错误去掉服务器地址/IP, 避免暴露后端地址
 */
fun errText(e: Exception?): String {
    var msg = e?.message?.trim().orEmpty()
    val lower = msg.lowercase()
    if (msg.isNotBlank() && (
            lower.contains("failed to connect") ||
            lower.contains("connectexception") ||
            lower.contains("sockettimeout") ||
            lower.contains("timeout") ||
            lower.contains("refused") ||
            lower.contains("unknownhost") ||
            lower.contains("unreachable") ||
            lower.contains("socketexception"))) {
        return "无法连接服务器，请检查网络后重试（生成器/编辑器等离线功能不受影响）"
    }
    if (msg.isBlank()) msg = "网络错误，请检查网络连接"
    // 兜底: 去掉 URL 与 IP(含端口), 统一显示为"服务器"
    msg = msg.replace(Regex("""https?://[^\s/),;：]+"""), "服务器")
    msg = msg.replace(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?"""), "服务器")
    msg = msg.trim()
    if (msg.isBlank()) msg = "无法连接服务器，请稍后重试"
    return msg
}

/** 简化网络调用包装 (协程取消必须继续传播, 不能当业务错误吞掉) */
suspend fun <T> safeApi(block: suspend () -> ApiResp<T>): ApiResp<T> =
    try { block() }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { ApiResp(ok = false, error = errText(e)) }

/**
 * 解析指令 TXT 文件:
 * - `#` 开头 = 注释/标题(可作下一条指令的标题)
 * - `/` 开头 = 一条指令
 * - 其余行忽略
 * 返回 (标题, 指令) 列表
 */
fun parseCommandTxt(content: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    var pendingTitle: String? = null
    content.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith("#")) {
            val t = line.removePrefix("#").trim()
            if (t.isNotBlank()) pendingTitle = t.take(128)
            return@forEach
        }
        if (line.startsWith("/")) {
            out.add((pendingTitle ?: line.take(20)) to line)
            pendingTitle = null
        }
    }
    return out
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun CopyButton(text: String, modifier: Modifier = Modifier, hint: String = "已复制") {
    val ctx = LocalContext.current
    Button(
        onClick = { copyToClipboard(ctx, text, hint) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("复制", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 解析指令属性 (意见 #27): tags 中编码的 __block=xx|__chain=xx|__redstone=xx|__delay=xx
 * 返回 (方块类型, 方块颜色, 附加属性列表[(文本, 颜色)])
 */
fun parseCommandProps(tags: String): Triple<String, Color, List<Pair<String, Color>>> {
    val props = tags.split(",").map { it.trim() }
        .filter { it.startsWith("__") }
        .associate { kv ->
            val idx = kv.indexOf('=')
            if (idx > 0) kv.substring(2, idx) to kv.substring(idx + 1) else "" to ""
        }
    val block = props["block"] ?: ""
    val blockColor = when (block) {
        "pulse" -> Color(0xFFFBBF24)   // 脉冲 黄
        "chain" -> Color(0xFF4ADE80)   // 连锁 绿
        "cycle" -> Color(0xFFC084FC)   // 循环 紫
        "chat" -> Color(0xFF94A3B8)    // 聊天栏 灰
        else -> Color(0xFF94A3B8)
    }
    val extras = mutableListOf<Pair<String, Color>>()
    when (props["chain"]) {
        "on" -> extras.add("连锁" to Color(0xFF4ADE80))
        "off" -> extras.add("无连锁" to Color(0xFF94A3B8))
    }
    when (props["redstone"]) {
        "keep" -> extras.add("保持开启" to Color(0xFF4ADE80))
        "need" -> extras.add("需要红石" to Color(0xFFF87171))
    }
    val delay = props["delay"]
    if (delay != null && delay != "0") extras.add("延迟${delay}刻" to Color(0xFF38BDF8))
    return Triple(block, blockColor, extras)
}

/** 指令属性徽章 (颜色标识, 意见 #27) */
@Composable
private fun PropBadge(text: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.18f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 指令卡片: 列表项通用样式 */
@Composable
fun CommandCard(
    cmd: Command,
    onClick: () -> Unit,
    favorited: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cmd.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (cmd.source == "user" && cmd.status == "pending") {
                    StatusBadge("审核中", Color(0xFFFBBF24))
                    Spacer(Modifier.width(6.dp))
                } else if (cmd.source == "user" && cmd.status == "rejected") {
                    StatusBadge("已驳回", Color(0xFFF87171))
                    Spacer(Modifier.width(6.dp))
                }
                if (cmd.source == "platform") {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (cmd.quality == "high") "平台·高质" else "平台·低质", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (cmd.quality == "high")
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    )
                }
            }
            // 驳回理由 (审核员驳回时填写, 展示给作者)
            if (cmd.source == "user" && cmd.status == "rejected" && !cmd.reviewNote.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)) {
                    Text("驳回理由：${cmd.reviewNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            if (cmd.description.isNotBlank()) {
                Text(cmd.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            // 命令方块属性彩色徽章 (意见 #27)
            val (blockKey, blockColor, propExtras) = parseCommandProps(cmd.tags)
            if (blockKey.isNotBlank() || propExtras.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (blockKey.isNotBlank()) {
                        PropBadge(
                            when (blockKey) {
                                "pulse" -> "脉冲"; "chain" -> "连锁"; "cycle" -> "循环"; "chat" -> "聊天栏"
                                else -> blockKey
                            }, blockColor)
                    }
                    propExtras.forEach { (t, c) -> PropBadge(t, c) }
                }
            }
            if (!cmd.image.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                RemoteImage(cmd.image, modifier = Modifier.fillMaxWidth().height(150.dp), corner = 8.dp)
            }
            Spacer(Modifier.height(6.dp))
            val darkPreview = BedrockHighlight.isDarkSurface(MaterialTheme.colorScheme.surface)
            Text(
                BedrockHighlight.highlight(cmd.command.lines().firstOrNull().orEmpty(), darkPreview),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val authorText = buildString {
                    append(cmd.categoryLabel())
                    if (cmd.mcVersion.isNotBlank()) append(" · ").append(cmd.mcVersion)
                }
                if (cmd.author?.isNotBlank() == true && cmd.source == "user") {
                    Text(
                        authorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "@${cmd.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = onAuthorClick != null) { onAuthorClick?.invoke() }
                            .padding(horizontal = 2.dp),
                    )
                    // 管理员徽章 (意见 #43)
                    if (cmd.authorRole == "admin") {
                        Spacer(Modifier.width(4.dp))
                        Surface(shape = MaterialTheme.shapes.small, color = Color(0xFFFBBF24).copy(alpha = 0.18f)) {
                            Text("管理员", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                } else {
                    Text(
                        authorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onFavorite != null) {
                    IconButton(onClick = onFavorite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (favorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (favorited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${cmd.likeCount}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (cmd.commentCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${cmd.commentCount}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 一键复制指令 (参考 CHelper/BNM: 复制原始命令文本)
                if (onCopy != null) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "复制指令",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** 指令审核状态徽章 */
@Composable
fun StatusBadge(label: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.18f)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

fun Command.categoryLabel(): String = when (category) {
    "survival" -> "生存"; "adventure" -> "冒险"; "redstone" -> "红石"; "scoreboard" -> "计分板"; "gamerule" -> "游戏规则"; else -> "其他"
}

@Composable
fun OutlinedTextField2(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun CommandOutputBox(commands: List<String>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val text = commands.joinToString("\n")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("生成结果 (${commands.size} 条)", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Button(onClick = { copyToClipboard(ctx, text) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部复制", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            // 每条命令单独展示, 可分别复制 (参考 CHelper/BNM)
            commands.forEachIndexed { idx, line ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF0A0F0C), RoundedCornerShape(8.dp))
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80), modifier = Modifier.width(22.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE7F2EA),
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { copyToClipboard(ctx, line, "已复制第 ${idx + 1} 条") },
                        modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, "复制第${idx + 1}条",
                            tint = Color(0xFF9DB4A5), modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
