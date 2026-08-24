package com.canglan.cyancodex.data

import com.google.gson.annotations.SerializedName

// ---- 通用响应包装 ----
data class ApiResp<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    @SerializedName("need_totp") val needTotp: Boolean = false,   // 登录需两步验证码
)

// ---- 用户/认证 ----
data class User(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val email: String = "",
    val gender: String = "secret",
    val points: Int = 0,
    @SerializedName("ai_unlocked") val aiUnlocked: Boolean = false,
    @SerializedName("ai_unlock_expire_at") val aiUnlockExpireAt: String? = null,
    @SerializedName("vip_level") val vipLevel: Int = 0,
    val role: String = "user",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class LoginResp(val token: String? = null, val user: User? = null)

data class CaptchaResp(val id: String = "", val image: String = "")

// ---- 指令 ----
data class Command(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val command: String = "",
    val category: String = "other",
    val source: String = "user",
    val quality: String = "high",
    @SerializedName("mc_version") val mcVersion: String = "",
    val tags: String = "",
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    val image: String? = null,       // 效果图 (/uploads/command_images/...)
    val status: String = "active",   // active=已通过 pending=待审核 rejected=已驳回
    @SerializedName("review_note") val reviewNote: String? = null, // 审核意见/驳回理由
    val liked: Boolean = false,      // 当前用户是否已点赞 (详情接口返回)
    @SerializedName("created_at") val createdAt: String? = null,
    val author: String? = null,
    @SerializedName("author_role") val authorRole: String? = null, // admin 显示管理员徽章 (意见 #43)
)

data class CommandPage(
    val list: List<Command> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

data class Category(val key: String = "", val label: String = "")

data class FavResp(val favorited: Boolean = false)

// ---- 教程 ----
data class Tutorial(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val category: String = "basic",
    val source: String = "platform",
    val quality: String = "high",
    val type: String = "post",            // post=帖子 / video=视频
    @SerializedName("video_url") val videoUrl: String = "",
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    val author: String? = null,
)

data class TutorialPage(
    val list: List<Tutorial> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

// ---- 签到 (替代充值得积分) ----
data class CheckinStatus(
    @SerializedName("checked_today") val checkedToday: Boolean = false,
    @SerializedName("last_date") val lastDate: String? = null,
    val streak: Int = 0,
    val total: Int = 0,
    val points: Int = 0,
    @SerializedName("today_gain") val todayGain: Int = 0,
    val gained: Int = 0,     // 本次签到获得 (签到响应)
    val bonus: Int = 0,      // 本次连续奖励
)

data class AiStatus(
    val unlocked: Boolean = false,
    val expireAt: String? = null,
    val points: Int = 0,
    @SerializedName("pointsNeeded") val pointsNeeded: Int = 200,
)

data class SimpleOk(
    val unlocked: Boolean? = null,
    val points: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

// ---- 历史/公告 ----
// 插件库 (意见 #37)
data class Plugin(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val size: Int = 0,
    val status: String = "pending",
    val downloads: Int = 0,
    @SerializedName("review_note") val reviewNote: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    val author: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class PluginPage(
    val list: List<Plugin> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

// 好友 (意见 #38)
data class FriendItem(
    val id: Long = 0,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("friend_id") val friendId: Long? = null,
    @SerializedName("peer_id") val peerId: Long? = null,
    val status: String = "pending",
    val nickname: String? = null,
    val avatar: String? = null,
    val username: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

// 私聊会话 (意见 #38)
data class Conversation(
    val id: Long = 0,
    @SerializedName("from_id") val fromId: Long? = null,
    @SerializedName("to_id") val toId: Long? = null,
    @SerializedName("peer_id") val peerId: Long? = null,
    val content: String = "",
    val nickname: String? = null,
    val avatar: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

// 私聊消息 (意见 #38)
data class DmMessage(
    val id: Long = 0,
    @SerializedName("from_id") val fromId: Long? = null,
    @SerializedName("to_id") val toId: Long? = null,
    val content: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

// 聊天室消息 (意见 #36)
data class ChatMessage(
    val id: Long = 0,
    val content: String = "",
    @SerializedName("user_id") val userId: Long? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val role: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

// AI 对话模型 (Gson 反射序列化: 必须放 data 包, R8 混淆需保留字段名)
data class ChatMsg(val role: String = "", val content: String = "")
data class UiMsg(val role: String = "", val content: String = "", val err: Boolean = false)

data class GenHistory(
    val id: Long = 0,
    @SerializedName("gen_type") val genType: String = "",
    @SerializedName("input_json") val inputJson: String = "",
    @SerializedName("output_command") val outputCommand: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class HistoryPage(
    val list: List<GenHistory> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

data class Announcement(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: String = "normal",
    val version: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class IdResp(val id: Long = 0)

// 具体 body 类型 (Retrofit 不允许 Map<String, Any> 含通配符)
data class ImportCmdItem(val title: String, val command: String, val tags: String)
data class ImportBody(val commands: List<ImportCmdItem>)

data class ImportResp(val imported: Int = 0)

// ---- 社区: 评论/用户主页/动态广场 ----
data class Comment(
    val id: Long = 0,
    @SerializedName("command_id") val commandId: Long = 0,
    val content: String = "",
    @SerializedName("user_id") val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    @SerializedName("user_role") val userRole: String? = null, // admin 管理员徽章 (意见 #43)
    @SerializedName("created_at") val createdAt: String? = null,
)

data class UserProfile(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val gender: String = "secret",
    val points: Int = 0,
    @SerializedName("ai_unlocked") val aiUnlocked: Boolean = false,
    val role: String = "user",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("follower_count") val followerCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("command_count") val commandCount: Int = 0,
    @SerializedName("is_following") val isFollowing: Boolean = false,
)

data class FeedItem(
    val type: String = "command",   // command | comment
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val category: String = "other",
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    val author: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    val content: String = "",            // 评论内容
    @SerializedName("command_id") val commandId: Long? = null,
    @SerializedName("command_title") val commandTitle: String? = null,
)

data class FeedPage(
    val list: List<FeedItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

data class AvatarResp(val avatar: String = "")

// ---- 积分明细 / 站内信 ----
data class PointsLogItem(
    val id: Long = 0,
    val delta: Int = 0,
    val balance: Int = 0,
    val reason: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class PointsLogPage(
    val list: List<PointsLogItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

// 积分转账结果 (意见 #42)
data class TransferResp(
    val sent: Int = 0,     // 转出积分
    val fee: Int = 0,      // 手续费
    val received: Int = 0, // 对方到账
    val balance: Int? = null, // 转出后余额
)

data class MessageItem(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    @SerializedName("is_read") val isRead: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class MessagePage(
    val list: List<MessageItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

data class UnreadCount(val count: Int = 0)

// ---- AI 对话历史 ----
data class AiHistoryItem(
    val id: Long = 0,
    val prompt: String = "",
    val result: String = "",
    @SerializedName("cost_points") val costPoints: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class AiHistoryPage(
    val list: List<AiHistoryItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
)

// ---- 更新/点赞 ----
data class UpdateInfo(
    val version: String = "",
    val url: String = "",
    val size: Long = 0,
    @SerializedName("min_version") val minVersion: String = "",
)

data class LikeResp(
    val liked: Boolean = false,
    @SerializedName("like_count") val likeCount: Int = 0,
)

// ---- 本地快捷指令(输入法/悬浮窗用) ----
data class QuickCommand(val title: String = "", val command: String = "")
