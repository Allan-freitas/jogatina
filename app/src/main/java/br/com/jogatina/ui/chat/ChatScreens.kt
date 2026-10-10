package br.com.jogatina.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.jogatina.data.chat.ConversationDto
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.ui.feed.timeAgo
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70
import androidx.compose.ui.tooling.preview.Preview

// ---------- Lista de conversas ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenConversation: (ConversationDto) -> Unit,
    onNewConversation: (FriendDto) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Conversas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openNewChat() }) {
                        Icon(Icons.Filled.AddComment, contentDescription = "Nova conversa")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
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

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else if (state.conversations.isEmpty()) {
                Text(
                    "Nenhuma conversa ainda. Toque em + para chamar um amigo!",
                    color = JogatinaSubtitle, fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.conversations, key = { it.conversationId }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            myUserId = state.myUserId,
                            onClick = { onOpenConversation(conversation) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }

    if (state.showNewChat) {
        NewChatDialog(
            friends = state.friends,
            loading = state.friendsLoading,
            onPick = {
                viewModel.closeNewChat()
                onNewConversation(it)
            },
            onDismiss = viewModel::closeNewChat
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationDto,
    myUserId: String?,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(JogatinaMagenta.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.title(myUserId).firstOrNull()?.uppercase() ?: "?",
                    color = JogatinaWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation.title(myUserId),
                    color = JogatinaWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    conversation.lastMessage?.content ?: "Diga oi!",
                    color = JogatinaWhite70,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    conversation.lastMessage?.let { timeAgo(it.sentOnUtc) } ?: "",
                    color = JogatinaSubtitle,
                    fontSize = 11.sp
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Badge(
                        containerColor = JogatinaDiscordRed,
                        contentColor = JogatinaWhite
                    ) {
                        Text("${conversation.unreadCount}")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    friends: List<FriendDto>,
    loading: Boolean,
    onPick: (FriendDto) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JogatinaNavyMid)
                .padding(20.dp)
        ) {
            Text("Nova conversa", color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Escolha um amigo", color = JogatinaWhite70, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (friends.isEmpty()) {
                Text(
                    "Você ainda não tem amigos. Envie um pedido na aba social!",
                    color = JogatinaSubtitle, fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(friends, key = { it.friendId }) { friend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(friend) }
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(JogatinaMagenta.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    friend.fullName.firstOrNull()?.uppercase() ?: "?",
                                    color = JogatinaWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.fullName.ifBlank { "Jogador" }, color = JogatinaWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (friend.isOnline) "Online" else "Offline",
                                    color = if (friend.isOnline) Color(0xFF4CAF50) else JogatinaSubtitle,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Conversa ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose { /* socket fecha no onCleared do ViewModel */ }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (state.connected) "Online" else state.connectionInfo,
                            color = if (state.connected) Color(0xFF4CAF50) else JogatinaSubtitle,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JogatinaNavyMid,
                    titleContentColor = JogatinaWhite,
                    navigationIconContentColor = JogatinaWhite
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
            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
            }

            if (state.loadingHistory) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma mensagem ainda. Diga oi!",
                        color = JogatinaSubtitle, fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            content = message.content,
                            time = message.sentOnUtc,
                            mine = viewModel.myUserId != null && message.senderId.equals(viewModel.myUserId, ignoreCase = true)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text("Mensagem...", color = JogatinaWhite70, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JogatinaWhite,
                        unfocusedTextColor = JogatinaWhite,
                        focusedBorderColor = JogatinaMagenta,
                        unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
                        cursorColor = JogatinaMagenta
                    )
                )
                IconButton(onClick = viewModel::send, enabled = !state.sending) {
                    if (state.sending) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = JogatinaMagenta)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(content: String, time: String, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) JogatinaMagenta else JogatinaNavyMid)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(content, color = JogatinaWhite, fontSize = 14.sp)
            Text(
                timeAgo(time),
                color = if (mine) JogatinaWhite.copy(alpha = 0.8f) else JogatinaSubtitle,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListPreview() {
    JogatinaTheme {
        Box(Modifier.background(JogatinaNavyBottom).padding(12.dp)) {
            Text("Conversas", color = JogatinaWhite)
        }
    }
}
