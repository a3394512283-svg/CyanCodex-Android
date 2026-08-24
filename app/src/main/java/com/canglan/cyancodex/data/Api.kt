package com.canglan.cyancodex.data

import com.canglan.cyancodex.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {

    // 认证
    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): ApiResp<LoginResp>

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): ApiResp<LoginResp>

    @GET("api/auth/captcha")
    suspend fun captcha(): ApiResp<CaptchaResp>

    @POST("api/auth/send-email-code")
    suspend fun sendEmailCode(@Body body: Map<String, String>): ApiResp<Any?>

    @GET("api/auth/me")
    suspend fun me(): ApiResp<User>

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): ApiResp<Any?>

    @PUT("api/auth/password")
    suspend fun changePassword(@Body body: Map<String, String>): ApiResp<Any?>

    @PUT("api/auth/email")
    suspend fun changeEmail(@Body body: Map<String, String>): ApiResp<Any?>

    // 指令库
    @GET("api/commands")
    suspend fun commands(
        @Query("category") category: String?,
        @Query("source") source: String?,
        @Query("quality") quality: String?,
        @Query("q") q: String?,
        @Query("mine") mine: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResp<CommandPage>

    @GET("api/commands/{id}")
    suspend fun commandDetail(@Path("id") id: Long): ApiResp<Command>

    @POST("api/commands")
    suspend fun uploadCommand(@Body body: Map<String, String>): ApiResp<IdResp>

    @POST("api/commands/import")
    suspend fun importCommands(@Body body: ImportBody): ApiResp<ImportResp>

    @PUT("api/commands/{id}")
    suspend fun updateCommand(@Path("id") id: Long, @Body body: Map<String, String>): ApiResp<Any?>

    @DELETE("api/commands/{id}")
    suspend fun deleteCommand(@Path("id") id: Long): ApiResp<Any?>

    @POST("api/commands/{id}/like")
    suspend fun likeCommand(@Path("id") id: Long): ApiResp<LikeResp>

    @POST("api/commands/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): ApiResp<FavResp>

    @GET("api/commands/favorites/list")
    suspend fun favorites(): ApiResp<List<Command>>

    @GET("api/commands/meta/categories")
    suspend fun categories(): ApiResp<List<Category>>

    // 教程
    @GET("api/tutorials")
    suspend fun tutorials(
        @Query("category") category: String?,
        @Query("source") source: String?,
        @Query("q") q: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResp<TutorialPage>

    @GET("api/tutorials/{id}")
    suspend fun tutorialDetail(@Path("id") id: Long): ApiResp<Tutorial>

    @POST("api/tutorials")
    suspend fun uploadTutorial(@Body body: Map<String, String>): ApiResp<IdResp>

    // 积分订单 (已废弃, 改为签到得积分)
    // 签到
    @GET("api/checkin")
    suspend fun checkinStatus(): ApiResp<CheckinStatus>

    @POST("api/checkin")
    suspend fun checkin(): ApiResp<CheckinStatus>

    // 积分明细
    @GET("api/points")
    suspend fun pointsLog(@Query("page") page: Int, @Query("size") size: Int): ApiResp<PointsLogPage>

    // 积分转账 (意见 #42, 手续费 20%)
    @POST("api/points/transfer")
    suspend fun pointsTransfer(@Body body: Map<String, String>): ApiResp<TransferResp>

    // 站内信
    @GET("api/messages")
    suspend fun messages(@Query("page") page: Int, @Query("size") size: Int): ApiResp<MessagePage>

    @GET("api/messages/unread-count")
    suspend fun unreadMessages(): ApiResp<UnreadCount>

    @POST("api/messages/{id}/read")
    suspend fun markMessageRead(@Path("id") id: Long): ApiResp<Any?>

    // AI
    @GET("api/ai/status")
    suspend fun aiStatus(): ApiResp<AiStatus>

    @POST("api/ai/unlock")
    suspend fun aiUnlock(): ApiResp<SimpleOk>

    @POST("api/ai/usage")
    suspend fun aiUsage(@Body body: Map<String, Any>): ApiResp<IdResp>

    // AI 对话历史
    @GET("api/ai/history")
    suspend fun aiHistory(@Query("page") page: Int, @Query("size") size: Int): ApiResp<AiHistoryPage>

    // AI 对话代理: 所有对话走服务器(自定义 Key 或平台 AI), 服务器记录全部历史
    @POST("api/ai/proxy")
    suspend fun aiProxy(@Body body: Map<String, String>): ApiResp<AiProxyResp>

    // 生成历史
    @GET("api/history")
    suspend fun history(@Query("type") type: String?, @Query("page") page: Int, @Query("size") size: Int): ApiResp<HistoryPage>

    @POST("api/history")
    suspend fun saveHistory(@Body body: Map<String, String>): ApiResp<IdResp>

    @DELETE("api/history/{id}")
    suspend fun deleteHistory(@Path("id") id: Long): ApiResp<Any?>

    // 公告
    @GET("api/announcements")
    suspend fun announcements(@Query("type") type: String?): ApiResp<List<Announcement>>

    // 社区: 头像上传
    @POST("api/auth/avatar")
    suspend fun uploadAvatar(@Body body: Map<String, String>): ApiResp<AvatarResp>

    // 社区: 评论
    @GET("api/comments/command/{id}")
    suspend fun comments(@Path("id") id: Long): ApiResp<List<Comment>>

    @POST("api/comments/command/{id}")
    suspend fun addComment(@Path("id") id: Long, @Body body: Map<String, String>): ApiResp<IdResp>

    @DELETE("api/comments/{id}")
    suspend fun deleteComment(@Path("id") id: Long): ApiResp<Any?>

    // 社区: 用户主页 / 关注
    @GET("api/users/{id}")
    suspend fun userProfile(@Path("id") id: Long): ApiResp<UserProfile>

    @GET("api/users/{id}/commands")
    suspend fun userCommands(@Path("id") id: Long, @Query("page") page: Int, @Query("size") size: Int): ApiResp<List<Command>>

    @POST("api/users/{id}/follow")
    suspend fun follow(@Path("id") id: Long): ApiResp<FavResp>

    @DELETE("api/users/{id}/follow")
    suspend fun unfollow(@Path("id") id: Long): ApiResp<FavResp>

    // 社区: 动态广场
    @GET("api/feed")
    suspend fun feed(@Query("page") page: Int, @Query("size") size: Int): ApiResp<FeedPage>

    // 更新: 服务器权威的最新版本/下载地址
    @GET("api/update/latest")
    suspend fun latestUpdate(): ApiResp<UpdateInfo>

    // 意见/申诉: 提交 (category: feedback=意见 appeal=申诉)
    @POST("api/feedback")
    suspend fun submitFeedback(@Body body: Map<String, String>): ApiResp<IdResp>

    // 两步验证 (TOTP)
    @GET("api/auth/2fa/status")
    suspend fun totpStatus(): ApiResp<TotpStatus>

    @POST("api/auth/2fa/setup")
    suspend fun totpSetup(): ApiResp<TotpSetup>

    @POST("api/auth/2fa/verify")
    suspend fun totpVerify(@Body body: Map<String, String>): ApiResp<SimpleOk>

    @POST("api/auth/2fa/disable")
    suspend fun totpDisable(@Body body: Map<String, String>): ApiResp<SimpleOk>

    // 指令效果图: 上传 base64 图片
    @POST("api/commands/{id}/image")
    suspend fun uploadCommandImage(@Path("id") id: Long, @Body body: Map<String, String>): ApiResp<ImageResp>

    @DELETE("api/commands/{id}/image")
    suspend fun deleteCommandImage(@Path("id") id: Long): ApiResp<Any?>

    // 客户端检测: 自检发现第三方客户端/篡改后主动上报, 服务器封禁当前账号
    @POST("api/auth/report-tamper")
    suspend fun reportTamper(@Body body: Map<String, String>): ApiResp<Any?>

    // 聊天室 (意见 #36)
    @GET("api/chat")
    suspend fun chatMessages(@Query("limit") limit: Int): ApiResp<List<ChatMessage>>

    @GET("api/chat/after")
    suspend fun chatAfter(@Query("after_id") afterId: Long): ApiResp<List<ChatMessage>>

    @POST("api/chat")
    suspend fun sendChat(@Body body: Map<String, String>): ApiResp<ChatMessage?>

    // 好友 (意见 #38)
    @GET("api/friends")
    suspend fun friends(@Query("status") status: String?): ApiResp<List<FriendItem>>

    @POST("api/friends/request")
    suspend fun friendRequest(@Body body: Map<String, String>): ApiResp<Any?>

    @POST("api/friends/handle")
    suspend fun friendHandle(@Body body: Map<String, String>): ApiResp<Any?>

    @POST("api/friends/remove")
    suspend fun friendRemove(@Body body: Map<String, String>): ApiResp<Any?>

    // 私聊 (意见 #38)
    @GET("api/dm/conversations")
    suspend fun dmConversations(): ApiResp<List<Conversation>>

    @GET("api/dm/messages")
    suspend fun dmMessages(@Query("peer_id") peerId: Long, @Query("before_id") beforeId: Long): ApiResp<List<DmMessage>>

    @GET("api/dm/messages/after")
    suspend fun dmAfter(@Query("peer_id") peerId: Long, @Query("after_id") afterId: Long): ApiResp<List<DmMessage>>

    @POST("api/dm/messages")
    suspend fun sendDm(@Body body: Map<String, String>): ApiResp<IdResp?>

    // 插件库 (意见 #37)
    @GET("api/plugins")
    suspend fun plugins(@Query("status") status: String?, @Query("page") page: Int, @Query("size") size: Int): ApiResp<PluginPage>

    @POST("api/plugins")
    suspend fun uploadPlugin(@Body body: Map<String, String>): ApiResp<IdResp>

    @GET("api/plugins/{id}")
    suspend fun pluginDetail(@Path("id") id: Long): ApiResp<Plugin>
}

data class ImageResp(val image: String? = null)

data class TotpStatus(val enabled: Boolean = false)
data class TotpSetup(val secret: String = "", @SerializedName("otpauth_uri") val otpauthUri: String = "")
data class AiProxyResp(
    val content: String? = null,
    val cost: Int = 0,        // 本次消耗积分 (平台 AI)
    val tokens: Int = 0,      // 本次消耗 tokens
    val balance: Int? = null, // 剩余积分 (平台 AI)
)

object ApiClient {
    @Volatile private var service: ApiService? = null

    fun get(): ApiService {
        val cached = service
        if (cached != null) return cached
        synchronized(this) {
            return service ?: build().also { service = it }
        }
    }

    /** 当用户修改了 API 地址后调用, 重建客户端 */
    fun reset() {
        synchronized(this) { service = null }
    }

    private fun build(): ApiService {
        val base = Session.baseUrl
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val b = req.newBuilder()
            Session.token?.let { b.header("Authorization", "Bearer $it") }
            b.header("Accept", "application/json")
            b.header("X-Device-Id", Session.deviceId)
            b.header("X-App-Version", BuildConfig.VERSION_NAME)
            // 客户端检测: 每次请求携带当前 APK 签名 SHA-256, 服务器与白名单比对,
            // 不符 -> 账号封禁; 本地拿不到签名(异常环境)时也上报该标识
            b.header("X-App-Signature", TamperDetector.ownSignature() ?: "unknown")
            chain.proceed(b.build())
        }
        // token 失效(401)时自动清理会话: 仅"已登录的请求"失效才登出;
        // 登录接口的 401 是密码错误, 不触发登出
        val authFailInterceptor = Interceptor { chain ->
            val req = chain.request()
            val resp = chain.proceed(req)
            if (resp.code == 401 && req.header("Authorization") != null) {
                Session.logout()
            }
            resp
        }
        // 服务器业务错误(非2xx + {ok:false} JSON)改写为 200, 让友好错误文案透传到 UI
        // 401: 已登录请求 -> 提示登录过期(登出由 authFailInterceptor 处理);
        //      登录请求(无 Authorization) -> 透传服务器文案(如"用户名或密码错误")
        val errorBodyInterceptor = Interceptor { chain ->
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
                                val body = if (resp.code == 401 && req.header("Authorization") != null) {
                                    org.json.JSONObject().put("ok", false)
                                        .put("error", "登录已过期，请重新登录").toString()
                                } else raw
                                return@Interceptor resp.newBuilder()
                                    .code(200).message("OK")
                                    .body(body.toResponseBody(resp.body?.contentType()))
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
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(errorBodyInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(authFailInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
