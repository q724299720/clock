package com.smartclock.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    onAuthed: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var isRegister by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) } // 0=手机 1=邮箱
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthed() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isRegister) "注册" else "登录", style = MaterialTheme.typography.headlineLarge)

        TabRow(selectedTabIndex = tabIndex, modifier = Modifier.padding(vertical = 16.dp)) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("手机号") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("邮箱") })
        }

        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text(if (tabIndex == 0) "手机号" else "邮箱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        if (isRegister) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                val isEmail = tabIndex == 1
                if (isRegister) vm.register(account.trim(), isEmail, password, nickname.ifBlank { null })
                else vm.login(account.trim(), isEmail, password)
            },
            enabled = !state.loading && account.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (isRegister) "注册并登录" else "登录")
        }

        TextButton(onClick = { isRegister = !isRegister; vm.consumeError() }) {
            Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
        }
    }
}
