package com.canglan.cyancodex.ai

import com.canglan.cyancodex.data.ChatMsg
import com.canglan.cyancodex.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI Agent: 直接调用用户自备的 OpenAI 兼容接口 (支持 DeepSeek/通义/Kimi 等)
 */
object AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    const val SYSTEM_PROMPT = """你是网易版《我的世界》的指令助手。
你的任务: 帮助用户编写网易版(手机基岩版内核)指令, 每次输出指令前必须先用【网易版语法自查】对照核对语法, 语法不对就改正后再输出。
要求:
1. 直接输出可直接粘贴的指令, 用 / 开头;
2. 涉及多条指令时, 分条列出并说明每条放入什么模式(脉冲/循环/链式)的命令方块;
3. 回答简洁, 中文;
4. 生成音乐、T显、滚动字幕等时, 给出完整可用的指令集合;
5. 用户提到指令时, 严格对照下发的语法条目检查 (尤其 execute/scoreboard/titleraw 的写法)。"""

    /** 组装系统提示词: 基础提示 + 网易版要点 + 本次问题相关的指令语法自查 */
    private fun buildSystemPrompt(userText: String): String {
        val hit = NetEaseWiki.lookup(userText)
        val sb = StringBuilder(SYSTEM_PROMPT)
        sb.append('\n').append(NetEaseWiki.FULL_NOTES)
        if (hit.isNotEmpty()) sb.append('\n').append("【网易版语法自查】\n").append(hit)
        return sb.toString()
    }

    /** 非流式对话, 返回完整回复 */
    suspend fun chat(messages: List<ChatMsg>): String = withContext(Dispatchers.IO) {
        val base = Session.aiApiBase
        val key = Session.aiApiKey
        val model = Session.aiModel.ifBlank { "gpt-4o-mini" }
        val url = if (base.endsWith("/chat/completions")) base else base + "chat/completions"
        // 取最近一条用户消息做语法匹配 (wiki 自查工具)
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()

        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", buildSystemPrompt(userText)))
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        }
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = try {
                    JSONObject(text).optString("error", text).let { e ->
                        JSONObject(e).optString("message", text)
                    }
                } catch (e: Exception) { text }
                throw RuntimeException("AI 请求失败(${resp.code}): $err")
            }
            val j = JSONObject(text)
            j.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?: throw RuntimeException("AI 响应解析失败")
        }
    }

    /** 检查 API 是否已配置 */
    fun isConfigured(): Boolean = Session.aiApiKey.isNotBlank()
}
