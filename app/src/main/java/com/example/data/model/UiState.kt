package com.example.data.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class AiExchange(
    val id: String,
    val question: String,
    val answer: String,
    val isStreaming: Boolean = false
)

enum class AdminRole {
    NONE, OWNER, DELEGATE
}

enum class AdminAuthStatus {
    IDLE, CHECKING, AUTHORIZED, INVITED, FORBIDDEN, ERROR
}
