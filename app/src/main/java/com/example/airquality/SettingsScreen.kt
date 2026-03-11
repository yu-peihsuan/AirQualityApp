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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var selectedAgeGroup by remember { mutableStateOf("18-64歲") }
    val ageGroups = listOf("18歲以下", "18-64歲", "65歲以上")
    
    val healthConditions = listOf("氣喘", "心血管疾病", "懷孕中", "過敏", "呼吸道疾病")
    val selectedConditions = remember { mutableStateListOf<String>() }
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

            // ── 個人健康檔案 ───────────────────────────────────────
            SettingSection("個人健康檔案") {
                Text(
                    "此資料將用於 AI (RAG) 分析，提供個人化健康建議",
                    color = TextGray, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text("年齡層", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ageGroups.forEach { age ->
                        FilterChip(
                            selected = selectedAgeGroup == age,
                            onClick = { selectedAgeGroup = age },
                            label = { Text(age) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeMain.copy(alpha = 0.2f),
                                selectedLabelColor = OrangeMain
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("生理狀態與病史", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    healthConditions.forEach { condition ->
                        FilterChip(
                            selected = selectedConditions.contains(condition),
                            onClick = { 
                                if (selectedConditions.contains(condition)) selectedConditions.remove(condition) 
                                else selectedConditions.add(condition) 
                            },
                            label = { Text(condition) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeMain.copy(alpha = 0.2f),
                                selectedLabelColor = OrangeMain
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = otherText,
                    onValueChange = { otherText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("其他說明（如長期服用藥物等）", color = TextGray, fontSize = 16.sp) },
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
