package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeaderText
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextGray,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
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
 * 首頁專用 Header：左側顯示位置與日期，右側顯示鈴鐺
 */
@Composable
fun HomeAppHeader(
    location: String,
    date: String,
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.location),
                        contentDescription = "Location",
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(HeaderText)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(location, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = HeaderText) // Location text size
                }
                Text(date, color = TextGray, fontSize = 15.sp, modifier = Modifier.padding(start = 26.dp)) // 日期字體大小調整
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.bell),
                contentDescription = "Bell",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBellClick() },
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(HeaderText)
            )
        }

        HorizontalDivider(color = HeaderBorder, thickness = 1.dp)
    }
}
