package com.example.airquality

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.BgMain
import com.example.airquality.ui.theme.OrangeMain
import com.example.airquality.ui.theme.TextDark
import com.example.airquality.ui.theme.TextGray

/**
 * 首次啟動時徵詢是否同意將健康屬性用於推播分眾。
 *
 * 健康屬性屬於特種個人資料，依個資法需取得明示同意，所以預設一律關閉，
 * 使用者按下「同意」之前不會有任何健康資料離開裝置。
 *
 * 文案刻意只講「用在哪裡、可以關掉」兩件事——完整的保存期間、刪除機制與
 * 第三方揭露寫在隱私權政策裡，由下方連結帶過去，彈窗不重複整段條文。
 *
 * 這個彈窗一輩子只跳一次（不論同意與否），之後改由設定頁的開關管理。
 */
@Composable
fun SensitiveAlertsConsentDialog(
    onDecision: (granted: Boolean) -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        // 點外面關掉視為「先不要」——預設關閉是安全的一邊，也不會一直重複詢問
        onDismissRequest = { onDecision(false) },
        containerColor = BgMain,
        title = {
            Text("要開啟敏感族群警示嗎？", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column {
                Text(
                    "空氣品質中等偏差（AQI 101-150）時優先提醒你。這個區間主要影響氣喘、" +
                        "心血管疾病、孕婦、長者與孩童。",
                    color = TextDark, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "需要將健康檔案中的年齡層與生理狀態傳送至伺服器，僅用於判斷要推播給誰。" +
                        "隨時可在「設定」關閉，關閉時一併刪除。",
                    color = TextGray, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "隱私權政策",
                    color = OrangeMain,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(true) }) {
                Text("同意", color = OrangeMain, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false) }) {
                Text("先不要", color = TextGray)
            }
        }
    )
}
