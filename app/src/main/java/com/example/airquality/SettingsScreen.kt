package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

@Composable
fun SettingsScreen() {
    var isAsthma   by remember { mutableStateOf(true) }
    var isHeart    by remember { mutableStateOf(false) }
    var isPregnant by remember { mutableStateOf(false) }
    var isAllergy  by remember { mutableStateOf(false) }
    var otherText  by remember { mutableStateOf("") }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var defaultAddress by remember { mutableStateOf(sharedPreferences.getString("default_address", "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(title = "設定")

        // ── 主內容 (scrollable) ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── 帳號管理 ───────────────────────────────────────
            SettingSection("帳號管理") {
                SettingInfoRow("個人帳號",   "abc123@gmail.com")
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 2.dp))
                SettingInfoRow("使用者名稱", "abc123")
            }

            Spacer(Modifier.height(20.dp))

            // ── 健康敏感度 ─────────────────────────────────────
            SettingSection("健康敏感度") {
                Text(
                    "AI 於你的偏好自動優化風險通知",
                    color = TextGray, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                HealthCheckRow("氣喘",   isAsthma)   { isAsthma   = it }
                HealthCheckRow("心臟病", isHeart)    { isHeart    = it }
                HealthCheckRow("懷孕中", isPregnant) { isPregnant = it }
                HealthCheckRow("過敏",   isAllergy)  { isAllergy  = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otherText,
                    onValueChange = { otherText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("其他（請說明）", color = TextGray, fontSize = 16.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BgMain,
                        focusedContainerColor   = BgMain,
                        unfocusedBorderColor    = DividerColor,
                        focusedBorderColor      = OrangeMain,
                    ),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 通報設定 ──────────────────────────────────────
            SettingSection("通報設定") {
                Text(
                    "設定後將自動帶入事件通報頁面",
                    color = TextGray, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = defaultAddress,
                    onValueChange = { 
                        defaultAddress = it
                        sharedPreferences.edit().putString("default_address", it).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("請輸入預設地址", color = TextGray, fontSize = 16.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BgMain,
                        focusedContainerColor   = BgMain,
                        unfocusedBorderColor    = DividerColor,
                        focusedBorderColor      = OrangeMain,
                    ),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 連結列 ────────────────────────────────────────
            SettingSection(title = null) {
                SettingLinkRow("我的通報配置")
                HorizontalDivider(color = DividerColor)
                SettingLinkRow("通知設定")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 輔助元件 ──────────────────────────────────────────────────────────────

@Composable
fun SettingSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextMid)
        Spacer(Modifier.height(8.dp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun SettingInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMid,  fontSize = 17.sp)
        Text(value, color = TextGray, fontSize = 17.sp)
    }
}

@Composable
fun HealthCheckRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 17.sp)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheck,
            colors = CheckboxDefaults.colors(checkedColor = OrangeMain)
        )
    }
}

@Composable
fun SettingLinkRow(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 17.sp)
        Text("›", color = TextGray, fontSize = 23.sp, fontWeight = FontWeight.Light)
    }
}
