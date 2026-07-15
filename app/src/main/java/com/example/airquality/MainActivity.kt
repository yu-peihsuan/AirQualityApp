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

data class NavItem(val label: String, val iconRes: Int?, val emoji: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 取得 FCM Token 並儲存，供後端發送推播使用
        TokenManager.fetchAndSaveToken(this)
        setContent {
            AirQualityTheme {
                AppEntry()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // App 退到背景時才切換桌面圖示（前景切換會把使用者踢回桌面）
        AppIconManager.applyPending(this)
    }
}

@Composable
fun AppEntry() {
    MainScreen()
}

@Composable
fun MainScreen(initialTab: Int = 0) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val navItems = listOf(
        NavItem("首頁",   R.drawable.home),
        NavItem("AI顧問", R.drawable.chat_bot),
        NavItem("通報",   R.drawable.broadcast),
        NavItem("通知",   R.drawable.alarm),
        NavItem("設定",   R.drawable.user),
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
                1 -> AiHealthScreen()
                2 -> ReportScreen()
                3 -> NotificationScreen()
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
                        item.iconRes?.let {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = it),
                                contentDescription = item.label,
                                modifier = Modifier.size(28.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(White)
                            )
                        } ?: Text(item.emoji ?: "＋", fontSize = 25.sp, color = White)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onItemClick(index) }
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        item.iconRes?.let {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = it),
                                contentDescription = item.label,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(bottom = 2.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (selectedIndex == index) NavSelected else NavUnselected
                                )
                            )
                        } ?: Text(item.emoji ?: "", fontSize = 23.sp)
                        
                        Text(
                            item.label,
                            color = if (selectedIndex == index) NavSelected else NavUnselected,
                            fontSize = 13.sp,
                            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}