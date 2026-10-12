package br.com.jogatina.ui.chat

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

private const val R_TAG_UPDATING = 0x7F0B0001

/**
 * EditText que aceita imagens do teclado (Gboard -> GIF, figurinhas, stickers)
 * via commitContent (InputConnectionCompat).
 *
 * O Jetpack Compose (OutlinedTextField) ignora esse conteúdo; por isso o input
 * da conversa usa este View via AndroidView.
 */
class CommitContentEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : android.widget.EditText(context, attrs, defStyleAttr) {

    var supportedMimeTypes: Array<String> =
        arrayOf("image/png", "image/jpeg", "image/gif", "image/webp")

    /** (mimeType, contentUri) — o caller deve ler os bytes IMEDIATAMENTE e liberar a permissão. */
    var onCommitContent: ((mimeType: String, uri: Uri) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(outAttrs, supportedMimeTypes)
        return InputConnectionCompat.createWrapper(
            ic,
            outAttrs,
            object : InputConnectionCompat.OnCommitContentListener {
                override fun onCommitContent(
                    inputContentInfo: InputContentInfoCompat,
                    flags: Int,
                    opts: android.os.Bundle?
                ): Boolean {
                    try {
                        inputContentInfo.requestPermission()
                    } catch (_: Exception) {
                        return false
                    }
                    val desc = inputContentInfo.description
                    var mime: String? = null
                    for (i in 0 until desc.mimeTypeCount) {
                        val candidate = desc.getMimeType(i)
                        if (supportedMimeTypes.any {
                                it.equals(candidate, ignoreCase = true) ||
                                    (it == "image/jpeg" && candidate.equals("image/jpg", ignoreCase = true))
                            } || candidate.startsWith("image/", ignoreCase = true)
                        ) {
                            mime = candidate
                            break
                        }
                    }
                    val resolved = mime ?: "image/gif"
                    try {
                        onCommitContent?.invoke(resolved, inputContentInfo.contentUri)
                    } finally {
                        try {
                            inputContentInfo.releasePermission()
                        } catch (_: Exception) {
                        }
                    }
                    return true
                }
            }
        )
    }
}

/**
 * Campo de mensagem com suporte a commitContent + visual próximo ao OutlinedTextField.
 * Lê os bytes do content:// na hora (a permissão do teclado é temporária).
 */
@Composable
fun ChatMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onCommitImage: (bytes: ByteArray, mimeType: String, previewUri: Uri) -> Unit,
    onSendAction: () -> Unit = {},
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = Color.White
    val hintColor = Color.White.copy(alpha = 0.6f)
    val borderColor = remember { androidx.compose.runtime.mutableStateOf(Color.White.copy(alpha = 0.25f)) }

    // Guarda a última Uri para preview (o caller copia os bytes; a Uri pode expirar).
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, borderColor.value, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                CommitContentEditText(ctx).apply {
                    background = null
                    setTextColor(textColor.toArgb())
                    setHintTextColor(hintColor.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    hint = placeholder
                    isSingleLine = false
                    maxLines = 4
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    imeOptions = EditorInfo.IME_ACTION_SEND
                    setText(value)
                    isEnabled = enabled
                    // Evita loop: update() marca via tag enquanto ajusta o texto programaticamente.
                    setTag(R_TAG_UPDATING, false)
                    setOnFocusChangeListener { _, hasFocus ->
                        borderColor.value =
                            if (hasFocus) Color(0xFFE040FB) else Color.White.copy(alpha = 0.25f)
                    }
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            onSendAction()
                            true
                        } else false
                    }
                    onCommitContent = { mime, uri ->
                        try {
                            ctx.contentResolver.openInputStream(uri)?.use { input ->
                                val bytes = input.readBytes()
                                if (bytes.size in 1..5 * 1024 * 1024) {
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
                            if (getTag(R_TAG_UPDATING) == true) return
                            onValueChange(s?.toString().orEmpty())
                        }
                    })
                }
            },
            update = { edit: CommitContentEditText ->
                if (edit.text?.toString() != value) {
                    edit.setTag(R_TAG_UPDATING, true)
                    val selStart = edit.selectionStart.coerceAtLeast(0)
                    edit.setText(value)
                    try {
                        edit.setSelection(selStart.coerceAtMost(value.length))
                    } catch (_: Exception) {
                    }
                    edit.setTag(R_TAG_UPDATING, false)
                }
                if (edit.hint?.toString() != placeholder) edit.hint = placeholder
                if (edit.isEnabled != enabled) edit.isEnabled = enabled
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    @Suppress("UNUSED_EXPRESSION") context
}
