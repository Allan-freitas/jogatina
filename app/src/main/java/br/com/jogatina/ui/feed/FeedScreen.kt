package br.com.jogatina.ui.feed

import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.jogatina.data.feed.CommentDto
import br.com.jogatina.data.feed.PostDto
import br.com.jogatina.data.feed.Reaction
import br.com.jogatina.ui.chat.CommitContentEditText
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

/** Mesmo teto do upload em `feed/images` na Web.Api (UploadImageCommandHandler.MaxBytes). */
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** Tag usada para evitar loop ao sincronizar o texto do composer (AndroidView <-> Compose). */
private const val COMPOSER_TAG_UPDATING = 0x7F0B0002

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
    unreadChatCount: Int = 0,
    onChatClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
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
                if (bytes.size > MAX_IMAGE_BYTES) return@rememberLauncherForActivityResult
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
                        text = state.profile?.let { stringResource(br.com.jogatina.R.string.greeting, it.displayName) }
                            ?: stringResource(br.com.jogatina.R.string.greeting_fallback),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onChatClick) {
                        BadgedBox(
                            badge = {
                                if (unreadChatCount > 0) {
                                    Badge(
                                        containerColor = JogatinaDiscordRed,
                                        contentColor = JogatinaWhite
                                    ) {
                                        Text(if (unreadChatCount > 99) "99+" else "$unreadChatCount")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = stringResource(br.com.jogatina.R.string.conversations))
                        }
                    }
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
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(br.com.jogatina.R.string.notifications))
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(br.com.jogatina.R.string.refresh_action))
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(br.com.jogatina.R.string.logout))
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
                    pickedImage = state.pickedImage,
                    publishing = state.publishing,
                    onTextChange = viewModel::onComposerChange,
                    onAttach = { pickImage.launch("image/*") },
                    onCommitImage = { bytes, mime, uri ->
                        viewModel.onImagePicked(PickedImage(bytes, mime, uri))
                    },
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
                            Text(stringResource(br.com.jogatina.R.string.retry), color = JogatinaMagenta, fontSize = 13.sp)
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
                    commentImage = state.commentImages[post.id],
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
                    onCommentImagePicked = { viewModel.onCommentImagePicked(post.id, it) },
                    onReplyTo = { viewModel.setReplyTo(post.id, it) },
                    onSendComment = { viewModel.submitComment(post.id) },
                    onDelete = { viewModel.deletePost(post.id) },
                    resolvePhoto = viewModel::postImageUrl,
                    onAuthorClick = onAuthorClick
                )
            }

            if (!state.loading && state.posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(br.com.jogatina.R.string.empty_feed),
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

/**
 * Campo com suporte a commitContent: aceita GIF/figurinha do teclado (Gboard)
 * além da galeria — usado no composer do post e no input de comentário.
 * Lê os bytes do content:// na hora, pois a permissão do teclado é temporária
 * (a Uri não serve para preview depois). ByteArray + ImageDecoderDecoder anima GIF.
 */
@Composable
private fun CommitContentField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommitImage: (ByteArray, String, Uri) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    singleLine: Boolean = false,
    maxLines: Int = 5
) {
    val textColor = JogatinaWhite
    val hintColor = JogatinaWhite70

    AndroidView(
        factory = { ctx ->
            CommitContentEditText(ctx).apply {
                background = null
                setTextColor(textColor.toArgb())
                setHintTextColor(hintColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                this.hint = hint
                this.maxLines = maxLines
                isSingleLine = singleLine
                inputType = InputType.TYPE_CLASS_TEXT or
                    (if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE) or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setText(value)
                setTag(COMPOSER_TAG_UPDATING, false)
                onCommitContent = { mime, uri ->
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size in 1..MAX_IMAGE_BYTES) {
                                post { onCommitImage(bytes, mime, uri) }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (getTag(COMPOSER_TAG_UPDATING) == true) return
                        onValueChange(s?.toString().orEmpty())
                    }
                })
            }
        },
        update = { edit ->
            if (edit.text?.toString() != value) {
                edit.setTag(COMPOSER_TAG_UPDATING, true)
                val selStart = edit.selectionStart.coerceAtLeast(0)
                edit.setText(value)
                try {
                    edit.setSelection(selStart.coerceAtMost(value.length))
                } catch (_: Exception) {
                }
                edit.setTag(COMPOSER_TAG_UPDATING, false)
            }
            if (edit.hint?.toString() != hint) edit.hint = hint
        },
        modifier = modifier
    )
}

@Composable
private fun ComposerCard(
    text: String,
    pickedImage: PickedImage?,
    publishing: Boolean,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onCommitImage: (ByteArray, String, Uri) -> Unit,
    onClearImage: () -> Unit,
    onPublish: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CommitContentField(
                value = text,
                onValueChange = onTextChange,
                onCommitImage = onCommitImage,
                hint = stringResource(br.com.jogatina.R.string.composer_hint),
                textSizeSp = 14f,
                singleLine = false,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            if (pickedImage != null) {
                Box {
                    AsyncImage(
                        // Bytes lidos na hora: a content:// do teclado (Gboard) expira
                        // após o releasePermission. ByteArray + ImageDecoderDecoder anima GIF.
                        model = pickedImage.bytes,
                        contentDescription = stringResource(br.com.jogatina.R.string.attached_image),
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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(br.com.jogatina.R.string.remove_image), tint = JogatinaWhite)
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
                    Icon(Icons.Filled.Image, contentDescription = stringResource(br.com.jogatina.R.string.attach_image), tint = JogatinaMagenta)
                }
                TextButton(onClick = onPublish, enabled = !publishing) {
                    if (publishing) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(br.com.jogatina.R.string.publish), color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
    commentImage: PickedImage?,
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
    onCommentImagePicked: (PickedImage?) -> Unit,
    onReplyTo: (CommentDto?) -> Unit,
    onSendComment: () -> Unit,
    onDelete: () -> Unit,
    resolvePhoto: (String?) -> String?,
    onAuthorClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clickable { onAuthorClick(post.authorId) }) {
                    ProfileAvatar(
                        photoUrl = resolvePhoto(post.authorPhotoUrl),
                        name = post.authorName,
                        size = 40.dp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        post.authorName,
                        color = JogatinaWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onAuthorClick(post.authorId) }
                    )
                    Text(timeAgo(post.createdOnUtc), color = JogatinaSubtitle, fontSize = 12.sp)
                }
                if (isMine) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(br.com.jogatina.R.string.delete), tint = JogatinaSubtitle)
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
                    contentDescription = stringResource(br.com.jogatina.R.string.post_image),
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
                                contentDescription = stringResource(br.com.jogatina.R.string.react),
                                tint = JogatinaWhite70
                            )
                        }
                    }
                    TextButton(onClick = onQuickReact, enabled = !reacting) {
                        Text(
                            if (post.myReaction == Reaction.HEART.kind) stringResource(br.com.jogatina.R.string.loved) else stringResource(br.com.jogatina.R.string.love),
                            color = if (post.myReaction != null) JogatinaDiscordRed else JogatinaWhite70,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onToggleComments, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = stringResource(br.com.jogatina.R.string.comments), tint = JogatinaWhite70)
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
                                Text(stringResource(reaction.labelRes), color = JogatinaWhite70, fontSize = 10.sp)
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
                        resolveImage = { resolvePhoto(it) },
                        onReply = { onReplyTo(comment) },
                        onAuthorClick = onAuthorClick
                    )
                    comment.replies.forEach { reply ->
                        CommentRow(
                            comment = reply,
                            photoUrl = resolvePhoto(reply.authorPhotoUrl),
                            indent = true,
                            resolveImage = { resolvePhoto(it) },
                            onReply = null,
                            onAuthorClick = onAuthorClick
                        )
                    }
                }
                if (replyTo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            stringResource(br.com.jogatina.R.string.replying_to, replyTo.authorName),
                            color = JogatinaMagenta,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onReplyTo(null) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(br.com.jogatina.R.string.cancel_reply), tint = JogatinaSubtitle)
                        }
                    }
                }
                if (commentImage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        AsyncImage(
                            // Bytes lidos na hora (GIF do teclado/temp Uri) + decoder global animam.
                            model = commentImage.bytes,
                            contentDescription = stringResource(br.com.jogatina.R.string.attached_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (sendingComment) {
                            CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            IconButton(onClick = { onCommentImagePicked(null) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(br.com.jogatina.R.string.remove_image), tint = JogatinaSubtitle)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CommitContentField(
                        value = commentInput,
                        onValueChange = onCommentInput,
                        onCommitImage = { bytes, mime, uri ->
                            onCommentImagePicked(PickedImage(bytes, mime, uri))
                        },
                        hint = stringResource(br.com.jogatina.R.string.comment_hint),
                        textSizeSp = 13f,
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, JogatinaWhite.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    IconButton(
                        onClick = onSendComment,
                        enabled = !sendingComment && (commentInput.isNotBlank() || commentImage != null)
                    ) {
                        if (sendingComment) {
                            CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(br.com.jogatina.R.string.send), tint = JogatinaMagenta)
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
    resolveImage: (String) -> String?,
    onReply: (() -> Unit)?,
    onAuthorClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 40.dp else 0.dp, top = 6.dp)
    ) {
        Box(modifier = Modifier.clickable { onAuthorClick(comment.authorId) }) {
            ProfileAvatar(photoUrl = photoUrl, name = comment.authorName, size = 30.dp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(JogatinaNavyTop)
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    color = JogatinaWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAuthorClick(comment.authorId) }
                )
                if (onReply != null) {
                    TextButton(onClick = onReply) {
                        Text(stringResource(br.com.jogatina.R.string.reply), color = JogatinaMagenta, fontSize = 12.sp)
                    }
                }
            }
            val (textContent, imageTokens) = splitImageTokens(comment.content)
            if (textContent.isNotBlank()) {
                Text(textContent, color = JogatinaWhite, fontSize = 13.sp)
            }
            imageTokens.forEach { token ->
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = resolveImage(token) ?: token,
                    contentDescription = stringResource(br.com.jogatina.R.string.attached_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 180.dp, height = 140.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Text(timeAgo(comment.createdOnUtc), color = JogatinaSubtitle, fontSize = 11.sp)
        }
    }
}

/** Separa o texto do comentário das URLs de imagem (convenção: URL no conteúdo). */
private fun splitImageTokens(content: String): Pair<String, List<String>> {
    val tokens = content.split("\\s+".toRegex())
    val images = tokens.filter { isImageToken(it) }
    val text = tokens.filterNot { isImageToken(it) }.joinToString(" ").trim()
    return text to images
}

private fun isImageToken(token: String): Boolean {
    val t = token.trim().trim('"', '\'', '(', ')', ',', ';')
    if (t.isBlank()) return false
    val lower = t.lowercase()
    val looksPath = t.startsWith("/uploads/") ||
        lower.startsWith("http://") || lower.startsWith("https://")
    if (!looksPath) return false
    return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
        lower.contains(".gif") || lower.contains(".webp") || t.startsWith("/uploads/")
}

@Composable
fun timeAgo(iso: String): String {
    val minutes: Long? = try {
        Duration.between(Instant.parse(iso), Instant.now()).toMinutes()
    } catch (_: Exception) {
        null
    }
    if (minutes == null) return ""
    return when {
        minutes < 1 -> stringResource(br.com.jogatina.R.string.time_now)
        minutes < 60 -> stringResource(br.com.jogatina.R.string.time_min, minutes.toInt())
        minutes < 60 * 24 -> stringResource(br.com.jogatina.R.string.time_hour, (minutes / 60).toInt())
        minutes < 60 * 24 * 7 -> stringResource(br.com.jogatina.R.string.time_day, (minutes / (60 * 24)).toInt())
        else -> iso.substring(0, 10).split("-").reversed().joinToString("/")
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
                commentsLoading = false, commentInput = "", commentImage = null, replyTo = null,
                reacting = false, pickerOpen = false, sendingComment = false,
                onQuickReact = {}, onOpenPicker = {}, onClosePicker = {}, onReact = {},
                onToggleComments = {}, onCommentInput = {}, onCommentImagePicked = {},
                onReplyTo = {}, onSendComment = {}, onDelete = {},
                resolvePhoto = { it },
                onAuthorClick = {}
            )
        }
    }
}
