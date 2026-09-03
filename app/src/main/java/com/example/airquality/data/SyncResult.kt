package com.example.airquality.data

/** 需要讓使用者知道成功或失敗的同步動作（例如把通知設定寫回後端）的結果。 */
sealed interface SyncResult {
    object Success : SyncResult
    data class Failure(val message: String) : SyncResult
}
