package com.musab.niqdah.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musab.niqdah.data.auth.AuthErrorMapper
import com.musab.niqdah.domain.auth.AuthRepository
import com.musab.niqdah.domain.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val authState: AuthState = AuthState.Loading,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { authState ->
                _uiState.update {
                    it.copy(authState = authState, isSubmitting = false)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        submit(email = email, password = password, isRegistration = false)
    }

    fun register(email: String, password: String, confirmPassword: String) {
        val validationError = when {
            password != confirmPassword -> "Passwords do not match."
            else -> validateCredentials(email, password)
        }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        submit(email = email, password = password, isRegistration = true)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun submit(email: String, password: String, isRegistration: Boolean) {
        val validationError = validateCredentials(email, password)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = if (isRegistration) {
                authRepository.register(email, password)
            } else {
                authRepository.signIn(email, password)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = AuthErrorMapper.toFriendlyMessage(error)
                        )
                    }
                }
        }
    }

    private fun validateCredentials(email: String, password: String): String? = when {
        email.isBlank() -> "Enter your email address."
        "@" !in email -> "Enter a valid email address."
        password.length < 6 -> "Use at least 6 characters for your password."
        else -> null
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
