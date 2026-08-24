package com.canglan.cyancodex.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录/注册页 —— 液态玻璃(Liquid Glass)风格
 * 注册: 图形验证码 + 邮箱验证码(邮件含QQ群链接)
 * asGate=true 时作为「登录门禁」全屏展示: 未登录/未注册无法使用 App;
 * 首次进入默认「注册」, 曾注册过的用户默认「登录」; 成功后不 popBackStack(由门禁状态切换)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(nav: NavHostController, asGate: Boolean = false) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var isLogin by remember { mutableStateOf(!asGate || Session.hasRegistered) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var needTotp by remember { mutableStateOf(false) }   // 两步验证: 需身份验证器验证码
    var totpCode by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var captchaId by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf("") }
    var captchaText by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    suspend fun refreshCaptcha() {
        val r = safeApi { ApiClient.get().captcha() }
        if (r.ok) {
            captchaId = r.data?.id ?: ""
            captchaImage = r.data?.image ?: ""
            captchaText = ""
        } else {
            error = r.error ?: "验证码获取失败"
        }
    }

    LaunchedEffect(isLogin) {
        if (!isLogin) refreshCaptcha()
    }

    // 发送邮箱验证码 (60s 倒计时)
    fun sendCode() {
        if (!email.contains("@")) { error = "请先填写正确的邮箱"; return }
        scope.launch {
            sending = true
            val r = safeApi { ApiClient.get().sendEmailCode(mapOf("email" to email.trim())) }
            sending = false
            if (r.ok) {
                error = ""
                countdown = 60
            } else {
                error = r.error ?: "发送失败"
            }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown-- }
    }

    fun submit() {
        if (username.isBlank() || password.isBlank()) { error = "请填写用户名和密码"; return }
        if (needTotp && totpCode.isBlank()) { error = "请输入两步验证码"; return }
        scope.launch {
            busy = true
            error = ""
            try {
                val r = if (isLogin) {
                    val body = mutableMapOf("username" to username.trim(), "password" to password)
                    if (needTotp) body["totp_code"] = totpCode.trim()
                    safeApi { ApiClient.get().login(body) }
                } else {
                    if (captchaText.isBlank()) { error = "请填写图形验证码"; busy = false; return@launch }
                    if (emailCode.isBlank()) { error = "请填写邮箱验证码"; busy = false; return@launch }
                    safeApi {
                        ApiClient.get().register(mapOf(
                            "username" to username.trim(),
                            "password" to password,
                            "nickname" to nickname.ifBlank { username.trim() },
                            "email" to email.trim(),
                            "captcha_id" to captchaId,
                            "captcha_text" to captchaText.trim(),
                            "email_code" to emailCode.trim(),
                        ))
                    }
                }
                val d = r.data
                if (r.ok && d != null && d.token != null && d.user != null) {
                    // 登录/注册成功均标记: 本设备已有账号 (首次进入引导注册, 之后默认登录)
                    Session.hasRegistered = true
                    needTotp = false
                    totpCode = ""
                    AppState.setLogin(d.user, d.token)
                    if (!asGate) nav.popBackStack()
                } else if (r.needTotp) {
                    // 账号开启了两步验证: 显示验证码输入框, 重新提交
                    needTotp = true
                    error = r.error ?: "请输入两步验证码"
                } else {
                    error = r.error ?: "操作失败"
                    if (!isLogin) refreshCaptcha()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            } finally { busy = false }
        }
    }

    // 液态玻璃背景
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1A14), Color(0xFF10352B), Color(0xFF0B1A14))))) {
        // 装饰光斑
        Box(Modifier.size(260.dp).offset(x = (-60).dp, y = 60.dp)
            .blur(70.dp).background(Brush.radialGradient(listOf(Color(0x554ADE80), Color.Transparent)), CircleShape))
        Box(Modifier.size(300.dp).offset(x = 700.dp, y = 300.dp)
            .blur(80.dp).background(Brush.radialGradient(listOf(Color(0x4438BDF8), Color.Transparent)), CircleShape))
        Box(Modifier.size(220.dp).offset(x = 120.dp, y = 1500.dp)
            .blur(70.dp).background(Brush.radialGradient(listOf(Color(0x44FBBF24), Color.Transparent)), CircleShape))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                painter = androidx.compose.ui.res.painterResource(com.canglan.cyancodex.R.drawable.usericon),
                contentDescription = "CyanCodex",
                modifier = Modifier.size(56.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            )
            Text("CyanCodex", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("沧蓝工坊 · 我的世界指令助手", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(24.dp))

            // 液态玻璃卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 登录/注册切换 (固定位置: 左=登录, 右=注册)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassChip("登录", selected = isLogin) { isLogin = true; error = "" }
                        GlassChip("注册", selected = !isLogin) { isLogin = false; error = "" }
                    }

                    GlassField(username, { username = it }, if (isLogin) "用户名 / 邮箱" else "用户名")
                    GlassField(password, { password = it }, "密码", isPassword = true)

                    if (isLogin && needTotp) {
                        GlassField(totpCode, { totpCode = it }, "两步验证码（身份验证器）")
                        Text("该账号已开启两步验证，请输入身份验证器中的 6 位验证码",
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }

                    if (!isLogin) {
                        GlassField(nickname, { nickname = it }, "昵称(可选)")
                        GlassField(email, { email = it }, "邮箱")

                        // 图形验证码
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassField(captchaText, { captchaText = it }, "图形验证码", modifier = Modifier.weight(1f))
                            val bmp = remember(captchaImage) {
                                runCatching {
                                    val b64 = captchaImage.substringAfter("base64,")
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "验证码",
                                    modifier = Modifier.size(96.dp, 42.dp).clip(RoundedCornerShape(8.dp))
                                        .clickable { scope.launch { refreshCaptcha() } },
                                )
                            } else {
                                Box(Modifier.size(96.dp, 42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                    Text("加载中…", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                            IconButton(onClick = { scope.launch { refreshCaptcha() } }) {
                                Icon(Icons.Default.Refresh, "刷新验证码", tint = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        // 邮箱验证码
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassField(emailCode, { emailCode = it }, "邮箱验证码", modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { sendCode() },
                                enabled = !sending && countdown == 0,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Text(if (countdown > 0) "${countdown}s" else if (sending) "发送中…" else "发送验证码",
                                    fontSize = 12.sp)
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }

                    Button(
                        onClick = { submit() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4ADE80),
                            contentColor = Color(0xFF052E16),
                        ),
                    ) { Text(if (busy) "请稍候…" else if (isLogin) "登录" else "注册并登录", fontWeight = FontWeight.Bold) }

                    Text(if (isLogin) "支持用户名或邮箱登录 · 未注册请先注册"
                        else "注册即送 100 积分 · 需填写全部信息",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(18.dp))
            // QQ 群超链接 (直接唤起 QQ)
            Text(
                "加入我们的QQ群 $QQ_GROUP_NUMBER",
                color = Color(0xFF8AB4FF),
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        joinQqGroup(ctx)
                    },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.08f),
        contentColor = if (selected) Color(0xFF052E16) else Color.White.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.2f)),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.6f)) },
        singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = TextStyle(color = Color.White),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedContainerColor = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            cursorColor = Color.White,
            focusedLabelColor = Color.White.copy(alpha = 0.85f),
        ),
    )
}
