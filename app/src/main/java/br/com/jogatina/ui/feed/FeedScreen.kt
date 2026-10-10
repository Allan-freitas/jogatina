package br.com.jogatina.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jogatina.data.feed.CommentDto
import br.com.jogatina.data.feed.PostDto
import br.com.jogatina.data.feed.Reaction
import br.com.jogatina.ui.profile.ProfileAvatar
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaNavyTop
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70
import coil.compose.AsyncImage
import java.time.Duration
import java.time.Instant

/**
 * Newsfeed estilo Olympus (só posts de texto/imagem — sem Multimídia/Blog):
 * composer com imagem, likes, comentários e respostas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onLogout: () -> Unit,
    unreadCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 8 * 1024 * 1024) return@rememberLauncherForActivityResult
                viewModel.onImagePicked(PickedImage(bytes, mime, uri))
            }
        } catch (_: Exception) {
            // Mantém o composer como está; o erro de rede aparece no publish.
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.let { "Olá, ${it.displayName}" } ?: "Olá",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = JogatinaDiscordRed,
                                        contentColor = JogatinaWhite
                                    ) {
                                        Text(if (unreadCount > 99) "99+" else "$unreadCount")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notificações")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sair")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JogatinaNavyMid,
                    titleContentColor = JogatinaWhite,
                    actionIconContentColor = JogatinaWhite
                )
            )
        },
        containerColor = JogatinaNavyBottom
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                ComposerCard(
                    text = state.composerText,
                    pickedUri = state.pickedImage?.previewUri,
                    publishing = state.publishing,
                    onTextChange = viewModel::onComposerChange,
                    onAttach = { pickImage.launch("image/*") },
                    onClearImage = { viewModel.onImagePicked(null) },
                    onPublish = viewModel::submitPost
                )
            }

            if (state.error != null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = state.error!!,
                            color = JogatinaDiscordRed,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("Tentar de novo", color = JogatinaMagenta, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = JogatinaMagenta)
                    }
                }
            }

            items(state.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    isMine = state.myUserId != null && post.authorId.equals(state.myUserId, ignoreCase = true),
                    imageUrl = viewModel.postImageUrl(post.imageUrl),
                    expanded = state.expanded.contains(post.id),
                    comments = state.comments[post.id].orEmpty(),
                    commentsLoading = state.commentsLoading.contains(post.id),
                    commentInput = state.commentInputs[post.id].orEmpty(),
                    replyTo = state.replyTo[post.id],
                    reacting = state.reacting.contains(post.id),
                    pickerOpen = state.reactionPickerFor == post.id,
                    sendingComment = state.sendingComment.contains(post.id),
                    onQuickReact = { viewModel.quickReact(post.id) },
                    onOpenPicker = { viewModel.openReactionPicker(post.id) },
                    onClosePicker = viewModel::closeReactionPicker,
                    onReact = { kind -> viewModel.react(post.id, kind) },
                    onToggleComments = { viewModel.toggleComments(post.id) },
                    onCommentInput = { viewModel.onCommentInput(post.id, it) },
                    onReplyTo = { viewModel.setReplyTo(post.id, it) },
                    onSendComment = { viewModel.submitComment(post.id) },
                    onDelete = { viewModel.deletePost(post.id) },
                    resolvePhoto = viewModel::postImageUrl
                )
            }

            if (!state.loading && state.posts.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum post ainda. Seja o primeiro a publicar!",
                        color = JogatinaSubtitle,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ComposerCard(
    text: String,
    pickedUri: Uri?,
    publishing: Boolean,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClearImage: () -> Unit,
    onPublish: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("No que você está pensando?", color = JogatinaWhite70, fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JogatinaWhite,
                    unfocusedTextColor = JogatinaWhite,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = JogatinaMagenta
                )
            )
            if (pickedUri != null) {
                Box {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = "Imagem anexada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover", tint = JogatinaWhite)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Filled.Image, contentDescription = "Anexar imagem", tint = JogatinaMagenta)
                }
                TextButton(onClick = onPublish, enabled = !publishing) {
                    if (publishing) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Publicar", color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: PostDto,
    isMine: Boolean,
    imageUrl: String?,
    expanded: Boolean,
    comments: List<CommentDto>,
    commentsLoading: Boolean,
    commentInput: String,
    replyTo: CommentDto?,
    reacting: Boolean,
    pickerOpen: Boolean,
    sendingComment: Boolean,
    onQuickReact: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onReact: (String?) -> Unit,
    onToggleComments: () -> Unit,
    onCommentInput: (String) -> Unit,
    onReplyTo: (CommentDto?) -> Unit,
    onSendComment: () -> Unit,
    onDelete: () -> Unit,
    resolvePhoto: (String?) -> String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    photoUrl = resolvePhoto(post.authorPhotoUrl),
                    name = post.authorName,
                    size = 40.dp,
                    showBorder = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(timeAgo(post.createdOnUtc), color = JogatinaSubtitle, fontSize = 12.sp)
                }
                if (isMine) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Apagar", tint = JogatinaSubtitle)
                    }
                }
            }

            if (!post.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(post.content, color = JogatinaWhite, fontSize = 14.sp, lineHeight = 20.sp)
            }

            if (imageUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagem do post",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            if (post.totalReactions > 0) {
                Text(
                    text = "${Reaction.topEmojis(post.reactionCounts)} ${post.totalReactions}",
                    color = JogatinaWhite70,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(onClick = onOpenPicker)
                        .padding(vertical = 2.dp)
                )
            }
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val myEmoji = Reaction.fromKind(post.myReaction)?.emoji
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .combinedClickable(
                                onClick = onOpenPicker,
                                onLongClick = onOpenPicker
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reacting) {
                            CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else if (myEmoji != null) {
                            Text(myEmoji, fontSize = 22.sp)
                        } else {
                            Icon(
                                Icons.Filled.FavoriteBorder,
                                contentDescription = "Reagir",
                                tint = JogatinaWhite70
                            )
                        }
                    }
                    TextButton(onClick = onQuickReact, enabled = !reacting) {
                        Text(
                            if (post.myReaction == Reaction.HEART.kind) "Amei!" else "Amei",
                            color = if (post.myReaction != null) JogatinaDiscordRed else JogatinaWhite70,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onToggleComments, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Comentários", tint = JogatinaWhite70)
                    }
                    Text("${post.commentCount}", color = JogatinaWhite70, fontSize = 13.sp)
                }
                DropdownMenu(
                    expanded = pickerOpen,
                    onDismissRequest = onClosePicker,
                    modifier = Modifier.background(JogatinaNavyMid)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Reaction.entries.forEach { reaction ->
                            val selected = post.myReaction == reaction.kind
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) JogatinaMagenta.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onReact(if (selected) null else reaction.kind)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(reaction.emoji, fontSize = 26.sp)
                                Text(reaction.label, color = JogatinaWhite70, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                if (commentsLoading && comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
                comments.forEach { comment ->
                    CommentRow(
                        comment = comment,
                        photoUrl = resolvePhoto(comment.authorPhotoUrl),
                        onReply = { onReplyTo(comment) }
                    )
                    comment.replies.forEach { reply ->
                        CommentRow(
                            comment = reply,
                            photoUrl = resolvePhoto(reply.authorPhotoUrl),
                            indent = true,
                            onReply = null
                        )
                    }
                }
                if (replyTo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "Respondendo a ${replyTo.authorName}",
                            color = JogatinaMagenta,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onReplyTo(null) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar resposta", tint = JogatinaSubtitle)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = onCommentInput,
                        placeholder = { Text("Comentar...", color = JogatinaWhite70, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JogatinaWhite,
                            unfocusedTextColor = JogatinaWhite,
                            focusedBorderColor = JogatinaMagenta,
                            unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
                            cursorColor = JogatinaMagenta
                        )
                    )
                    IconButton(onClick = onSendComment, enabled = !sendingComment) {
                        if (sendingComment) {
                            CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = JogatinaMagenta)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentDto,
    photoUrl: String?,
    indent: Boolean = false,
    onReply: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 40.dp else 0.dp, top = 6.dp)
    ) {
        ProfileAvatar(photoUrl = photoUrl, name = comment.authorName, size = 30.dp, showBorder = false)
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(JogatinaNavyTop)
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (onReply != null) {
                    TextButton(onClick = onReply) {
                        Text("Responder", color = JogatinaMagenta, fontSize = 12.sp)
                    }
                }
            }
            Text(comment.content, color = JogatinaWhite, fontSize = 13.sp)
            Text(timeAgo(comment.createdOnUtc), color = JogatinaSubtitle, fontSize = 11.sp)
        }
    }
}

fun timeAgo(iso: String): String {
    return try {
        val then = Instant.parse(iso)
        val minutes = Duration.between(then, Instant.now()).toMinutes()
        when {
            minutes < 1 -> "agora"
            minutes < 60 -> "há ${minutes}min"
            minutes < 60 * 24 -> "há ${minutes / 60}h"
            minutes < 60 * 24 * 7 -> "há ${minutes / (60 * 24)}d"
            else -> iso.substring(0, 10).split("-").reversed().joinToString("/")
        }
    } catch (_: Exception) {
        ""
    }
}

@Preview(showBackground = true)
@Composable
private fun PostCardPreview() {
    JogatinaTheme {
        Box(Modifier.background(JogatinaNavyBottom).padding(12.dp)) {
            PostCard(
                post = PostDto(
                    id = "1", authorId = "a", authorName = "Mestre Lee",
                    authorPhotoUrl = null,
                    content = "Bora de ranked hoje à noite?",
                    imageUrl = null, createdOnUtc = Instant.now().toString(),
                    totalReactions = 12,
                    reactionCounts = mapOf("heart" to 8, "wow" to 3, "haha" to 1),
                    myReaction = "heart", commentCount = 3
                ),
                isMine = true, imageUrl = null, expanded = true,
                comments = listOf(
                    CommentDto("c1", "b", "Ana", null, "Eu vou!", Instant.now().toString(), emptyList())
                ),
                commentsLoading = false, commentInput = "", replyTo = null,
                reacting = false, pickerOpen = false, sendingComment = false,
                onQuickReact = {}, onOpenPicker = {}, onClosePicker = {}, onReact = {},
                onToggleComments = {}, onCommentInput = {},
                onReplyTo = {}, onSendComment = {}, onDelete = {},
                resolvePhoto = { it }
            )
        }
    }
}
