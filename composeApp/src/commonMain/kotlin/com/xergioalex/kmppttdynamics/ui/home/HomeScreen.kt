package com.xergioalex.kmppttdynamics.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.ui.components.PttHorizontalMark
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.home_create
import kmppttdynamics.composeapp.generated.resources.home_join_code_hint
import kmppttdynamics.composeapp.generated.resources.home_join_with_code
import kmppttdynamics.composeapp.generated.resources.home_live_meetups
import kmppttdynamics.composeapp.generated.resources.home_no_live
import kmppttdynamics.composeapp.generated.resources.home_no_past
import kmppttdynamics.composeapp.generated.resources.home_past_meetups
import kmppttdynamics.composeapp.generated.resources.join_meetup_not_found
import kmppttdynamics.composeapp.generated.resources.room_join_code
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    container: AppContainer,
    onCreate: () -> Unit,
    onJoin: (meetupId: String) -> Unit,
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container.meetups) }
    val state by vm.state.collectAsStateWithLifecycle()
    val event by vm.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (val e = event) {
            is HomeEvent.JoinResolved -> {
                onJoin(e.meetupId)
                vm.consumeEvent()
            }
            null -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PttHorizontalMark()
            Button(onClick = onCreate) { Text(stringResource(Res.string.home_create)) }
        }
        Spacer(Modifier.height(16.dp))

        JoinByCodeBar(
            pending = state.joinPending,
            errorKey = state.joinError,
            onJoin = vm::onJoinByCode,
        )
        Spacer(Modifier.height(20.dp))
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SectionHeader(stringResource(Res.string.home_live_meetups))
        if (state.live.isEmpty() && !state.isLoading) {
            EmptyState(stringResource(Res.string.home_no_live))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.live, key = { it.id }) { meetup ->
                    MeetupCard(meetup, onClick = { onJoin(meetup.id) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(Res.string.home_past_meetups))
        if (state.past.isEmpty() && !state.isLoading) {
            EmptyState(stringResource(Res.string.home_no_past))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.past, key = { it.id }) { meetup ->
                    MeetupCard(meetup, onClick = { onJoin(meetup.id) })
                }
            }
        }
    }
}

@Composable
private fun JoinByCodeBar(pending: Boolean, errorKey: String?, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(8).uppercase() },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.home_join_code_hint)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onJoin(code) },
                enabled = !pending && code.isNotBlank(),
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.home_join_with_code))
                }
            }
        }
        if (errorKey != null) {
            // Today only one error is reported, so map it directly. When we
            // grow more, swap this for a typed error sealed class.
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.join_meetup_not_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun MeetupCard(meetup: Meetup, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(meetup.title, style = MaterialTheme.typography.titleMedium)
            if (!meetup.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meetup.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.room_join_code, meetup.joinCode),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = meetup.status.name.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
