package com.example.airquality.ui.theme

import androidx.compose.ui.graphics.Color

// ── 主要背景 ──────────────────────────────────────
val BgMain        = Color(0xFFFFFFFF)   // 頁面主背景
val BgWarm        = Color(0xFFF5F0E8)   // 舊暖色背景（保留相容）
val CardWhite     = Color(0xFFFFFFFF)
val InputBg       = Color(0xFFF0EBE3)

// ── 文字 ─────────────────────────────────────────
val TextDark      = Color(0xFF1A1A1A)
val TextMid       = Color(0xFF555555)
val TextGray      = Color(0xFF999999)
val HeaderText    = Color(0xFF333333)   // Header 文字

// ── 主色 ─────────────────────────────────────────
val OrangeMain    = Color(0xFFD4663A)
val OrangeLight   = Color(0xFFFFF3ED)
val OrangeBadge   = Color(0xFFFFE8D6)

// ── 狀態色卡 ─────────────────────────────────────
val RedCard       = Color(0xFFFFF0F0)
val RedText       = Color(0xFFD32F2F)
val GreenCard     = Color(0xFFF0FFF4)
val GreenText     = Color(0xFF2D7A4F)

// ── 其他 ─────────────────────────────────────────
val FaceBg        = Color(0xFFE8D0A8)
val DividerColor  = Color(0xFFEEEEEE)
val White         = Color(0xFFFFFFFF)
val BubbleRight   = Color(0xFFFFF3ED)
val BubbleLeft    = Color(0xFFFFFFFF)

// ── Header ───────────────────────────────────────
val HeaderBg      = Color(0xFFF9F8F8)   // Header 背景
val HeaderBorder  = Color(0xFFEAE8E8)   // Header 底部線

// ── 底部導覽列 ────────────────────────────────────
val NavBg         = Color(0xFFF9F8F8)   // 底部列背景
val NavSelected   = Color(0xFF444444)   // 選中分頁文字
val NavUnselected = Color(0xFF999999)   // 未選中分頁文字

// ── AQI 標準顏色 ─────────────────────────────────────
val AqiGreen = Color(0xFF7EC366)   // 良好
val AqiYellow = Color(0xFFFBC853)  // 普通
val AqiOrange = Color(0xFFEA580C)  // 對敏感族群不良
val AqiRed = Color(0xFFCC0033)     // 對所有族群不良
val AqiPurple = Color(0xFF660099)  // 非常不良
val AqiMaroon = Color(0xFF7E0023)  // 危害

fun getAqiColor(status: String): Color {
    return when {
        status.contains("良好") -> AqiGreen
        status.contains("普通") -> AqiYellow
        status.contains("對敏感族群不健康") -> AqiOrange
        status.contains("對所有族群不健康") -> AqiRed
        status.contains("非常不健康") -> AqiPurple
        status.contains("危害") -> AqiMaroon
        else -> AqiGreen // Fallback
    }
}


