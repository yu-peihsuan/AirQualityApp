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
 * 首次啟動時說明本App為什麼需要健康狀況，並帶使用者去填寫。
 *
 * 這個彈窗**不代表任何同意**——它只做說明與引導。把健康屬性送到伺服器的
 * 明確同意放在健康檔案裡的開關上：使用者一邊看著自己填的病史、一邊決定
 * 要不要分享，比在開場對著一個還沒填任何資料的彈窗按「同意」有意義得多。
 *
 * 只跳一次，不論使用者選哪個。
 */
@Composable
fun HealthProfileIntroDialog(
    onDismiss: () -> Unit,
    onGoToHealthProfile: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Text("為什麼需要你的健康狀況？", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column {
                Text(
                    "同樣的空氣品質，對氣喘、心血管疾病、孕婦、長者與孩童的影響並不一樣。",
                    color = TextDark, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "填寫年齡層與身體狀況後，AI 顧問會依你的情況給個人化的防護建議，" +
                        "首頁的行動建議也會跟著調整。",
                    color = TextDark, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "這份資料預設只存在你的裝置上，可以隨時修改或清除。",
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
            TextButton(onClick = onGoToHealthProfile) {
                Text("去填寫", color = OrangeMain, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍後再說", color = TextGray)
            }
        }
    )
}
