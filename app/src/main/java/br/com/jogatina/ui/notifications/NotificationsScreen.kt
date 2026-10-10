package br.com.jogatina.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jogatina.data.notifications.NotificationDto
import br.com.jogatina.ui.feed.timeAgo
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startAutoRefresh()
        onDispose { viewModel.stopAutoRefresh() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(br.com.jogatina.R.string.notif_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(br.com.jogatina.R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(br.com.jogatina.R.string.refresh_action))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JogatinaNavyMid,
                    titleContentColor = JogatinaWhite,
                    navigationIconContentColor = JogatinaWhite,
                    actionIconContentColor = JogatinaWhite
                )
            )
        },
        containerColor = JogatinaNavyBottom
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !state.onlyUnread,
                    onClick = { viewModel.setOnlyUnread(false) },
                    label = { Text(stringResource(br.com.jogatina.R.string.tab_all), fontSize = 13.sp) },
                    colors = notificationChipColors()
                )
                FilterChip(
                    selected = state.onlyUnread,
                    onClick = { viewModel.setOnlyUnread(true) },
                    label = { Text(stringResource(br.com.jogatina.R.string.tab_unread, state.unreadCount), fontSize = 13.sp) },
                    colors = notificationChipColors()
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.markAllAsRead() },
                    enabled = !state.markingAll && state.unreadCount > 0
                ) {
                    if (state.markingAll) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, tint = JogatinaMagenta, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(br.com.jogatina.R.string.mark_all_read), color = JogatinaMagenta, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                TextButton(onClick = { viewModel.refresh() }) {
                    Text(stringResource(br.com.jogatina.R.string.retry), color = JogatinaMagenta, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else if (state.visible.isEmpty()) {
                Text(
                    if (state.onlyUnread) stringResource(br.com.jogatina.R.string.empty_unread) else stringResource(br.com.jogatina.R.string.empty_notif),
                    color = JogatinaSubtitle, fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.visible, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            onClick = { viewModel.open(notification) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun notificationChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = JogatinaMagenta,
    selectedLabelColor = JogatinaWhite,
    containerColor = JogatinaNavyMid,
    labelColor = JogatinaWhite70
)

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) JogatinaNavyMid.copy(alpha = 0.6f) else JogatinaNavyMid
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(JogatinaMagenta.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    typeIcon(notification.type),
                    contentDescription = null,
                    tint = JogatinaWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title.ifBlank { typeLabel(notification.type) }, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (notification.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(notification.body, color = JogatinaWhite70, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(timeAgo(notification.createdOnUtc), color = JogatinaSubtitle, fontSize = 11.sp)
            }
            if (!notification.isRead) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(JogatinaMagenta)
                )
            }
        }
    }
}

private fun typeIcon(type: String): ImageVector = when (type) {
    "friend_request" -> Icons.Filled.PersonAdd
    "friend_accepted" -> Icons.Filled.GroupAdd
    "new_message" -> Icons.Filled.ChatBubbleOutline
    else -> Icons.Filled.Notifications
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    "friend_request" -> stringResource(br.com.jogatina.R.string.notif_friend_request)
    "friend_accepted" -> stringResource(br.com.jogatina.R.string.notif_friend_accepted)
    "new_message" -> stringResource(br.com.jogatina.R.string.notif_new_message)
    else -> stringResource(br.com.jogatina.R.string.notif_generic)
}

@Preview(showBackground = true)
@Composable
private fun NotificationRowPreview() {
    JogatinaTheme {
        Box(Modifier.background(JogatinaNavyBottom).padding(12.dp)) {
            NotificationRow(
                notification = NotificationDto(
                    id = "1", type = "friend_request",
                    title = "Novo pedido de amizade",
                    body = "Ana quer jogar com você",
                    isRead = false, createdOnUtc = ""
                ),
                onClick = {}
            )
        }
    }
}