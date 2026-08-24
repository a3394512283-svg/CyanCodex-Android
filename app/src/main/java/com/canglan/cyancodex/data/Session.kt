package com.canglan.cyancodex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本地会话与配置存储
 */
object Session {
    private const val PREFS = "cyancodex"
    private lateinit var sp: SharedPreferences
    private val gson = Gson()

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 清理历史版本可能保存过的旧服务器地址(服务器地址现在固定内置)
        sp.edit().remove("base_url").apply()
        // 迁移旧版明文 token / AI Key -> 加密存储 (v1.0.9 及之前是明文)
        migrateLegacySecrets()
        // v1.0.50: 插件改为默认关闭 (此前"自定义背景"插件默认开启, 导致主页背景变黑且白天模式无效, 反馈 id=24)
        if (!sp.getBoolean("plugin_migrated_v150", false)) {
            sp.edit()
                .remove("plugin_dev.cyancodex.background")
                .remove("plugin_dev.cyancodex.example")
                .putBoolean("plugin_migrated_v150", true)
                .apply()
        }
    }

    private fun migrateLegacySecrets() {
        val edit = sp.edit()
        if (sp.getString("token_enc", null) == null) {
            val old = sp.getString("token", null)
            if (old != null) edit.putString("token_enc", Crypto.encryptOrNull(old))
            edit.remove("token")
        }
        if (sp.getString("ai_api_key_enc", null) == null) {
            val oldKey = sp.getString("ai_api_key", null)
            if (oldKey != null) edit.putString("ai_api_key_enc", Crypto.encryptOrNull(oldKey))
            edit.remove("ai_api_key")
        }
        edit.apply()
    }

    // ---------- 认证 (token 加密落盘, 明文仅内存短暂存在) ----------
    var token: String?
        get() = Crypto.decryptOrNull(sp.getString("token_enc", null))
        set(v) { sp.edit().putString("token_enc", Crypto.encryptOrNull(v)).apply() }

    fun saveUser(u: User) {
        sp.edit().putString("user_json", gson.toJson(u)).apply()
        token?.let { sp.edit().putString("token_enc", Crypto.encryptOrNull(it)).apply() }
    }

    fun user(): User? {
        val j = sp.getString("user_json", null) ?: return null
        return try { gson.fromJson(j, User::class.java) } catch (e: Exception) { null }
    }

    fun updatePoints(points: Int) {
        user()?.let { u ->
            saveUser(u.copy(points = points))
        }
    }

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    /** 是否曾注册过账号 (首次进入 App 引导「注册」而非「登录」) */
    var hasRegistered: Boolean
        get() = sp.getBoolean("has_registered", false)
        set(v) { sp.edit().putBoolean("has_registered", v).apply() }

    fun logout() {
        sp.edit().remove("token_enc").remove("user_json").apply()
    }

    // ---------- API 配置 ----------
    // 服务器地址以「XOR+Base64 混淆」形式内置, 避免 APK 明文暴露服务器 IP;
    // 运行时解码, App 开箱即连官方服务器, 用户无需手动填写。
    // 注意: 始终使用内置默认地址, 忽略历史版本可能保存过的旧地址(防止旧值覆盖新服务器)
    // 服务器地址通过 CDN 域名 cdn.example.com (https) 访问, 不再 IP 直连
    private const val SERVER_KEY = "canglan"
    private const val SERVER_ENC = "CxUaFx9bQUwCCglCBBYCDB4LCU8NDAxB"

    private fun decodeServer(): String {
        val enc = java.util.Base64.getDecoder().decode(SERVER_ENC)
        val key = SERVER_KEY.toByteArray(Charsets.UTF_8)
        val out = ByteArray(enc.size)
        for (i in enc.indices) out[i] = (enc[i].toInt() xor key[i % key.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    val DEFAULT_BASE_URL: String get() = decodeServer()

    /** 服务器地址固定使用内置默认值 */
    val baseUrl: String get() = DEFAULT_BASE_URL

    // ---------- AI 配置 (用户自备 API) ----------
    var aiApiBase: String
        get() = sp.getString("ai_api_base", "https://api.openai.com/v1/")!!
        set(v) { sp.edit().putString("ai_api_base", if (v.endsWith("/")) v else "$v/").apply() }

    var aiApiKey: String
        get() = Crypto.decryptOrNull(sp.getString("ai_api_key_enc", null)) ?: ""
        set(v) { sp.edit().putString("ai_api_key_enc", Crypto.encryptOrNull(v)).apply() }

    var aiModel: String
        get() = sp.getString("ai_model", "gpt-4o-mini")!!
        set(v) { sp.edit().putString("ai_model", v).apply() }

    // ---------- 快捷指令 (输入法/悬浮窗共用) ----------
    fun quickCommands(): MutableList<QuickCommand> {
        val j = sp.getString("quick_commands", null) ?: return mutableListOf()
        return try {
            gson.fromJson(j, object : TypeToken<MutableList<QuickCommand>>() {}.type)
        } catch (e: Exception) { mutableListOf() }
    }

    fun saveQuickCommands(list: List<QuickCommand>) {
        sp.edit().putString("quick_commands", gson.toJson(list)).apply()
    }

    fun addQuickCommand(qc: QuickCommand) {
        val list = quickCommands().filter { it.command != qc.command }.toMutableList()
        list.add(0, qc)
        if (list.size > 100) list.removeAt(list.size - 1)
        saveQuickCommands(list)
    }

    fun removeQuickCommand(command: String) {
        saveQuickCommands(quickCommands().filter { it.command != command })
    }

    // ---------- 最近使用 (IME 快捷) ----------
    fun recentCommands(): MutableList<String> {
        val j = sp.getString("recent_commands", null) ?: return mutableListOf()
        return try { gson.fromJson(j, object : TypeToken<MutableList<String>>() {}.type) } catch (e: Exception) { mutableListOf() }
    }

    fun addRecent(command: String) {
        val list = recentCommands().filter { it != command }.toMutableList()
        list.add(0, command)
        if (list.size > 30) list.removeAt(list.size - 1)
        sp.edit().putString("recent_commands", gson.toJson(list)).apply()
    }

    // ---------- 收藏缓存 (IME/悬浮窗离线可用) ----------
    var cachedFavorites: String
        get() = sp.getString("cached_favorites", "[]")!!
        set(v) { sp.edit().putString("cached_favorites", v).apply() }

    // ---------- 优化选项 ----------
    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) { sp.edit().putBoolean("reduce_motion", v).apply() }

    var animationsScale: Float
        get() = sp.getFloat("anim_scale", 1f)
        set(v) { sp.edit().putFloat("anim_scale", v).apply() }

    fun clearCacheData() {
        sp.edit().remove("cached_favorites").remove("recent_commands").apply()
    }

    // ---------- 悬浮窗调节 (BNM 参考: 大小/透明度/位置持久化) ----------
    var floatWidthDp: Int
        get() = sp.getInt("float_width_dp", 300)
        set(v) { sp.edit().putInt("float_width_dp", v).apply() }

    var floatHeightDp: Int
        get() = sp.getInt("float_height_dp", 520)
        set(v) { sp.edit().putInt("float_height_dp", v).apply() }

    var floatOpacity: Float
        get() = sp.getFloat("float_opacity", 1f)
        set(v) { sp.edit().putFloat("float_opacity", v).apply() }

    var floatX: Int
        get() = sp.getInt("float_x", -1)   // -1 = 未设置(默认右上角)
        set(v) { sp.edit().putInt("float_x", v).apply() }

    var floatY: Int
        get() = sp.getInt("float_y", -1)
        set(v) { sp.edit().putInt("float_y", v).apply() }

    // 悬浮球位置 (BNM 式悬浮球, -1 = 默认右侧中部)
    var floatBallX: Int
        get() = sp.getInt("float_ball_x", -1)
        set(v) { sp.edit().putInt("float_ball_x", v).apply() }

    var floatBallY: Int
        get() = sp.getInt("float_ball_y", -1)
        set(v) { sp.edit().putInt("float_ball_y", v).apply() }

    // 悬浮球大小 (dp, 小/中/大)
    var floatBallSizeDp: Int
        get() = sp.getInt("float_ball_size_dp", 52)
        set(v) { sp.edit().putInt("float_ball_size_dp", v).apply() }

    // ---------- 插件 ----------
    // 默认关闭: 需用户在设置→插件里主动开启 (避免默认改动外观, 反馈 id=24)
    fun pluginEnabled(id: String): Boolean = sp.getBoolean("plugin_$id", false)
    fun setPluginEnabled(id: String, v: Boolean) { sp.edit().putBoolean("plugin_$id", v).apply() }

    // ---------- AI 对话 (JSON, 会话内持久: 切页/重进不丢, 意见 id=12) ----------
    var aiMessages: String
        get() = sp.getString("ai_messages", "[]")!!
        set(v) { sp.edit().putString("ai_messages", v).apply() }

    // ---------- 自定义背景图片 (意见 id=22) ----------
    var backgroundImage: String
        get() = sp.getString("background_image", "")!!
        set(v) { sp.edit().putString("background_image", v).apply() }

    // ---------- 外观 ----------
    // theme_mode: system=跟随系统 / light=白天 / dark=夜晚 (默认跟随系统)
    var themeMode: String
        get() = sp.getString("theme_mode", "system")!!
        set(v) { sp.edit().putString("theme_mode", v).apply() }

    // ---------- 生成历史 (本地兜底: 离线/未登录也保存) ----------
    fun localHistory(): List<GenHistory> {
        val j = sp.getString("local_history", null) ?: return emptyList()
        return try {
            gson.fromJson(j, object : TypeToken<List<GenHistory>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    fun addLocalHistory(genType: String, inputJson: String, outputCommand: String) {
        val list = localHistory().toMutableList()
        list.add(0, GenHistory(
            id = System.currentTimeMillis(),
            genType = genType, inputJson = inputJson, outputCommand = outputCommand, createdAt = null,
        ))
        if (list.size > 200) list.removeAt(list.size - 1)
        sp.edit().putString("local_history", gson.toJson(list)).apply()
    }

    fun removeLocalHistory(outputCommand: String) {
        sp.edit().putString("local_history", gson.toJson(localHistory().filter { it.outputCommand != outputCommand })).apply()
    }

    // ---------- 公告已读 (commit 同步写盘, 防止进程被杀丢失导致反复弹窗) ----------
    var lastSeenAnnouncementId: Long
        get() = sp.getLong("last_seen_announcement", 0)
        set(v) { sp.edit().putLong("last_seen_announcement", v).commit() }

    // 已发系统通知的公告 id (与已读分开, 避免通知逻辑干扰弹窗逻辑)
    var lastNotifiedAnnouncementId: Long
        get() = sp.getLong("last_notified_announcement", 0)
        set(v) { sp.edit().putLong("last_notified_announcement", v).commit() }

    // 已发系统通知的站内信 id (新增消息才提醒)
    var lastNotifiedMessageId: Long
        get() = sp.getLong("last_notified_message", 0)
        set(v) { sp.edit().putLong("last_notified_message", v).commit() }

    // 设备唯一 ID (浏览量去重用, 首次生成后固定)
    val deviceId: String
        get() {
            sp.getString("device_id", null)?.let { return it }
            val id = java.util.UUID.randomUUID().toString()
            sp.edit().putString("device_id", id).apply()
            return id
        }

    fun cacheSizeBytes(): Long = 0  // 占位: 主要缓存都在内存/网络
}
