package com.musab.niqdah.domain.auth

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String?) : AuthState
    data class ConfigurationNeeded(val message: String) : AuthState
}
