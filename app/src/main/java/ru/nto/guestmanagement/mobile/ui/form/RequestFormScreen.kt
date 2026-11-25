package ru.nto.guestmanagement.mobile.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

/**
 * Форма новой заявки: валидация, ошибки сервера под полями, защита от повторной отправки,
 * прокрутка и учёт IME-insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel = containerViewModel { RequestFormViewModel(container) }

    LaunchedEffect(viewModel.sessionExpired) {
        if (viewModel.sessionExpired) onSessionExpired()
    }

    val commonError = viewModel.commonErrorRes?.let { stringResource(it) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.form_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TestIds.FORM_BACK),
                    ) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (commonError != null) {
                    Text(
                        text = commonError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestIds.FORM_ERROR_COMMON),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // Индикатор отправки — отдельный элемент вне кнопки.
                if (viewModel.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag(TestIds.FORM_LOADING),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { viewModel.submit(onCreated) },
                    enabled = viewModel.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestIds.FORM_SUBMIT),
                ) {
                    Text(stringResource(R.string.form_submit))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormField(
                value = viewModel.fullName,
                onValueChange = viewModel::onFullNameChange,
                labelRes = R.string.form_full_name,
                fieldTag = TestIds.FORM_FULL_NAME,
                error = fieldError(viewModel, FormErrorSlot.FULL_NAME),
                errorTag = TestIds.FORM_ERROR_FULL_NAME,
            )
            FormField(
                value = viewModel.phone,
                onValueChange = viewModel::onPhoneChange,
                labelRes = R.string.form_phone,
                fieldTag = TestIds.FORM_PHONE,
                error = fieldError(viewModel, FormErrorSlot.PHONE),
                errorTag = TestIds.FORM_ERROR_PHONE,
            )
            FormField(
                value = viewModel.organization,
                onValueChange = viewModel::onOrganizationChange,
                labelRes = R.string.form_organization,
                fieldTag = TestIds.FORM_ORGANIZATION,
                error = fieldError(viewModel, FormErrorSlot.ORGANIZATION),
                errorTag = TestIds.FORM_ERROR_ORGANIZATION,
            )
            FormField(
                value = viewModel.purpose,
                onValueChange = viewModel::onPurposeChange,
                labelRes = R.string.form_purpose,
                fieldTag = TestIds.FORM_PURPOSE,
                error = fieldError(viewModel, FormErrorSlot.PURPOSE),
                errorTag = TestIds.FORM_ERROR_PURPOSE,
            )
            FormField(
                value = viewModel.date,
                onValueChange = viewModel::onDateChange,
                labelRes = R.string.form_date,
                fieldTag = TestIds.FORM_DATE,
                error = fieldError(viewModel, FormErrorSlot.DATE),
                errorTag = TestIds.FORM_ERROR_DATE,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = viewModel.timeFrom,
                    onValueChange = viewModel::onTimeFromChange,
                    labelRes = R.string.form_time_from,
                    fieldTag = TestIds.FORM_TIME_FROM,
                    error = fieldError(viewModel, FormErrorSlot.VALID_FROM),
                    errorTag = TestIds.FORM_ERROR_VALID_FROM,
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = viewModel.timeTo,
                    onValueChange = viewModel::onTimeToChange,
                    labelRes = R.string.form_time_to,
                    fieldTag = TestIds.FORM_TIME_TO,
                    error = fieldError(viewModel, FormErrorSlot.VALID_TO),
                    errorTag = TestIds.FORM_ERROR_VALID_TO,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    fieldTag: String,
    error: String?,
    errorTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(labelRes)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(fieldTag),
        )
        // Ошибка выводится отдельным текстом под полем, чтобы её было видно и доступно прочитать.
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
                    .testTag(errorTag),
            )
        }
    }
}

@Composable
private fun fieldError(viewModel: RequestFormViewModel, slot: FormErrorSlot): String? {
    val serverText = when (slot) {
        FormErrorSlot.FULL_NAME -> viewModel.serverErrors["fullName"]
        FormErrorSlot.PHONE -> viewModel.serverErrors["phone"]
        FormErrorSlot.ORGANIZATION -> viewModel.serverErrors["organization"]
        FormErrorSlot.PURPOSE -> viewModel.serverErrors["purpose"]
        FormErrorSlot.VALID_FROM -> viewModel.serverErrors["validFrom"]
        FormErrorSlot.VALID_TO -> viewModel.serverErrors["validTo"]
        FormErrorSlot.DATE -> null
    }
    val clientError = viewModel.clientErrors[slot]
    return if (clientError != null) stringResource(clientError.messageRes) else serverText
}
