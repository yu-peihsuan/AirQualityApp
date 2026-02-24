package com.example.airquality

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        HomeAppHeader(
            location = "臺北市中正區",
            date = "1月9日 週五",
            onBellClick = {}
        )

        // ── 主內容 ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f) // 讓主內容佔據剩餘的高度
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center // 內容靠中間集中
        ) {
            // ── 空氣品質標題 ──────────────────────────────────
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    buildAnnotatedString {
                        append("空氣")
                        withStyle(SpanStyle(color = OrangeMain)) { append("不健康") }
                    },
                    fontSize = 48.sp, // 字體再放大
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text("最後更新 18:00", color = TextGray, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(40.dp)) // 增加文字與臉的間距

            // ── 臉 + AQI 標籤 ───────────────────────────────
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AqiFace()

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(OrangeBadge)
                        .border(1.dp, OrangeMain.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 24.dp, vertical = 10.dp) // AQI標籤也稍微加大一點 padding
                ) {
                    Text("AQI 130 不健康", color = OrangeMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) // AQI 文字也稍微加大
                }
            }

            Spacer(Modifier.height(50.dp)) // 增加臉與下方按鈕的間距

            // ── 行動按鈕 ─────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionChip("😷", "外出戴口罩")
                ActionChip("🪟", "關閉門窗")
                ActionChip("💨", "空氣清淨機")
            }
        }
    }
}

// ── 臉 (Canvas 繪製) ──────────────────────────────────────────────────────

@Composable
fun AqiFace() {
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(FaceBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawFace() }
    }
}

fun DrawScope.drawFace() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = minOf(cx, cy) * 0.82f
    val browColor   = Color(0xFF5C3A1E)
    val eyeColor    = Color(0xFF3D2210)
    val maskColor   = Color(0xFFF8F8F8)
    val stripeColor = Color(0xFFDDDDDD)
    val bw = r * 0.065f

    // 眉毛（皺眉：外高內低）
    drawLine(browColor, Offset(cx - r * 0.58f, cy - r * 0.44f), Offset(cx - r * 0.12f, cy - r * 0.28f), bw, StrokeCap.Round)
    drawLine(browColor, Offset(cx + r * 0.12f, cy - r * 0.28f), Offset(cx + r * 0.58f, cy - r * 0.44f), bw, StrokeCap.Round)

    // 眼睛
    drawCircle(eyeColor, r * 0.085f, Offset(cx - r * 0.33f, cy - r * 0.08f))
    drawCircle(eyeColor, r * 0.085f, Offset(cx + r * 0.33f, cy - r * 0.08f))

    // 口罩
    val maskLeft = cx - r * 0.72f
    val maskTop  = cy + r * 0.08f
    val maskW    = r * 1.44f
    val maskH    = r * 0.70f
    drawRoundRect(maskColor, Offset(maskLeft, maskTop), Size(maskW, maskH), CornerRadius(r * 0.13f))

    // 口罩橫線
    val sx = maskLeft + maskW * 0.08f
    val sw = maskW * 0.84f
    val st = r * 0.028f
    drawLine(stripeColor, Offset(sx, maskTop + maskH * 0.38f), Offset(sx + sw, maskTop + maskH * 0.38f), st)
    drawLine(stripeColor, Offset(sx + maskW * 0.05f, maskTop + maskH * 0.68f), Offset(sx + sw - maskW * 0.05f, maskTop + maskH * 0.68f), st)
}

// ── 行動按鈕卡片 ─────────────────────────────────────────────────────────

@Composable
fun ActionChip(emoji: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(OrangeBadge.copy(alpha = 0.5f)) // 背景變得更透明一點
            .clickable {}
            .border(1.dp, OrangeMain.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) // 外框也變淡一點點
            .height(130.dp) // 給定固定的高度讓它長一點
            .width(90.dp)   // 把寬度稍微縮回來，讓三個框距比較開
    ) {
        Text(emoji, fontSize = 36.sp) // Emoji 變大
        Spacer(Modifier.height(12.dp))
        Text(label, color = OrangeMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) // 改為橘色字體
    }
}
