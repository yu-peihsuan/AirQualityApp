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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import android.content.Context
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

// ── 相對時間 ───────────────────────────────────────────────────────────────────

private fun relativeTime(context: Context, timestamp: String): String {
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
            m < 1    -> context.getString(R.string.time_just_now)
            m < 60   -> context.getString(R.string.time_minutes_ago, m.toInt())
            m < 1440 -> context.getString(R.string.time_hours_ago, (m / 60).toInt())
            else     -> context.getString(R.string.time_days_ago, (m / 1440).toInt())
        }
    } catch (e: Exception) {
        timestamp.take(10)
    }
}

// ── Section 樣式設定 ───────────────────────────────────────────────────────────

private data class SectionStyle(
    val icon: String,
    @StringRes val labelRes: Int,
    val accentColor: Color,
    val cardBg: Color
)

private fun sectionStyle(group: NotifGroup): SectionStyle = when (group) {
    NotifGroup.FIRE     -> SectionStyle("🔥", R.string.notif_group_fire,     Color(0xFFB71C1C), Color(0xFFFFF3F3))
    NotifGroup.REPORT   -> SectionStyle("👤", R.string.notif_group_report,   OrangeMain,        OrangeLight)
    NotifGroup.AQI      -> SectionStyle("🔴", R.string.notif_group_aqi,      AqiRed,            Color(0xFFFFF3F3))
    NotifGroup.FORECAST -> SectionStyle("📅", R.string.notif_group_forecast, Color(0xFF1565C0), Color(0xFFE3F2FD))
    NotifGroup.NEWS     -> SectionStyle("📰", R.string.notif_group_news,     TextGray,          CardWhite)
}

// ── 卡片內容萃取 ──────────────────────────────────────────────────────────────

private fun cardContent(context: Context, item: NewsRecord, group: NotifGroup): Pair<String, String> = when (group) {
    NotifGroup.FIRE   -> Pair(
        item.title.ifBlank { context.getString(R.string.notif_fire_fallback_title) },
        item.summary.ifBlank { item.region }
    )
    NotifGroup.REPORT -> {
        val label = context.getString(
            eventTypeLabelRes(item.structuredEvent?.eventType ?: item.category)
        )
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
            title = stringResource(R.string.notif_title),
            actions = {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.location),
                    contentDescription = stringResource(R.string.notif_map_cd),
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
                        Text(stringResource(R.string.notif_empty), color = TextMid, fontSize = 16.sp)
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
                                stringResource(R.string.notif_disclaimer),
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
            stringResource(style.labelRes),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = style.accentColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            pluralStringResource(R.plurals.notif_item_count, section.items.size, section.items.size),
            fontSize = 13.sp, color = TextGray
        )
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
    val context = LocalContext.current
    val (title, subtitle) = cardContent(context, item, group)
    val time = relativeTime(context, item.publishedAt)

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
