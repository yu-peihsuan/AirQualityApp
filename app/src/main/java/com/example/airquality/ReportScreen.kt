package com.example.airquality

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    reportViewModel: ReportViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val savedAddress = sharedPreferences.getString("default_address", "") ?: ""

    var location    by remember { mutableStateOf(savedAddress) }
    var description by remember { mutableStateOf("") }
    var expanded    by remember { mutableStateOf(false) }
    var category    by remember { mutableStateOf("") }
    val categories  = listOf("工廠排放", "車輛廢氣", "露天燃燒", "建築揚塵", "火災煙霧", "其他")

    val uiState by reportViewModel.uiState.collectAsState()
    val locationFetchState by reportViewModel.locationFetchState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = coroutineScope()

    // 定位權限請求
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 不論結果，提交時 ViewModel 會自行處理無權限的情況 */ }

    // 進入畫面時請求定位權限
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
            else -> {}
        }
    }

    // 回應成功/失敗 → 顯示 Snackbar
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ReportUiState.Success -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                location = savedAddress
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
                .padding(innerPadding)
                .background(BgMain)
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            AppHeader(title = "事件通報")

            // ── 主內容 (scrollable) ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── 位置 ──────────────────────────────────────────
                SectionLabel("位置")
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
                        placeholder = { Text("輸入地址或點擊定位", color = TextGray) },
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
                        onClick = { reportViewModel.fetchAddressFromGps(context) },
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
                            Text("📍 定位", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 事件類別下拉 ──────────────────────────────────
                SectionLabel("事件類別")
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category.ifEmpty { "請選擇事件類別" },
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
                                text = { Text(item) },
                                onClick = { category = item; expanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 簡易描述 ──────────────────────────────────────
                SectionLabel("簡易描述")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("請描述您觀察到的空氣品質問題…", color = TextGray) },
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
                            location = savedAddress
                            description = ""
                            category = ""
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消", color = TextMid) }

                    Button(
                        onClick = { reportViewModel.submitReport(context, location, category, description) },
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
                            Text("立即通報", fontWeight = FontWeight.SemiBold)
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
