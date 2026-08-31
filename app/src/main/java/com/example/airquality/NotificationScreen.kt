package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

// ── 相對時間 ───────────────────────────────────────────────────────────────────

private fun relativeTime(timestamp: String): String {
    if (timestamp.isBlank()) return ""
    return try {
        val clean = timestamp.trim()
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            .replace(Regex("Z$"), "+0000")
            .let { if (it.matches(Regex(".*[+-]\\d{4}$"))) it else "${it}+0800" }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Taipei")
        val date = sdf.parse(clean) ?: return timestamp.take(10)
        val diff = System.currentTimeMillis() - date.time
        val m = diff / 60_000
        when {
            m < 1    -> "剛剛"
            m < 60   -> "${m} 分鐘前"
            m < 1440 -> "${m / 60} 小時前"
            else     -> "${m / 1440} 天前"
        }
    } catch (e: Exception) {
        timestamp.take(10)
    }
}

// ── Section 樣式設定 ───────────────────────────────────────────────────────────

private data class SectionStyle(
    val icon: String,
    val label: String,
    val accentColor: Color,
    val cardBg: Color
)

@Composable
private fun sectionStyle(group: NotifGroup): SectionStyle = when (group) {
    NotifGroup.FIRE     -> SectionStyle("🔥", "火災警示",    Color(0xFFB71C1C), Color(0xFFFFF3F3))
    NotifGroup.REPORT   -> SectionStyle("👤", "民眾回報",    OrangeMain,        OrangeLight)
    NotifGroup.AQI      -> SectionStyle("🔴", "空氣品質警報", AqiRed,           Color(0xFFFFF3F3))
    NotifGroup.FORECAST -> SectionStyle("📅", "空品預報警示", Color(0xFF1565C0), Color(0xFFE3F2FD))
    NotifGroup.NEWS     -> SectionStyle("📰", "近期新聞",    TextGray,          CardWhite)
}

// ── 卡片內容萃取 ──────────────────────────────────────────────────────────────

private fun eventLabel(type: String?): String = when (type) {
    "fire"                -> "火災/濃煙"
    "chemical"            -> "化學異味"
    "dust"                -> "揚塵"
    "odor"                -> "異味"
    "vehicle"             -> "車輛廢氣"
    "factory"             -> "工廠排放"
    "general_air_quality" -> "空氣品質不良"
    else                  -> "污染回報"
}

private fun cardContent(item: NewsRecord, group: NotifGroup): Pair<String, String> = when (group) {
    NotifGroup.FIRE   -> Pair(
        item.title.ifBlank { "重大火災" },
        item.summary.ifBlank { item.region }
    )
    NotifGroup.REPORT -> {
        val label = eventLabel(item.structuredEvent?.eventType ?: item.category)
        // 不標示「已證實／未證實」（僅 AI 判斷、非官方核實，避免誤導）；
        // 底部免責聲明已說明僅供參考。標題僅顯示事件類型，完整描述放內容區、
        // 地址縮到底部小字（見 NotifItem）
        Pair(label, item.summary)
    }
    NotifGroup.AQI      -> Pair(
        item.title,
        item.summary.ifBlank { item.region }
    )
    NotifGroup.FORECAST -> {
        val hint = item.summary
            .split("\n", "。")
            .map { it.trim() }
            .firstOrNull { it.contains("等級") && it.length > 10 }
            ?.take(60)
            ?: item.summary.take(60)
        Pair(item.title, hint.ifBlank { item.region })
    }
    NotifGroup.NEWS     -> Pair(
        item.title,
        item.region.ifBlank { item.summary.take(30) }
    )
}

// ── NotificationScreen ────────────────────────────────────────────────────────

@Composable
fun NotificationScreen(
    homeViewModel: HomeViewModel = viewModel(),
    viewModel: NotificationViewModel = viewModel()
) {
    val uiState     by viewModel.uiState.collectAsState()
    val homeUiState by homeViewModel.uiState.collectAsState()
    var showMap     by remember { mutableStateOf(false) }

    val county = (homeUiState as? AqiUiState.Success)?.nearestRecord?.county
    LaunchedEffect(county) { viewModel.fetchNotifications(county) }

    if (showMap) {
        MapScreen(onBack = { showMap = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        AppHeader(
            title = "通知中心",
            actions = {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.location),
                    contentDescription = "熱點地圖",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { showMap = true },
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OrangeMain)
                )
            }
        )

        when (uiState) {
            is NotificationUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeMain)
                }
            }
            is NotificationUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text((uiState as NotificationUiState.Error).message, color = RedText)
                }
            }
            is NotificationUiState.Success -> {
                val sections = (uiState as NotificationUiState.Success).sections
                if (sections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("目前沒有與您所在區域相關的通知。", color = TextMid, fontSize = 16.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        sections.forEach { section ->
                            SectionBlock(section = section)
                            Spacer(Modifier.height(20.dp))
                        }
                        // 民眾回報免責聲明（僅在列表含回報時顯示）
                        if (sections.any { it.group == NotifGroup.REPORT }) {
                            Text(
                                "民眾回報為使用者提供之資訊，未經證實前僅供參考。",
                                fontSize = 11.sp,
                                color = TextGray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── Section 區塊 ──────────────────────────────────────────────────────────────

@Composable
private fun SectionBlock(section: NotificationSection) {
    val style = sectionStyle(section.group)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(style.icon, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            style.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = style.accentColor
        )
        Spacer(Modifier.width(6.dp))
        Text("${section.items.size} 件", fontSize = 13.sp, color = TextGray)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        section.items.forEach { item ->
            NotifItem(
                item        = item,
                group       = section.group,
                accentColor = style.accentColor,
                cardBg      = style.cardBg
            )
        }
    }
}

// ── 單筆通知卡片 ──────────────────────────────────────────────────────────────

@Composable
private fun NotifItem(
    item: NewsRecord,
    group: NotifGroup,
    accentColor: Color,
    cardBg: Color
) {
    val (title, subtitle) = cardContent(item, group)
    val time = relativeTime(item.publishedAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
    ) {
        // 左側色條
        Box(
            modifier = Modifier
                .width(4.dp)
                .defaultMinSize(minHeight = 56.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text     = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = TextDark,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(text = time, fontSize = 12.sp, color = TextGray)
                }
            }

            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = subtitle,
                    fontSize = 13.sp,
                    color    = TextMid,
                    // 民眾回報顯示完整描述；其他類別最多 3 行，
                    // 避免小螢幕或字體放大時內容被截成單行吃字
                    maxLines = if (group == NotifGroup.REPORT) 10 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 民眾回報：地址縮到底部小灰字，次要呈現
            if (group == NotifGroup.REPORT && item.region.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = "📍 ${item.region}",
                    fontSize = 11.sp,
                    color    = TextGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
