package com.example.airquality

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

data class NavItem(val label: String, val emoji: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirQualityTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        NavItem("首頁",   "🏠"),
        NavItem("通知",   "🔔"),
        NavItem("通報",   "＋"),
        NavItem("AI顧問", "🤖"),
        NavItem("設定",   "👤"),
    )

    androidx.compose.material3.Scaffold(
        containerColor = BgMain,
        bottomBar = {
            AppBottomBar(navItems, selectedTab) { selectedTab = it }
        }
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> NotificationScreen()
                2 -> ReportScreen()
                3 -> AiHealthScreen()
                4 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun AppBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    Surface(color = NavBg, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                if (index == 2) {
                    // 中央大按鈕
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(OrangeMain)
                            .clickable { onItemClick(index) }
                    ) {
                        Text(item.emoji, fontSize = 22.sp, color = White)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onItemClick(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(item.emoji, fontSize = 20.sp)
                        Text(
                            item.label,
                            color = if (selectedIndex == index) NavSelected else NavUnselected,
                            fontSize = 10.sp,
                            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}