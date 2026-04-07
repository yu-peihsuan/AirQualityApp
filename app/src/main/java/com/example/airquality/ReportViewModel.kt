package com.example.airquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReportUiState {
    object Idle : ReportUiState()
    object Loading : ReportUiState()
    data class Success(val message: String, val isConfirmed: Boolean) : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}

class ReportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun submitReport(location: String, category: String, description: String) {
        if (location.isBlank() || category.isBlank() || description.isBlank()) {
            _uiState.value = ReportUiState.Error("請填寫所有欄位")
            return
        }
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            try {
                val response = RetrofitClient.apiService.submitReport(
                    ReportRequest(
                        location = location,
                        category = category,
                        description = description
                    )
                )
                val msg = if (response.isConfirmed) "已確認為污染事件，感謝您的通報！" else "回報已送出，感謝您的通報。"
                _uiState.value = ReportUiState.Success(msg, response.isConfirmed)
            } catch (e: Exception) {
                _uiState.value = ReportUiState.Error("送出失敗：${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _uiState.value = ReportUiState.Idle
    }
}
