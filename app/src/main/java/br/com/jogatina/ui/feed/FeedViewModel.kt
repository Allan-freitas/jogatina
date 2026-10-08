package br.com.jogatina.ui.feed

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.TokenStore
import br.com.jogatina.data.feed.CommentDto
import br.com.jogatina.data.feed.FeedRepository
import br.com.jogatina.data.feed.PostDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PickedImage(val bytes: ByteArray, val mimeType: String, val previewUri: Uri)

data class FeedUiState(
    val posts: List<PostDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val composerText: String = "",
    val pickedImage: PickedImage? = null,
    val publishing: Boolean = false,
    val myUserId: String? = null,
    val comments: Map<String, List<CommentDto>> = emptyMap(),
    val expanded: Set<String> = emptySet(),
    val commentsLoading: Set<String> = emptySet(),
    val commentInputs: Map<String, String> = emptyMap(),
    val replyTo: Map<String, CommentDto?> = emptyMap(),
    val liking: Set<String> = emptySet(),
    val sendingComment: Set<String> = emptySet(),
    val deleting: Set<String> = emptySet()
)

class FeedViewModel(
    private val feed: FeedRepository,
    private val tokens: TokenStore,
    private val onAuthExpired: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState(myUserId = tokens.userId))
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    init {
        refresh(first = true)
    }

    fun refresh(first: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        _state.value = current.copy(
            loading = first,
            refreshing = !first,
            error = null
        )
        viewModelScope.launch {
            when (val r = feed.getFeed(1, 20)) {
                is AuthResult.Success -> _state.value = _state.value.copy(
                    posts = r.value,
                    loading = false,
                    refreshing = false
                )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(loading = false, refreshing = false)
                    handleError(r)
                }
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun postImageUrl(relative: String?): String? = feed.imageUrl(relative)

    fun onComposerChange(text: String) {
        _state.value = _state.value.copy(composerText = text)
    }

    fun onImagePicked(image: PickedImage?) {
        _state.value = _state.value.copy(pickedImage = image)
    }

    fun submitPost() {
        val current = _state.value
        if (current.publishing) return
        val text = current.composerText.trim()
        if (text.isBlank() && current.pickedImage == null) {
            _state.value = current.copy(error = "Escreva algo ou anexe uma imagem.")
            return
        }
        _state.value = current.copy(publishing = true, error = null)
        viewModelScope.launch {
            var imageUrl: String? = null
            current.pickedImage?.let { img ->
                when (val up = feed.uploadImage(img.bytes, img.mimeType)) {
                    is AuthResult.Success -> imageUrl = up.value
                    is AuthResult.Error -> {
                        _state.value = _state.value.copy(publishing = false)
                        handleError(up)
                        return@launch
                    }
                }
            }
            when (val r = feed.createPost(text.ifBlank { null }, imageUrl)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(
                        composerText = "",
                        pickedImage = null,
                        publishing = false
                    )
                    refresh()
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(publishing = false)
                    handleError(r)
                }
            }
        }
    }

    fun toggleLike(postId: String) {
        if (_state.value.liking.contains(postId)) return
        _state.value = _state.value.copy(liking = _state.value.liking + postId)
        viewModelScope.launch {
            when (val r = feed.toggleLike(postId)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        posts = _state.value.posts.map { p ->
                            if (p.id == postId) p.copy(likedByMe = r.value.liked, likeCount = r.value.likeCount)
                            else p
                        },
                        liking = _state.value.liking - postId
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(liking = _state.value.liking - postId)
                    handleError(r)
                }
            }
        }
    }

    fun toggleComments(postId: String) {
        val expanded = _state.value.expanded
        _state.value = _state.value.copy(
            expanded = if (expanded.contains(postId)) expanded - postId else expanded + postId
        )
        if (!expanded.contains(postId) && !_state.value.comments.containsKey(postId)) {
            loadComments(postId)
        }
    }

    fun loadComments(postId: String) {
        if (_state.value.commentsLoading.contains(postId)) return
        _state.value = _state.value.copy(commentsLoading = _state.value.commentsLoading + postId)
        viewModelScope.launch {
            when (val r = feed.getComments(postId)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        comments = _state.value.comments + (postId to r.value),
                        commentsLoading = _state.value.commentsLoading - postId
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(commentsLoading = _state.value.commentsLoading - postId)
                    handleError(r)
                }
            }
        }
    }

    fun onCommentInput(postId: String, text: String) {
        _state.value = _state.value.copy(
            commentInputs = _state.value.commentInputs + (postId to text)
        )
    }

    fun setReplyTo(postId: String, parent: CommentDto?) {
        _state.value = _state.value.copy(replyTo = _state.value.replyTo + (postId to parent))
    }

    fun submitComment(postId: String) {
        val text = (_state.value.commentInputs[postId] ?: "").trim()
        if (text.isBlank() || _state.value.sendingComment.contains(postId)) return
        val parent = _state.value.replyTo[postId]
        _state.value = _state.value.copy(sendingComment = _state.value.sendingComment + postId)
        viewModelScope.launch {
            when (val r = feed.addComment(postId, text, parent?.id)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        commentInputs = _state.value.commentInputs + (postId to ""),
                        replyTo = _state.value.replyTo + (postId to null),
                        sendingComment = _state.value.sendingComment - postId,
                        posts = _state.value.posts.map { p ->
                            if (p.id == postId) p.copy(commentCount = p.commentCount + 1) else p
                        }
                    ).also { loadComments(postId) }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(sendingComment = _state.value.sendingComment - postId)
                    handleError(r)
                }
            }
        }
    }

    fun deletePost(postId: String) {
        if (_state.value.deleting.contains(postId)) return
        _state.value = _state.value.copy(deleting = _state.value.deleting + postId)
        viewModelScope.launch {
            when (val r = feed.deletePost(postId)) {
                is AuthResult.Success ->
                    _state.value = _state.value.copy(
                        posts = _state.value.posts.filterNot { it.id == postId },
                        deleting = _state.value.deleting - postId
                    )
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(deleting = _state.value.deleting - postId)
                    handleError(r)
                }
            }
        }
    }

    private fun handleError(error: AuthResult.Error) {
        if (error.statusCode == 401) {
            onAuthExpired()
        } else {
            _state.value = _state.value.copy(error = error.message)
        }
    }

    companion object {
        fun factory(
            feed: FeedRepository,
            tokens: TokenStore,
            onAuthExpired: () -> Unit
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FeedViewModel(feed, tokens, onAuthExpired) as T
            }
    }
}
