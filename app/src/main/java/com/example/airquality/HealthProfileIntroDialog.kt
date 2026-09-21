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
import androidx.compose.ui.res.stringResource
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
            Text(stringResource(R.string.intro_title), fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.intro_body_1),
                    color = TextDark, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.intro_body_2),
                    color = TextDark, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.intro_body_3),
                    color = TextGray, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.privacy_policy),
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
                Text(stringResource(R.string.intro_fill_now), color = OrangeMain, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.intro_later), color = TextGray)
            }
        }
    )
}
