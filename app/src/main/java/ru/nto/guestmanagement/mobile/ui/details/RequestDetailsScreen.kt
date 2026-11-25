package ru.nto.guestmanagement.mobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import ru.nto.guestmanagement.mobile.data.RequestDetails
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.formatDateTime
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

/**
 * Детали заявки: все поля, комментарий охраны, отмена с подтверждением, обработка `409`,
 * ручное обновление и повтор при ошибке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    container: AppContainer,
    requestId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel = containerViewModel { RequestDetailsViewModel(container, requestId) }

    LaunchedEffect(viewModel.sessionExpired) {
        if (viewModel.sessionExpired) onSessionExpired()
    }
    ResumeEffect { viewModel.refresh() }

    val details = viewModel.details

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.details_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TestIds.DETAILS_BACK),
                    ) {
                        Text(stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.testTag(TestIds.DETAILS_REFRESH),
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        bottomBar = {
            val messageRes = viewModel.cancelErrorRes
            val showCancel = details?.status == RequestStatus.PENDING
            // Сообщение об ошибке отмены показывается и тогда, когда кнопка отмены уже исчезла.
            if (messageRes != null || showCancel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    if (messageRes != null) {
                        Text(
                            text = stringResource(messageRes),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestIds.DETAILS_CANCEL_ERROR),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (showCancel) {
                        Button(
                            onClick = viewModel::askCancel,
                            enabled = !viewModel.cancelling,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestIds.DETAILS_CANCEL),
                        ) {
                            Text(stringResource(R.string.details_cancel))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                viewModel.initialError -> DetailsError(onRetry = viewModel::refresh)
                details == null -> Unit
                else -> DetailsContent(details = details, offline = viewModel.offline)
            }
        }
    }

    if (viewModel.confirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text(stringResource(R.string.details_cancel_title)) },
            text = { Text(stringResource(R.string.details_cancel_text)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCancel,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_CONFIRM),
                ) {
                    Text(stringResource(R.string.details_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissCancel,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_DISMISS),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}

@Composable
private fun DetailsContent(details: RequestDetails, offline: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (offline) {
            Text(
                text = stringResource(R.string.offline),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestIds.DETAILS_OFFLINE),
            )
        }
        DetailRow(R.string.details_label_id, details.id.toString(), TestIds.DETAILS_ID)
        DetailRow(R.string.details_label_name, details.fullName, TestIds.DETAILS_NAME)
        DetailRow(R.string.details_label_phone, details.phone, TestIds.DETAILS_PHONE)
        if (!details.organization.isNullOrBlank()) {
            DetailRow(
                labelRes = R.string.details_label_organization,
                value = details.organization,
                tag = TestIds.DETAILS_ORGANIZATION,
            )
        }
        DetailRow(R.string.details_label_purpose, details.purpose, TestIds.DETAILS_PURPOSE)
        DetailRow(R.string.details_label_from, formatDateTime(details.validFrom), TestIds.DETAILS_VALID_FROM)
        DetailRow(R.string.details_label_to, formatDateTime(details.validTo), TestIds.DETAILS_VALID_TO)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.details_label_status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusLabel(status = details.status, tag = TestIds.DETAILS_STATUS)
        }
        DetailRow(R.string.details_label_created, formatDateTime(details.createdAt), TestIds.DETAILS_CREATED_AT)
        DetailRow(R.string.details_label_updated, formatDateTime(details.updatedAt), TestIds.DETAILS_UPDATED_AT)
        if (details.status == RequestStatus.REJECTED && details.comment != null) {
            DetailRow(R.string.details_label_comment, details.comment, TestIds.DETAILS_COMMENT)
        }
    }
}

@Composable
private fun DetailRow(labelRes: Int, value: String, tag: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Текст элемента деталей равен значению поля, без подписи внутри того же узла.
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun DetailsError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.details_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(TestIds.DETAILS_ERROR),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(TestIds.DETAILS_RETRY),
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
