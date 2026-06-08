package com.smartclock.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartclock.data.remote.UserRemoteDataSource.AuthResult
import com.smartclock.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(account: String, isEmail: Boolean, password: String) = run(
        action = { repo.login(account, isEmail, password) }
    )

    fun register(account: String, isEmail: Boolean, password: String, nickname: String?) = run(
        action = { repo.register(account, isEmail, password, nickname) }
    )

    private fun run(action: suspend () -> AuthResult) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = runCatching { action() }.getOrElse {
                AuthResult.Failure("网络异常：${it.message ?: "连接失败"}")
            }
            _state.value = when (result) {
                is AuthResult.Success -> AuthUiState(success = true)
                is AuthResult.Failure -> AuthUiState(error = result.message)
            }
        }
    }

    fun consumeError() { _state.value = _state.value.copy(error = null) }
}
