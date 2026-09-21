package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.data.AppLanguage
import com.example.airquality.data.FavoriteLocation
import com.example.airquality.data.HealthOptions
import com.example.airquality.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // 由首次同意流程帶進來：進場時直接展開健康檔案對話框
    openHealthProfile: Boolean = false,
    onHealthProfileOpened: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    // ── 通知設定 state ────────────────────────────────────────────────────
    val dailyNotificationEnabled by viewModel.dailyEnabled.collectAsState()
    val dailyHour   by viewModel.dailyHour.collectAsState()
    val dailyMinute by viewModel.dailyMinute.collectAsState()
    val sensitiveAlertsEnabled by viewModel.sensitiveAlertsEnabled.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    // ── 健康檔案 state（編輯中的暫存值，按下儲存才寫回）────────────────────
    val savedAgeGroup   by viewModel.ageGroup.collectAsState()
    val savedConditions by viewModel.conditions.collectAsState()
    val savedOtherNotes by viewModel.otherNotes.collectAsState()

    var selectedAgeGroup by remember(savedAgeGroup) { mutableStateOf(savedAgeGroup) }
    var otherText        by remember(savedOtherNotes) { mutableStateOf(savedOtherNotes) }
    val selectedConditions = remember(savedConditions) {
        mutableStateListOf<String>().also { it.addAll(savedConditions) }
    }
    var showHealthDialog by remember { mutableStateOf(false) }

    // 從同意彈窗導過來時自動展開健康檔案。回報一次就把旗標清掉，
    // 否則使用者關掉對話框後切回首頁再回來，它又會自己跳出來。
    LaunchedEffect(openHealthProfile) {
        if (openHealthProfile) {
            showHealthDialog = true
            onHealthProfileOpened()
        }
    }

    // ── 常用地點 state ────────────────────────────────────────────────────────
    val savedFavorites by viewModel.favorites.collectAsState()
    val favLocations = remember(savedFavorites) {
        mutableStateListOf<FavoriteLocation>().also { it.addAll(savedFavorites) }
    }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editName     by remember { mutableStateOf("") }
    var editAddress  by remember { mutableStateOf("") }

    // ── 使用說明 ──────────────────────────────────────────────────────────
    var showGuideDialog by remember { mutableStateOf(false) }

    // 介面語言
    val language by viewModel.language.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    // ── Snackbar ──────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }

    // 設定寫回後端成功或失敗都要讓使用者知道（原本失敗是靜默的）
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

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
            AppHeader(title = stringResource(R.string.settings_title))

            // ── 主內容（可捲動：小螢幕或字體放大時，下方項目才不會被切掉）──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // ── 通知設定（常用，直接顯示）───────────────────────────
                SettingSection(stringResource(R.string.settings_section_notifications)) {
                    Text(
                        stringResource(R.string.settings_daily_description),
                        color = TextGray, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_daily_switch),
                        checked = dailyNotificationEnabled,
                        onCheckedChange = { viewModel.setDailyEnabled(it) }
                    )
                    if (dailyNotificationEnabled) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_notify_time), color = TextDark, fontSize = 16.sp)
                            Text(
                                "%02d:%02d".format(dailyHour, dailyMinute),
                                color = OrangeMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.sendTestNotification() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeMain)
                ) {
                    Text(stringResource(R.string.settings_test_notification), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(20.dp))

                // ── 其他設定：點開才顯示詳細內容 ─────────────────────────
                SettingSection(title = null) {
                    SettingLinkRow(stringResource(R.string.settings_health_profile)) { showHealthDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow(stringResource(R.string.settings_favorites)) { showLocationsDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow(
                        label = stringResource(R.string.settings_language),
                        value = stringResource(language.labelRes)
                    ) { showLanguageDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow(stringResource(R.string.settings_user_guide)) { showGuideDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow(stringResource(R.string.privacy_policy)) {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(PRIVACY_URL)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── 個人健康檔案 彈跳視窗 ───────────────────────────────────────────────
    if (showHealthDialog) {
        HealthProfileDialog(
            sensitiveAlertsEnabled = sensitiveAlertsEnabled,
            onSensitiveAlertsChange = { viewModel.setSensitiveAlertsEnabled(it) },
            selectedAgeGroup   = selectedAgeGroup,
            onAgeGroupChange   = { selectedAgeGroup = it },
            selectedConditions = selectedConditions,
            otherText          = otherText,
            onOtherTextChange  = { otherText = it },
            onDismiss          = { showHealthDialog = false },
            onSave = {
                viewModel.saveHealthProfile(
                    selectedAgeGroup, selectedConditions.toList(), otherText
                )
                showHealthDialog = false
            }
        )
    }

    // ── 常用地點 彈跳視窗 ─────────────────────────────────────────────────
    if (showLocationsDialog) {
        LocationsDialog(
            favLocations = favLocations,
            onDismiss = { showLocationsDialog = false },
            onAddClick = {
                editingIndex = favLocations.size
                editName    = ""
                editAddress = ""
            },
            onItemClick = { idx ->
                editingIndex = idx
                editName    = favLocations[idx].name
                editAddress = favLocations[idx].address
            }
        )
    }

    // ── 常用地點新增／編輯 彈跳視窗 ──────────────────────────────────────
    if (editingIndex != null) {
        val isNew = editingIndex == favLocations.size
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            containerColor = BgMain,
            title = {
                Text(
                    stringResource(
                        if (isNew) R.string.location_add_title else R.string.location_edit_title
                    ),
                    fontWeight = FontWeight.Bold, color = TextDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.location_name_label)) },
                        placeholder = {
                            Text(stringResource(R.string.location_name_hint), color = TextGray, fontSize = 13.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = healthTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text(stringResource(R.string.location_address_label)) },
                        placeholder = {
                            Text(stringResource(R.string.location_address_hint), color = TextGray, fontSize = 13.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = healthTextFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val i = editingIndex ?: return@TextButton
                    val newFav = FavoriteLocation(editName.trim(), editAddress.trim())
                    if (isNew) favLocations.add(newFav)
                    else favLocations[i] = newFav
                    viewModel.saveFavorites(favLocations.toList())
                    editingIndex = null
                }) { Text(stringResource(R.string.action_save), color = OrangeMain) }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = {
                            val i = editingIndex ?: return@TextButton
                            favLocations.removeAt(i)
                            viewModel.saveFavorites(favLocations.toList())
                            editingIndex = null
                        }) { Text(stringResource(R.string.action_delete), color = RedText) }
                    }
                    TextButton(onClick = { editingIndex = null }) {
                        Text(stringResource(R.string.action_cancel), color = TextGray)
                    }
                }
            }
        )
    }

    if (showTimePicker) {
        DailyNotificationTimePickerDialog(
            initialHour = dailyHour,
            initialMinute = dailyMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setDailyTime(hour, minute)
                showTimePicker = false
            }
        )
    }

    // ── 使用說明 彈跳視窗 ──────────────────────────────────────────────────
    if (showGuideDialog) {
        InfoDialog(
            stringResource(R.string.settings_user_guide),
            stringResource(R.string.user_guide_text)
        ) { showGuideDialog = false }
    }

    // ── 語言選單 ───────────────────────────────────────────────
    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onDismiss = { showLanguageDialog = false },
            onSelect = { picked ->
                showLanguageDialog = false
                if (picked != language) {
                    viewModel.setLanguage(picked)
                    // 資源要重新解析才會換語系，而 Context 是在
                    // MainActivity.attachBaseContext 時包的，所以要整個 Activity 重建。
                    (context as? Activity)?.recreate()
                }
            }
        )
    }
}

// ── 語言選單 Dialog ─────────────────────────────────────────

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Text(
                stringResource(R.string.settings_language_dialog_title),
                fontWeight = FontWeight.Bold, color = TextDark
            )
        },
        text = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = OrangeMain)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(option.labelRes),
                            color = TextDark, fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextGray)
            }
        }
    )
}

// ── 可捲動說明對話框（使用說明／隱私權政策共用）──────────────────────────────────
@Composable
private fun InfoDialog(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(content, color = TextDark, fontSize = 13.sp, lineHeight = 21.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = OrangeMain) }
        }
    )
}

// 隱私權政策完整版網頁（設定頁與首次同意彈窗都會連到這裡）
// 正式站台為 Vercel；GitHub Pages 那份路徑已失效，不要改回去
internal const val PRIVACY_URL =
    "https://air-quality-privacy-policy.vercel.app/"

// ── 個人健康檔案 Dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HealthProfileDialog(
    sensitiveAlertsEnabled: Boolean,
    onSensitiveAlertsChange: (Boolean) -> Unit,
    selectedAgeGroup: String,
    onAgeGroupChange: (String) -> Unit,
    selectedConditions: MutableList<String>,
    otherText: String,
    onOtherTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val ageGroups     = HealthOptions.ageGroups
    val conditionList = HealthOptions.conditions

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Text(stringResource(R.string.health_dialog_title), fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            // 可捲動：小螢幕或系統字體放大時，內容超出對話框高度仍可完整檢視
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.health_dialog_description),
                    color = TextGray, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HealthFieldLabel(stringResource(R.string.health_field_age))
                SingleSelectChipRow(ageGroups, selectedAgeGroup, onAgeGroupChange)

                Spacer(Modifier.height(16.dp))

                HealthFieldLabel(stringResource(R.string.health_field_conditions))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    conditionList.forEach { condition ->
                        FilterChip(
                            selected = selectedConditions.contains(condition.value),
                            onClick = {
                                if (selectedConditions.contains(condition.value))
                                    selectedConditions.remove(condition.value)
                                else
                                    selectedConditions.add(condition.value)
                            },
                            label = { Text(stringResource(condition.labelRes), fontSize = 13.sp) },
                            colors = healthChipColors()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                HealthFieldLabel(stringResource(R.string.health_field_other))
                OutlinedTextField(
                    value = otherText,
                    onValueChange = onOtherTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = {
                        Text(stringResource(R.string.health_other_hint), color = TextGray, fontSize = 14.sp)
                    },
                    colors = healthTextFieldColors(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(12.dp))

                // 把健康屬性送到伺服器的明確同意就在這裡——使用者一邊看著自己
                // 填的病史、一邊決定要不要分享，比在開場的彈窗上按同意有意義。
                HealthFieldLabel(stringResource(R.string.health_field_sensitive_alerts))
                Text(
                    stringResource(R.string.health_sensitive_description),
                    color = TextGray, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                SettingSwitchRow(
                    label = stringResource(
                        if (sensitiveAlertsEnabled) R.string.health_switch_on else R.string.health_switch_off
                    ),
                    checked = sensitiveAlertsEnabled,
                    onCheckedChange = onSensitiveAlertsChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save), color = OrangeMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextGray) }
        }
    )
}

// ── 常用地點 Dialog ──────────────────────────────────────────────────────────

@Composable
private fun LocationsDialog(
    favLocations: List<FavoriteLocation>,
    onDismiss: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_favorites), fontWeight = FontWeight.Bold, color = TextDark)
                TextButton(onClick = onAddClick) {
                    Text(stringResource(R.string.action_add), color = OrangeMain, fontSize = 14.sp)
                }
            }
        },
        text = {
            val filled = favLocations.filter { it.name.isNotEmpty() && it.address.isNotEmpty() }
            if (filled.isEmpty()) {
                Text(
                    stringResource(R.string.favorites_empty),
                    color = TextGray, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filled.forEachIndexed { idx, fav ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(favLocations.indexOf(fav)) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fav.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextDark)
                                Text(fav.address, fontSize = 12.sp, color = TextGray)
                            }
                            Text("›", fontSize = 18.sp, color = TextGray)
                        }
                        if (idx < filled.size - 1) HorizontalDivider(color = DividerColor)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = OrangeMain) }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SingleSelectChipRow(
    options: List<com.example.airquality.data.HealthOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // FlowRow：小螢幕或字體放大時放不下就換行，避免 chip 被壓成直排
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt.value,
                onClick  = { onSelect(opt.value) },
                label    = { Text(stringResource(opt.labelRes), fontSize = 13.sp) },
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
private fun DailyNotificationTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Text(stringResource(R.string.time_picker_title), fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.action_confirm), color = OrangeMain)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextGray) }
        }
    )
}

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

/** [value] 是右側的現値（例如語言列要顯示目前選的語言），不給就只有箭頭。 */
@Composable
fun SettingLinkRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, color = TextGray, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text("›", color = TextGray, fontSize = 23.sp, fontWeight = FontWeight.Light)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = OrangeMain,
            )
        )
    }
}
