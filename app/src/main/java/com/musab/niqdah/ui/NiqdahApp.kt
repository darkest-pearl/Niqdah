package com.musab.niqdah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musab.niqdah.data.ai.FirebaseAiChatRepository
import com.musab.niqdah.data.auth.FirebaseAuthRepository
import com.musab.niqdah.data.finance.FirebaseFinanceRepository
import com.musab.niqdah.domain.auth.AuthState
import com.musab.niqdah.ui.ai.AiChatViewModel
import com.musab.niqdah.ui.auth.AuthRoute
import com.musab.niqdah.ui.auth.AuthViewModel
import com.musab.niqdah.ui.finance.FinanceViewModel
import com.musab.niqdah.ui.shell.MainShell

@Composable
fun NiqdahApp(openTransactionsRequest: Int = 0) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { FirebaseAuthRepository(context) }
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(authRepository)
    )
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (val authState = uiState.authState) {
        AuthState.Loading -> LoadingScreen()
        is AuthState.ConfigurationNeeded -> ConfigurationNeededScreen(authState.message)
        AuthState.SignedOut -> AuthRoute(
            uiState = uiState,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onClearError = authViewModel::clearError
        )
        is AuthState.SignedIn -> {
            val financeRepository = remember(context, authState.uid) {
                FirebaseFinanceRepository(context, authState.uid)
            }
            val financeViewModel: FinanceViewModel = viewModel(
                key = "finance-${authState.uid}",
                factory = FinanceViewModel.Factory(financeRepository)
            )
            val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()
            val aiChatRepository = remember(context) { FirebaseAiChatRepository(context) }
            val aiChatViewModel: AiChatViewModel = viewModel(
                key = "ai-chat-${authState.uid}",
                factory = AiChatViewModel.Factory(aiChatRepository)
            )
            val aiChatUiState by aiChatViewModel.uiState.collectAsStateWithLifecycle()

            MainShell(
                userEmail = authState.email,
                financeUiState = financeUiState,
                financeViewModel = financeViewModel,
                aiChatUiState = aiChatUiState,
                aiChatViewModel = aiChatViewModel,
                openTransactionsRequest = openTransactionsRequest,
                onLogout = authViewModel::signOut
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(text = "Checking your Niqdah session")
            }
        }
    }
}

@Composable
private fun ConfigurationNeededScreen(message: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = "Niqdah",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
