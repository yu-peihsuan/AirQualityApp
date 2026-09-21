package com.example.airquality

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    reportViewModel: ReportViewModel = viewModel()
) {
    var location    by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expanded    by remember { mutableStateOf(false) }
    var category    by remember { mutableStateOf("") }
    // value 是送到後端的正規值（固定中文，回報資料庫與 LLM 結構化都按這個比對），
    // 介面上顯示的是 labelRes。
    val categories = REPORT_CATEGORIES

    val permissionDeniedMessage = stringResource(R.string.report_location_denied)

    val uiState by reportViewModel.uiState.collectAsState()
    val locationFetchState by reportViewModel.locationFetchState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = coroutineScope()

    // 定位權限請求：允許後自動抓取 GPS 位置；拒絕則提示改為手動輸入
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) reportViewModel.fetchAddressFromGps()
        else scope.launch { snackbarHostState.showSnackbar(permissionDeniedMessage) }
    }

    // 進入畫面時：若有手動選擇地點則用該座標填入，否則用 GPS。
    // 權限判斷交給 ViewModel／LocationRepository，未授權會回 PermissionRequired。
    LaunchedEffect(Unit) {
        val selectedCoords = AppLocationState.selectedLatLng.value
        if (selectedCoords != null) {
            reportViewModel.fetchAddressFromCoords(selectedCoords.first, selectedCoords.second)
        } else {
            reportViewModel.fetchAddressFromGps()
        }
    }

    // 定位完成 → 自動填入地址欄
    LaunchedEffect(locationFetchState) {
        when (val state = locationFetchState) {
            is LocationFetchState.Success -> {
                location = state.address
                reportViewModel.resetLocationFetchState()
            }
            is LocationFetchState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                reportViewModel.resetLocationFetchState()
            }
            LocationFetchState.PermissionRequired -> {
                reportViewModel.resetLocationFetchState()
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {}
        }
    }

    // 回應成功/失敗 → 顯示 Snackbar
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ReportUiState.Success -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                reportViewModel.fetchAddressFromGps()
                description = ""
                category = ""
                reportViewModel.resetState()
            }
            is ReportUiState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                reportViewModel.resetState()
            }
            else -> {}
        }
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
            // ── Header ──────────────────────────────────────────────────────────
            AppHeader(title = stringResource(R.string.report_title))

            // ── 主內容 (scrollable) ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── 位置 ──────────────────────────────────────────
                SectionLabel(stringResource(R.string.report_section_location))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text(stringResource(R.string.report_location_hint), color = TextGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = CardWhite,
                            focusedContainerColor   = CardWhite,
                            unfocusedBorderColor    = DividerColor,
                            focusedBorderColor      = OrangeMain,
                        ),
                        singleLine = true
                    )
                    val isFetchingLocation = locationFetchState is LocationFetchState.Loading
                    OutlinedButton(
                        onClick = { reportViewModel.fetchAddressFromGps() },
                        enabled = !isFetchingLocation,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeMain),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeMain)
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = OrangeMain,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.report_locate), fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 事件類別下拉 ──────────────────────────────────
                SectionLabel(stringResource(R.string.report_section_category))
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.value == category }
                            ?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.report_category_hint),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = CardWhite,
                            focusedContainerColor   = CardWhite,
                            unfocusedBorderColor    = DividerColor,
                            focusedBorderColor      = OrangeMain,
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.labelRes)) },
                                onClick = { category = item.value; expanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 簡易描述 ──────────────────────────────────────
                SectionLabel(stringResource(R.string.report_section_description))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.report_description_hint), color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = CardWhite,
                        focusedContainerColor   = CardWhite,
                        unfocusedBorderColor    = DividerColor,
                        focusedBorderColor      = OrangeMain,
                    )
                )

                Spacer(Modifier.height(32.dp))

                // ── 按鈕列 ────────────────────────────────────────
                val isLoading = uiState is ReportUiState.Loading
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            reportViewModel.fetchAddressFromGps()
                            description = ""
                            category = ""
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.action_cancel), color = TextMid) }

                    Button(
                        onClick = { reportViewModel.submitReport(location, category, description) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(2f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextDark)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CardWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("📤", fontSize = 19.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.report_submit), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun coroutineScope() = rememberCoroutineScope()

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextMid)
}

/**
 * 回報類別。[value] 是送給後端的正規值，一律維持中文——它是資料不是文案，
 * 翻譯它會讓已經存在資料庫裡的回報分類對不起來。
 */
data class ReportCategory(val value: String, @StringRes val labelRes: Int)

private val REPORT_CATEGORIES = listOf(
    ReportCategory("工廠排放", R.string.category_factory),
    ReportCategory("車輛廢氣", R.string.category_vehicle),
    ReportCategory("露天燃燒", R.string.category_open_burning),
    ReportCategory("建築揚塵", R.string.category_construction_dust),
    ReportCategory("火災煙霧", R.string.category_fire_smoke),
    ReportCategory("其他",     R.string.category_other),
)
