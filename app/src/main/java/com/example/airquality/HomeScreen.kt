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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*

@Composable
fun HomeScreen(
    // 注入 ViewModel 來管理狀態
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val defaultAddress = sharedPreferences.getString("default_address", "") ?: ""

    // 監聽來自 ViewModel 的狀態變化
    val uiState by viewModel.uiState.collectAsState()

    // 當 defaultAddress 改變或第一次進入時，載入空氣品質資料
    LaunchedEffect(defaultAddress) {
        viewModel.fetchAirQuality(context, defaultAddress)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        val displayRegion = if (uiState is AqiUiState.Success) {
            (uiState as AqiUiState.Success).displayRegion
        } else {
            defaultAddress.takeIf { it.isNotBlank() } ?: "臺北市中正區"
        }

        HomeAppHeader(
            location = displayRegion,
            date = "1月9日 週五",
            onBellClick = {}
        )

        // ── 主內容 ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f) // 讓主內容佔據剩餘的高度
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center, // 內容靠中間集中
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 根據 API 連線狀態顯示不同畫面
            when (uiState) {
                is AqiUiState.Loading -> {
                    CircularProgressIndicator(color = OrangeMain)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在取得空氣品質資料...", color = TextGray)
                }
                
                is AqiUiState.Error -> {
                    Text(
                        text = (uiState as AqiUiState.Error).message, 
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
                
                is AqiUiState.Success -> {
                    val successState = uiState as AqiUiState.Success
                    val nearestRecord = successState.nearestRecord
                    
                    val aqiValue = nearestRecord.aqi
                    val aqiStatus = nearestRecord.status
                    val pm25 = nearestRecord.pm25
                    val sitename = nearestRecord.sitename
                    
                    // ── 空氣品質標題 ──────────────────────────────────
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            buildAnnotatedString {
                                append("空氣")
                                withStyle(SpanStyle(color = OrangeMain)) { append(aqiStatus) }
                            },
                            fontSize = 48.sp, // 字體再放大
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        // 顯示最近的測站名稱與對應 PM2.5
                        Text("最近測站: $sitename | PM2.5: $pm25", color = TextGray, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
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
                            Text("AQI $aqiValue $aqiStatus", color = OrangeMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) // AQI 文字也稍微加大
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