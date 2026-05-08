package com.musab.niqdah.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    isSubmitting: Boolean,
    errorMessage: String?,
    onLogin: (email: String, password: String) -> Unit,
    onShowRegister: () -> Unit,
    onClearError: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Sign in to keep your money plan steady.",
        errorMessage = errorMessage,
        onClearError = onClearError
    ) {
        EmailField(value = email, onValueChange = { email = it }, enabled = !isSubmitting)
        PasswordField(value = password, onValueChange = { password = it }, enabled = !isSubmitting)
        SubmitButton(
            text = "Log in",
            isSubmitting = isSubmitting,
            onClick = { onLogin(email, password) }
        )
        AuthSwitchRow(
            prompt = "New to Niqdah?",
            action = "Create account",
            onClick = onShowRegister
        )
    }
}

@Composable
fun RegisterScreen(
    isSubmitting: Boolean,
    errorMessage: String?,
    onRegister: (email: String, password: String, confirmPassword: String) -> Unit,
    onShowLogin: () -> Unit,
    onClearError: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = "Create your account",
        subtitle = "Start with a secure sign-in for your finance plan.",
        errorMessage = errorMessage,
        onClearError = onClearError
    ) {
        EmailField(value = email, onValueChange = { email = it }, enabled = !isSubmitting)
        PasswordField(value = password, onValueChange = { password = it }, enabled = !isSubmitting)
        PasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            enabled = !isSubmitting,
            label = "Confirm password"
        )
        SubmitButton(
            text = "Register",
            isSubmitting = isSubmitting,
            onClick = { onRegister(email, password, confirmPassword) }
        )
        AuthSwitchRow(
            prompt = "Already have an account?",
            action = "Log in",
            onClick = onShowLogin
        )
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onClearError: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "Niqdah",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    AuthErrorMessage(message = errorMessage, onClear = onClearError)
                    content()
                }
            }
        }
    }
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String = "Password"
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isVisible) "Hide password" else "Show password"
                )
            }
        }
    )
}

@Composable
private fun SubmitButton(text: String, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        enabled = !isSubmitting,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        onClick = onClick
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text = text)
        }
    }
}

@Composable
private fun AuthSwitchRow(prompt: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = prompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) {
            Text(text = action)
        }
    }
}

@Composable
private fun AuthErrorMessage(message: String?, onClear: () -> Unit) {
    if (message == null) {
        Spacer(modifier = Modifier.height(0.dp))
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss error",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
