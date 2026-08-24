package com.canglan.cyancodex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.canglan.cyancodex.ui.Routes
import com.canglan.cyancodex.ui.components.AppTopBar

private data class GenEntry(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val desc: String,
    val route: String,
)

private val gens = listOf(
    GenEntry("T显生成器", Icons.Default.BarChart, Color(0xFFFBBF24), "计分板/T显示指令", Routes.SCOREBOARD),
    GenEntry("穷举", Icons.Default.SwapHoriz, Color(0xFFA78BFA), "批量生成不同数据指令", Routes.ENUMERATE),
    GenEntry("滚动字幕", Icons.Default.Campaign, Color(0xFFF472B6), "公告/滚动字幕", Routes.SCROLLER),
    GenEntry("音乐生成器", Icons.Default.MusicNote, Color(0xFF34D399), "命令方块音乐", Routes.MUSIC),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorsScreen(nav: NavHostController) {
    Scaffold(
        topBar = {
            AppTopBar(title = "生成器", icon = Icons.Default.Build)
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            items(gens) { g ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate(g.route) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(g.icon, null, tint = g.tint, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(g.title, style = MaterialTheme.typography.titleSmall)
                        Text(g.desc, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.HISTORY) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("生成历史", style = MaterialTheme.typography.titleSmall)
                        Text("查看已保存的生成记录", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
