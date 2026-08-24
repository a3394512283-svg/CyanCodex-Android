package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.data.ApiClient
import com.canglan.cyancodex.data.CheckinStatus
import com.canglan.cyancodex.ui.AppState
import com.canglan.cyancodex.ui.components.AppTopBar
import com.canglan.cyancodex.ui.components.LoadingBox
import com.canglan.cyancodex.ui.components.safeApi
import kotlinx.coroutines.launch

/** 每日签到: 签到得积分, 积分可解锁 AI 等高级能力 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<CheckinStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            val r = safeApi { ApiClient.get().checkinStatus() }
            if (r.ok) st = r.data else msg = r.error ?: "加载失败"
            loading = false
        }
    }

    fun doCheckin() {
        scope.launch {
            busy = true
            msg = ""
            val r = safeApi { ApiClient.get().checkin() }
            busy = false
            if (r.ok && r.data != null) {
                st = r.data
                AppState.patchUser(points = r.data.points)
                msg = "签到成功，获得 +${r.data.gained} 积分"
            } else {
                msg = r.error ?: "签到失败"
                load()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = { AppTopBar(title = "每日签到", icon = Icons.Default.EventAvailable, onBack = { nav.popBackStack() }) },
    ) { pad ->
        if (loading && st == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val s = st
            if (s != null) {
                // 积分卡
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("当前积分", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${s.points}", style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("积分可解锁 AI Agent 等高级能力", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                // 统计行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("连续签到", "${s.streak} 天", Modifier.weight(1f))
                    MiniStat("累计签到", "${s.total} 次", Modifier.weight(1f))
                }

                // 今日可得
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (s.checkedToday) "今日已签到 ✓" else "今日签到可得",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (s.checkedToday) "明天继续签到可保持连续奖励" else "签到 +${s.todayGain} 积分（基础 20 + 连续奖励）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                // 签到按钮
                Button(
                    onClick = { doCheckin() },
                    enabled = !s.checkedToday && !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.EventAvailable, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.checkedToday) "今日已签到" else "立即签到", fontWeight = FontWeight.Bold)
                    }
                }

                if (msg.isNotBlank()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("签到成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }

                Text("规则：每天可签到 1 次，基础 +20 积分；连续签到每天额外 +5 积分（封顶 +50），断签后重新累计。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
