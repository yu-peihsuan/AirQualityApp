package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val savedAddress = sharedPreferences.getString("default_address", "") ?: ""
    
    var location    by remember { mutableStateOf(savedAddress) }
    var description by remember { mutableStateOf("") }
    var expanded    by remember { mutableStateOf(false) }
    var category    by remember { mutableStateOf("") }
    val categories  = listOf("工廠排放", "車輛廢氣", "露天燃燒", "建築揚塵", "火災煙霧", "其他")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(
            title = "事件通報"
        )

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
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor   = CardWhite,
                    unfocusedBorderColor    = DividerColor,
                    focusedBorderColor      = OrangeMain,
                ),
                singleLine = true
            )

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        location = savedAddress // Reset to user's saved address from settings
                        description = ""
                        category = ""
                        expanded = false
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消", color = TextMid) }

                Button(
                    onClick = {},
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextDark)
                ) {
                    Text("📤", fontSize = 19.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("立即通報", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextMid)
}
