package br.com.jogatina.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
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
                title = { Text(stringResource(br.com.jogatina.R.string.chat_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(br.com.jogatina.R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openNewChat() }) {
                        Icon(Icons.Filled.AddComment, contentDescription = stringResource(br.com.jogatina.R.string.new_chat))
                    }
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
                    stringResource(br.com.jogatina.R.string.chat_empty),
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
                val preview = conversation.lastMessage?.content?.let { lastPreviewText(it) }
                Text(
                    preview ?: stringResource(br.com.jogatina.R.string.say_hi),
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
            Text(stringResource(br.com.jogatina.R.string.new_chat), color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(br.com.jogatina.R.string.pick_friend), color = JogatinaWhite70, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (friends.isEmpty()) {
                Text(
                    stringResource(br.com.jogatina.R.string.no_friends_chat),
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
                                Text(friend.fullName.ifBlank { stringResource(br.com.jogatina.R.string.player) }, color = JogatinaWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (friend.isOnline) stringResource(br.com.jogatina.R.string.online) else stringResource(br.com.jogatina.R.string.offline),
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
    bottomLift: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose { /* socket fecha no onCleared do ViewModel */ }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // O Scaffold externo (JogatinaApp) já consome os insets do sistema;
        // reaplicar aqui duplicava o padding inferior (o vão gigante).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            if (state.connected) stringResource(br.com.jogatina.R.string.chat_online) else state.connectionInfo,
                            color = if (state.connected) Color(0xFF4CAF50) else JogatinaSubtitle,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(br.com.jogatina.R.string.back))
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
        val context = LocalContext.current
        val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 5 * 1024 * 1024) return@rememberLauncherForActivityResult
                    viewModel.onImagePicked(PickedChatImage(bytes, mime, uri))
                }
            } catch (_: Exception) {
            }
        }

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
                        stringResource(br.com.jogatina.R.string.no_messages),
                        color = JogatinaSubtitle, fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.runtime.LaunchedEffect(state.messages.size) {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.messages.reversed(), key = { it.id }) { message ->
                        MessageBubble(
                            content = message.content,
                            time = message.sentOnUtc,
                            mine = viewModel.myUserId != null && message.senderId.equals(viewModel.myUserId, ignoreCase = true),
                            dim = message.id.startsWith("pending:"),
                            resolveImageUrl = viewModel::fullImageUrl
                        )
                    }
                }
            }

            // Preview do anexo (galeria ou GIF/figurinha do teclado) antes de enviar.
            val picked = state.pickedImage
            if (picked != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JogatinaNavyMid)
                        .padding(8.dp)
                ) {
                    AsyncImage(
                        model = picked.previewUri ?: picked.bytes,
                        contentDescription = stringResource(br.com.jogatina.R.string.attached_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.uploadingImage) stringResource(br.com.jogatina.R.string.chat_uploading)
                        else stringResource(br.com.jogatina.R.string.attached_image),
                        color = JogatinaWhite70, fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.uploadingImage) {
                        CircularProgressIndicator(
                            color = JogatinaMagenta, strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(onClick = { viewModel.onImagePicked(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(br.com.jogatina.R.string.remove_image), tint = JogatinaWhite)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    // Altura exata calculada na Activity (teclado menos o que
                    // o padding externo já cobre): sem chute, sem vão.
                    .padding(bottom = bottomLift)
            ) {
                IconButton(
                    onClick = { pickGallery.launch("image/*") },
                    enabled = !state.sending
                ) {
                    Icon(Icons.Filled.Image, contentDescription = stringResource(br.com.jogatina.R.string.attach_image), tint = JogatinaMagenta)
                }
                ChatMessageInput(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    onCommitImage = { bytes, mime, uri ->
                        viewModel.onImagePicked(PickedChatImage(bytes, mime, uri))
                    },
                    onSendAction = viewModel::send,
                    placeholder = stringResource(br.com.jogatina.R.string.message_hint),
                    enabled = !state.sending,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::send, enabled = !state.sending) {
                    if (state.sending) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(br.com.jogatina.R.string.send), tint = JogatinaMagenta)
                    }
                }
            }
        }
    }
}

/** Divide o conteúdo em texto + URLs de imagem (convenção: imageUrl enviada no corpo). */
private fun splitChatContent(content: String): Pair<String, List<String>> {
    val tokens = content.split("\\s+".toRegex())
    val images = tokens.filter { isImageToken(it) }
    val text = tokens.filterNot { isImageToken(it) }.joinToString(" ").trim()
    return text to images
}

private fun isImageToken(token: String): Boolean {
    val t = token.trim().trim('"', '\'', '(', ')', ',', ';')
    if (t.isBlank()) return false
    val lower = t.lowercase()
    val looksPath = t.startsWith("/uploads/") || t.startsWith("/chat-images/") || t.startsWith("/chat/") ||
        lower.startsWith("http://") || lower.startsWith("https://")
    if (!looksPath) return false
    return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
        lower.contains(".gif") || lower.contains(".webp") ||
        t.startsWith("/uploads/") || t.startsWith("/chat-images/") || t.startsWith("/chat/")
}

@Composable
private fun lastPreviewText(content: String): String {
    val (text, images) = splitChatContent(content)
    if (text.isNotBlank()) return text
    if (images.isNotEmpty()) return stringResource(br.com.jogatina.R.string.chat_image_label)
    return content
}

@Composable
private fun MessageBubble(
    content: String,
    time: String,
    mine: Boolean,
    dim: Boolean = false,
    resolveImageUrl: (String) -> String? = { it }
) {
    val (text, imageTokens) = splitChatContent(content)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.6f else 1f),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) JogatinaMagenta else JogatinaNavyMid)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (text.isNotBlank()) {
                Text(text, color = JogatinaWhite, fontSize = 14.sp)
            }
            imageTokens.forEach { token ->
                val url = resolveImageUrl(token) ?: token
                Spacer(modifier = Modifier.height(if (text.isNotBlank()) 6.dp else 2.dp))
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(br.com.jogatina.R.string.chat_image_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 200.dp, height = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
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
            Text(stringResource(br.com.jogatina.R.string.chat_title), color = JogatinaWhite)
        }
    }
}