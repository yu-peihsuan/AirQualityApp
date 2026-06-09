package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun doLogin() {
        val trimmed = email.trim().lowercase()
        if (!trimmed.contains("@") || !trimmed.contains(".")) {
            errorMsg = "請輸入有效的 Email 格式"
            return
        }
        keyboard?.hide()
        isLoading = true
        errorMsg = ""
        scope.launch {
            val result = UserManager.loginAndSync(context, trimmed)
            isLoading = false
            if (result.isSuccess) {
                onLoginSuccess()
            } else {
                errorMsg = "連線失敗，請確認網路後再試"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 標題
            Text(
                "空氣品質監測",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                "輸入你的 Email 開始使用\n資料將跟著帳號走",
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(8.dp))

            // Email 輸入框
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                placeholder = { Text("example@gmail.com", color = TextGray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = CardWhite,
                    focusedContainerColor   = CardWhite,
                    unfocusedBorderColor    = DividerColor,
                    focusedBorderColor      = OrangeMain,
                    errorBorderColor        = RedText,
                ),
                isError = errorMsg.isNotEmpty()
            )

            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = RedText, fontSize = 13.sp)
            }

            // 登入按鈕
            Button(
                onClick = { doLogin() },
                enabled = !isLoading && email.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeMain)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("開始使用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
