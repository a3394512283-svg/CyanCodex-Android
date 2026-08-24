package com.canglan.cyancodex.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 更新包下载/安装:
 * 发版约定 APK 托管在 服务器 /downloads/CyanCodex-vX.Y.Z.apk
 * 下载地址: 源站 cdn.example.com (CDN cdn.example.com 故障, 已回退源站)
 */
object Updater {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 下载服务器 (源站) */
    private val downloadBases: List<String> by lazy {
        listOf(Session.baseUrl.trimEnd('/'))
    }

    /** 按发版约定生成下载地址 */
    fun apkUrl(version: String): String =
        downloadBases.first() + "/downloads/CyanCodex-v" + version.trimStart('v') + ".apk"

    /**
     * 服务器权威下载地址: 优先 /api/update/latest 返回的真实文件地址,
     * 失败时回退为按版本号拼接的约定地址。
     */
    suspend fun resolveUrl(fallbackVersion: String?): String? {
        val latest = try { ApiClient.get().latestUpdate() } catch (e: Exception) { null }
        val data = latest?.data
        if (data != null && data.url.isNotBlank()) {
            return downloadBases.first() + data.url
        }
        return fallbackVersion?.let { apkUrl(it) }
    }

    /** 用浏览器打开下载地址 (下载失败时的兜底方案) */
    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "无法打开下载页面", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 下载 APK 到 cache/downloads/, 返回文件; 失败返回 null。
     * url 为相对路径(/downloads/...)时按候选服务器逐个尝试(加速节点优先, 自动回退);
     * 完整地址则直接下载。onProgress: (已完成, 总大小) — 总大小未知时为 -1
     */
    suspend fun download(context: Context, url: String, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }): File? =
        withContext(Dispatchers.IO) {
            val urls = if (url.startsWith("http")) listOf(url)
                else downloadBases.map { it + url }
            val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val target = File(dir, "CyanCodex-update.apk")
            for (u in urls) {
                val ok = try {
                    val req = okhttp3.Request.Builder().url(u).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use false
                        val body = resp.body ?: return@use false
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            FileOutputStream(target).use { fos ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    fos.write(buf, 0, n)
                                    done += n
                                    onProgress(done, total)
                                }
                            }
                        }
                        true
                    }
                } catch (e: Exception) {
                    target.delete()
                    false
                }
                if (ok) return@withContext target
            }
            null
        }

    /** 用系统安装器安装 (FileProvider 分享 APK) */
    fun install(context: Context, file: File): Boolean = try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
