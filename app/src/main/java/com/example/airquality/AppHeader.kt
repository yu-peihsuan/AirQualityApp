package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.airquality.ui.theme.*

/**
 * 標準 Header：標題 + 可選副標題 + 可選右側操作按鈕
 * 背景色 HeaderBg，底部有 HeaderBorder 分隔線，
 * 並自動處理 statusBarsPadding，讓背景貼到螢幕最頂端。
 */
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    centeredTitle: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
    ) {
        // 讓 HeaderBg 背景延伸到狀態列，內容從狀態列下方開始
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (centeredTitle) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = title,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = HeaderText,
                    textAlign = if (centeredTitle) TextAlign.Center else TextAlign.Start
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextGray,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        textAlign = if (centeredTitle) TextAlign.Center else TextAlign.Start
                    )
                }
            }
            // 右側操作按鈕插槽
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }

        HorizontalDivider(color = HeaderBorder, thickness = 1.dp)
    }
}

/**
 * 首頁專用 Header：左側顯示位置與日期，右側顯示地點切換鈕與通知鈴鐺（含 badge）
 */
@Composable
fun HomeAppHeader(
    location: String,
    date: String,
    currentLocationName: String = "GPS 定位",
    unreadCount: Int = 0,
    onLocationSwitchClick: () -> Unit = {},
    onBellClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(location, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = HeaderText)
                Text(date, color = TextGray, fontSize = 15.sp)
            }

            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.location_add),
                contentDescription = "常用地點",
                modifier = Modifier
                    .size(30.dp)
                    .clickable { onLocationSwitchClick() },
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OrangeMain)
            )
        }

        HorizontalDivider(color = HeaderBorder, thickness = 1.dp)
    }
}
