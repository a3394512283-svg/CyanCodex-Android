package com.canglan.cyancodexreviewer.data

import android.content.Context
import com.canglan.cyancodexreviewer.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// ---------- 模型 ----------
data class ApiResp<T>(val ok: Boolean = false, val data: T? = null, val error: String? = null)

data class LoginResp(val token: String? = null, val code: String? = null, val nickname: String? = null)

data class ReviewCommand(
    val id: Long = 0, val title: String = "", val description: String = "", val command: String = "",
    val category: String = "other", @SerializedName("mc_version") val mcVersion: String = "",
    val tags: String = "", @SerializedName("created_at") val createdAt: String? = null,
    val author: String? = null,
)

data class ReviewStats(
    val pending: Long = 0, val reviewed: Long = 0,
)

data class Page<T>(val list: List<T> = emptyList(), val total: Int = 0, val page: Int = 1, val size: Int = 20)

// ---------- API ----------
interface ReviewerApi {
    @POST("api/reviewer/login")
    suspend fun login(@Body body: Map<String, String>): ApiResp<LoginResp>

    @GET("api/reviewer/pending")
    suspend fun pending(@Query("page") page: Int, @Query("size") size: Int): ApiResp<Page<ReviewCommand>>

    @GET("api/reviewer/stats")
    suspend fun stats(): ApiResp<ReviewStats>

    @PUT("api/reviewer/commands/{id}")
    suspend fun review(@Path("id") id: Long, @Body body: Map<String, String>): ApiResp<Any?>

    // 插件审核 (意见 #37)
    @GET("api/reviewer/plugins/pending")
    suspend fun pendingPlugins(): ApiResp<List<ReviewPlugin>>

    @PUT("api/reviewer/plugins/{id}")
    suspend fun reviewPlugin(@Path("id") id: Long, @Body body: Map<String, String>): ApiResp<Any?>
}

data class ReviewPlugin(
    val id: Long = 0, val title: String = "", val description: String = "",
    val version: String = "", val size: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    val author: String? = null,
)

object ReviewerSession {
    private const val PREFS = "cyancodex_reviewer"
    private lateinit var sp: android.content.SharedPreferences
    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    var token: String?
        get() = sp.getString("token", null)
        set(v) { sp.edit().putString("token", v).apply() }
    var code: String
        get() = sp.getString("code", "")!!
        set(v) { sp.edit().putString("code", v).apply() }
    fun isLoggedIn(): Boolean = !token.isNullOrBlank()
    fun logout() { sp.edit().remove("token").remove("code").apply() }
}

object ReviewerDevice {
    @Volatile private var cached: String? = null

    /** 设备唯一 ID: Android ID + 签名哈希, 用于邀请码绑定 */
    fun id(ctx: Context): String {
        cached?.let { return it }
        val aid = android.provider.Settings.Secure.getString(
            ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val sig = try {
            val pm = ctx.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val certs = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION") info.signatures
            }
            val cert = certs?.firstOrNull()?.toByteArray() ?: ByteArray(0)
            MessageDigest.getInstance("SHA-256").digest(cert)
                .joinToString("") { "%02X".format(it.toInt() and 0xFF) }.take(16)
        } catch (e: Exception) { "nosig" }
        cached = MessageDigest.getInstance("SHA-256")
            .digest("$aid|$sig".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            .take(48)
        return cached!!
    }
}

object ReviewerApiClient {
    @Volatile private var api: ReviewerApi? = null

    // 内置服务器地址 (XOR("canglan") + Base64, 与主 App 同源; 防明文抓取)
    private const val SERVER_KEY = "canglan"
    private const val SERVER_ENC = "CxUaFx9bQUwCCglCBBYCDB4LCU8NDAxB"

    fun decodeServer(): String {
        val enc = java.util.Base64.getDecoder().decode(SERVER_ENC)
        val key = SERVER_KEY.toByteArray(Charsets.UTF_8)
        val out = ByteArray(enc.size)
        for (i in enc.indices) out[i] = (enc[i].toInt() xor key[i % key.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    fun get(ctx: Context): ReviewerApi {
        api?.let { return it }
        synchronized(this) {
            return api ?: run {
                val deviceId = ReviewerDevice.id(ctx)
                val auth = Interceptor { chain ->
                    val b = chain.request().newBuilder()
                    ReviewerSession.token?.let { b.header("Authorization", "Bearer $it") }
                    b.header("Accept", "application/json")
                    b.header("X-Device-Id", deviceId)
                    b.header("X-App-Version", BuildConfig.VERSION_NAME)
                    chain.proceed(b.build())
                }
                // 业务错误(非2xx + {ok:false} JSON)改写为 200 透传文案
                val errorBody = Interceptor { chain ->
                    val req = chain.request()
                    val resp = chain.proceed(req)
                    if (!resp.isSuccessful) {
                        val ct = resp.headers["Content-Type"] ?: ""
                        if (ct.contains("json", ignoreCase = true)) {
                            val raw = resp.body?.string()
                            if (!raw.isNullOrBlank()) {
                                try {
                                    val obj = org.json.JSONObject(raw)
                                    if (!obj.optBoolean("ok", true)) {
                                        return@Interceptor resp.newBuilder()
                                            .code(200).message("OK")
                                            .body(raw.toResponseBody(resp.body?.contentType()))
                                            .build()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    resp
                }
                val client = OkHttpClient.Builder()
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .addInterceptor(errorBody)
                    .addInterceptor(auth)
                    .build()
                Retrofit.Builder()
                    .baseUrl(decodeServer())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ReviewerApi::class.java).also { api = it }
            }
        }
    }
}
