package ru.nto.guestmanagement.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

/**
 * Экран входа: поле кода, кнопка «Войти», запрос `/api/{code}/auth`, сохранение кода в `container.session`,
 * сообщение об ошибке и «Сессия завершена» при [sessionExpired].
 */
@Composable
fun LoginScreen(container: AppContainer, sessionExpired: Boolean, onLoggedIn: () -> Unit) {
    val viewModel = containerViewModel { LoginViewModel(container, sessionExpired) }

    LaunchedEffect(viewModel.loggedIn) {
        if (viewModel.loggedIn) onLoggedIn()
    }

    // Пока читается сохранённый код (и сразу после успешного входа) форма не показывается, чтобы не мелькала.
    if (viewModel.checkingSavedCode || viewModel.loggedIn) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = viewModel.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text(stringResource(R.string.login_code_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestIds.AUTH_CODE),
        )
        if (sessionExpired) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_session_expired),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.AUTH_SESSION_EXPIRED),
            )
        }
        viewModel.errorRes?.let { messageRes ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(messageRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.AUTH_ERROR),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestIds.AUTH_SUBMIT),
        ) {
            Text(stringResource(R.string.login_submit))
        }
        // Индикатор выводится рядом с кнопкой, а не внутри неё.
        if (viewModel.loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.testTag(TestIds.AUTH_LOADING))
        }
    }
}
