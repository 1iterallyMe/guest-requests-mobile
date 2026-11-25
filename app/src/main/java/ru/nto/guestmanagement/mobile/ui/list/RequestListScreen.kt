package ru.nto.guestmanagement.mobile.ui.list

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestSummary
import ru.nto.guestmanagement.mobile.data.formatDate
import ru.nto.guestmanagement.mobile.data.formatInterval
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

/**
 * Список заявок: фильтры, статусы, пустое состояние, ошибки, «Нет связи», ручное обновление, выход.
 * Данные загружаются по событию возвращения на экран ([ResumeEffect]); отдельной загрузки в
 * `init` ViewModel нет, поэтому при открытии выполняется один запрос.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestListScreen(
    container: AppContainer,
    onOpenDetails: (Long) -> Unit,
    onAddRequest: () -> Unit,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel = containerViewModel { RequestListViewModel(container) }

    LaunchedEffect(viewModel.sessionExpired) {
        if (viewModel.sessionExpired) onSessionExpired()
    }
    ResumeEffect { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.list_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    TextButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.testTag(TestIds.REQUESTS_REFRESH),
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                    TextButton(
                        onClick = { viewModel.logout(onLogout) },
                        modifier = Modifier.testTag(TestIds.REQUESTS_LOGOUT),
                    ) {
                        Text(stringResource(R.string.action_logout))
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(
                    onClick = onAddRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestIds.REQUESTS_ADD),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Filters(viewModel)
            if (viewModel.offline) {
                Text(
                    text = stringResource(R.string.offline),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag(TestIds.REQUESTS_OFFLINE),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    viewModel.initialError -> ErrorState()
                    viewModel.hasResponse && viewModel.requests.isEmpty() -> EmptyState()
                    else -> RequestList(viewModel.requests, onOpenDetails)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(viewModel: RequestListViewModel) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterItem(
            labelRes = R.string.filter_all,
            tag = TestIds.REQUESTS_FILTER_ALL,
            selected = viewModel.filter == null,
            onClick = { viewModel.selectFilter(null) },
        )
        FilterItem(
            labelRes = R.string.filter_pending,
            tag = TestIds.REQUESTS_FILTER_PENDING,
            selected = viewModel.filter == RequestStatus.PENDING,
            onClick = { viewModel.selectFilter(RequestStatus.PENDING) },
        )
        FilterItem(
            labelRes = R.string.filter_approved,
            tag = TestIds.REQUESTS_FILTER_APPROVED,
            selected = viewModel.filter == RequestStatus.APPROVED,
            onClick = { viewModel.selectFilter(RequestStatus.APPROVED) },
        )
        FilterItem(
            labelRes = R.string.filter_rejected,
            tag = TestIds.REQUESTS_FILTER_REJECTED,
            selected = viewModel.filter == RequestStatus.REJECTED,
            onClick = { viewModel.selectFilter(RequestStatus.REJECTED) },
        )
        FilterItem(
            labelRes = R.string.filter_cancelled,
            tag = TestIds.REQUESTS_FILTER_CANCELLED,
            selected = viewModel.filter == RequestStatus.CANCELLED,
            onClick = { viewModel.selectFilter(RequestStatus.CANCELLED) },
        )
    }
}

@Composable
private fun FilterItem(labelRes: Int, tag: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun RequestList(requests: List<RequestSummary>, onOpenDetails: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(requests, key = { _, item -> item.id }) { index, item ->
            RequestCard(item = item, index = index, onOpen = { onOpenDetails(item.id) })
        }
    }
}

@Composable
private fun RequestCard(item: RequestSummary, index: Int, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestIds.requestsPos(index))
                // Жест и действие доступности заданы вручную, чтобы содержимое карточки
                // оставалось набором отдельных элементов для чтения с экрана.
                .semantics {
                    role = Role.Button
                    onClick {
                        onOpen()
                        true
                    }
                }
                .pointerInput(item.id) { detectTapGestures { onOpen() } },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(TestIds.REQUEST_NAME),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDate(item.validFrom),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(TestIds.REQUEST_DATE),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatInterval(item.validFrom, item.validTo),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(TestIds.REQUEST_TIME),
                        )
                    }
                }
                StatusLabel(status = item.status, tag = TestIds.REQUEST_STATUS)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.list_empty),
            modifier = Modifier.testTag(TestIds.REQUESTS_EMPTY),
        )
    }
}

@Composable
private fun ErrorState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.list_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(TestIds.REQUESTS_ERROR),
        )
    }
}
