package com.icloudandroid.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.icloudandroid.R
import com.icloudandroid.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onSignedIn: (photosServiceUrl: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val signedIn = state as? AuthState.SignedIn
        if (signedIn != null) onSignedIn(signedIn.photosServiceUrl)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is AuthState.AwaitingTwoFactorCode,
                is AuthState.VerifyingTwoFactorCode -> {
                    TwoFactorForm(
                        isBusy = s is AuthState.VerifyingTwoFactorCode,
                        errorMessage = null,
                        onSubmit = viewModel::submitTwoFactorCode,
                    )
                }

                is AuthState.Failed -> {
                    val retry = s.retryState
                    if (retry is AuthState.AwaitingTwoFactorCode) {
                        TwoFactorForm(
                            isBusy = false,
                            errorMessage = s.message,
                            onSubmit = viewModel::submitTwoFactorCode,
                        )
                    } else {
                        CredentialsForm(
                            isBusy = false,
                            errorMessage = s.message,
                            initialAppleId = viewModel.savedAppleId,
                            onSubmit = viewModel::signIn,
                        )
                    }
                }

                is AuthState.CheckingSavedSession -> CircularProgressIndicator()

                else -> {
                    CredentialsForm(
                        isBusy = s is AuthState.SigningIn,
                        errorMessage = null,
                        initialAppleId = viewModel.savedAppleId,
                        onSubmit = viewModel::signIn,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialsForm(
    isBusy: Boolean,
    errorMessage: String?,
    initialAppleId: String?,
    onSubmit: (appleId: String, password: String) -> Unit,
) {
    var appleId by remember { mutableStateOf(initialAppleId.orEmpty()) }
    var password by remember { mutableStateOf("") }

    Icon(
        imageVector = Icons.Default.CloudQueue,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(64.dp),
    )
    Text(
        text = stringResource(R.string.login_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Text(
        text = stringResource(R.string.login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
    )

    OutlinedTextField(
        value = appleId,
        onValueChange = { appleId = it },
        label = { Text(stringResource(R.string.label_apple_id)) },
        singleLine = true,
        enabled = !isBusy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.label_password)) },
        singleLine = true,
        enabled = !isBusy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    errorMessage?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    Button(
        onClick = { onSubmit(appleId.trim(), password) },
        enabled = !isBusy && appleId.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.action_sign_in))
        }
    }
}

@Composable
private fun TwoFactorForm(
    isBusy: Boolean,
    errorMessage: String?,
    onSubmit: (code: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.two_factor_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = stringResource(R.string.two_factor_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
    )

    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.label_verification_code)) },
        singleLine = true,
        enabled = !isBusy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .width(200.dp)
            .padding(bottom = 8.dp),
    )

    errorMessage?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    Button(
        onClick = { onSubmit(code) },
        enabled = !isBusy && code.length == 6,
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.action_verify))
        }
    }
}
