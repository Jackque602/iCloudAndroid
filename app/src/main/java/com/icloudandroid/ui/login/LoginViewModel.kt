package com.icloudandroid.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icloudandroid.auth.AuthRepository
import com.icloudandroid.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val state: StateFlow<AuthState> = authRepository.state

    val savedAppleId: String? get() = authRepository.savedAppleId

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }

    fun signIn(appleId: String, password: String) {
        viewModelScope.launch { authRepository.signIn(appleId, password) }
    }

    fun submitTwoFactorCode(code: String) {
        viewModelScope.launch { authRepository.submitTwoFactorCode(code) }
    }
}
