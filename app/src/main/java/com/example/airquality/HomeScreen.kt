package com.example.airquality

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
            .background(BgWarm)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── 頂部列 ──────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("臺北市中正區", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextDark)
                }
                Text("1月9日 週五", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp))
            }
            Text("🔔", fontSize = 22.sp, modifier = Modifier.clickable {})
        }

        Spacer(Modifier.height(28.dp))

        // ── 空氣品質標題 ──────────────────────────────────
        Text(
            buildAnnotatedString {
                append("空氣")
                withStyle(SpanStyle(color = OrangeMain)) { append("不健康") }
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text("最後更新 18:00", color = TextGray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(28.dp))

        // ── 臉 + AQI 標籤 ───────────────────────────────
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AqiFace()

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OrangeBadge)
                    .border(1.dp, OrangeMain.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("AQI 130 不健康", color = OrangeMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── 行動按鈕 ─────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionChip("😷", "外出戴口罩")
            ActionChip("🪟", "關閉門窗")
            ActionChip("💨", "空氣清淨機")
        }

        Spacer(Modifier.height(32.dp))
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
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .width(90.dp)
    ) {
        Text(emoji, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextMid, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}
