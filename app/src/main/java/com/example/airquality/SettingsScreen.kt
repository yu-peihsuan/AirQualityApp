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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("health_profile", Context.MODE_PRIVATE) }

    // ── 健康檔案 state（從 SharedPreferences 載入）────────────────────────
    val ageGroups       = listOf("18歲以下", "18-64歲", "65歲以上")
    val smokingOptions  = listOf("不吸菸", "有吸菸")
    val conditionList   = listOf("氣喘", "心血管疾病", "懷孕中", "過敏", "呼吸道疾病", "糖尿病", "腎臟病", "高血壓")

    var selectedAgeGroup  by remember { mutableStateOf(prefs.getString("health_age_group", "18-64歲") ?: "18-64歲") }
    var selectedSmoking   by remember { mutableStateOf(prefs.getString("health_smoking",   "不吸菸") ?: "不吸菸") }
    var otherText         by remember { mutableStateOf(prefs.getString("health_other",     "") ?: "") }

    val savedConditions = prefs.getString("health_conditions", "") ?: ""
    val selectedConditions = remember {
        mutableStateListOf<String>().also { list ->
            if (savedConditions.isNotEmpty()) list.addAll(savedConditions.split(","))
        }
    }

    // ── Snackbar ──────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgMain
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .background(BgMain)
        ) {
            // ── Header ────────────────────────────────────────────────────
            AppHeader(title = "設定")

            // ── 主內容 (scrollable) ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // ── 帳號管理 ───────────────────────────────────────────────
                SettingSection("帳號管理") {
                    SettingInfoRow("個人帳號",   "abc123@gmail.com")
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 2.dp))
                    SettingInfoRow("使用者名稱", "abc123")
                }

                Spacer(Modifier.height(20.dp))

                // ── 個人健康檔案 ───────────────────────────────────────────
                SettingSection("個人健康檔案") {
                    Text(
                        "此資料將用於 AI (RAG) 分析，提供個人化健康建議",
                        color = TextGray, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 1. 年齡層
                    HealthFieldLabel("年齡層")
                    SingleSelectChipRow(ageGroups, selectedAgeGroup) { selectedAgeGroup = it }

                    Spacer(Modifier.height(16.dp))

                    // 2. 生理狀態與病史（多選）
                    HealthFieldLabel("生理狀態與病史")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        conditionList.forEach { condition ->
                            FilterChip(
                                selected = selectedConditions.contains(condition),
                                onClick = {
                                    if (selectedConditions.contains(condition))
                                        selectedConditions.remove(condition)
                                    else
                                        selectedConditions.add(condition)
                                },
                                label = { Text(condition, fontSize = 13.sp) },
                                colors = healthChipColors()
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 3. 吸菸習慣
                    HealthFieldLabel("吸菸習慣")
                    SingleSelectChipRow(smokingOptions, selectedSmoking) { selectedSmoking = it }

                    Spacer(Modifier.height(16.dp))

                    // 4. 其他說明
                    HealthFieldLabel("其他說明")
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("如長期服用藥物、特殊病史等", color = TextGray, fontSize = 14.sp) },
                        colors = healthTextFieldColors(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(Modifier.height(20.dp))

                    // 8. 儲存按鈕
                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("health_age_group",  selectedAgeGroup)
                                .putString("health_conditions", selectedConditions.joinToString(","))
                                .putString("health_smoking",    selectedSmoking)
                                .putString("health_other",      otherText)
                                .apply()
                            scope.launch {
                                snackbarHostState.showSnackbar("✅ 健康檔案已儲存")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeMain)
                    ) {
                        Text("儲存健康檔案", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── 連結列 ────────────────────────────────────────────────
                SettingSection(title = null) {
                    SettingLinkRow("我的通報配置")
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow("通知設定")
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── 輔助 Composable ────────────────────────────────────────────────────────

@Composable
private fun HealthFieldLabel(text: String) {
    Text(
        text,
        color = TextDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleSelectChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick  = { onSelect(opt) },
                label    = { Text(opt, fontSize = 13.sp) },
                colors   = healthChipColors()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun healthChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = OrangeMain.copy(alpha = 0.18f),
    selectedLabelColor     = OrangeMain
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun healthTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = BgMain,
    focusedContainerColor   = BgMain,
    unfocusedBorderColor    = DividerColor,
    focusedBorderColor      = OrangeMain,
)

// ── 通用區塊元件 ───────────────────────────────────────────────────────────

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
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 17.sp)
        Text("›", color = TextGray, fontSize = 23.sp, fontWeight = FontWeight.Light)
    }
}
