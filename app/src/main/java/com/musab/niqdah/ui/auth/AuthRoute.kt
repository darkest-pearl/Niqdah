package com.musab.niqdah.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun AuthRoute(
    uiState: AuthUiState,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, confirmPassword: String) -> Unit,
    onClearError: () -> Unit
) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.errorMessage,
            onRegister = onRegister,
            onShowLogin = {
                onClearError()
                showRegister = false
            },
            onClearError = onClearError
        )
    } else {
        LoginScreen(
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.errorMessage,
            onLogin = onLogin,
            onShowRegister = {
                onClearError()
                showRegister = true
            },
            onClearError = onClearError
        )
    }
}
