package com.example.airquality

import android.content.Context
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
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.data.AppContainer
import com.example.airquality.data.AppLocale
import com.example.airquality.ui.theme.*

data class NavItem(@StringRes val labelRes: Int, val iconRes: Int?, val emoji: String? = null)

class MainActivity : ComponentActivity() {

    /**
     * 套用設定頁選的介面語言。設定頁改語言後會呼叫 `recreate()`，
     * Activity 重建時再走一次這裡，整個畫面就會換成新語系。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 取得 FCM Token 並儲存，供後端發送推播使用
        AppContainer.fcmToken.refreshToken()
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
    // 首次同意健康資料後，直接把使用者帶到設定頁並展開健康檔案——
    // 同意了卻沒填任何狀況的話，分眾推播等於沒作用
    var openHealthProfile by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavItem(R.string.nav_home,          R.drawable.home),
        NavItem(R.string.nav_ai,            R.drawable.chat_bot),
        NavItem(R.string.nav_report,        R.drawable.broadcast),
        NavItem(R.string.nav_notifications, R.drawable.alarm),
        NavItem(R.string.nav_settings,      R.drawable.user),
    )

    androidx.compose.material3.Scaffold(
        containerColor = BgMain,
        bottomBar = {
            AppBottomBar(navItems, selectedTab) { selectedTab = it }
        }
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onGoToHealthProfile = {
                        openHealthProfile = true
                        selectedTab = 4
                    }
                )
                1 -> AiHealthScreen()
                2 -> ReportScreen()
                3 -> NotificationScreen()
                4 -> SettingsScreen(
                    openHealthProfile = openHealthProfile,
                    onHealthProfileOpened = { openHealthProfile = false }
                )
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
                val label = stringResource(item.labelRes)
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
                                contentDescription = label,
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
                                contentDescription = label,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(bottom = 2.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (selectedIndex == index) NavSelected else NavUnselected
                                )
                            )
                        } ?: Text(item.emoji ?: "", fontSize = 23.sp)
                        
                        Text(
                            label,
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