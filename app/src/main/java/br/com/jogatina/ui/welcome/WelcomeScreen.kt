package br.com.jogatina.ui.welcome

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import br.com.jogatina.R
import br.com.jogatina.data.auth.AuthRepository
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaMagentaGlow
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaNavyTop
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tela de boas-vindas — segue o mockup:
 * - Fundo gradiente azul-marinho profundo.
 * - Faixas angulares magenta neon atrás do lutador (terço superior).
 * - Lutador grande centralizado (drawable/fighter_pixel.png, sem fundo).
 * - Título "Conecte-se e organize suas jogatinas" (branco, negrito).
 * - Subtítulo "Crie grupos para jogar seus games favoritos com seus amigos".
 * - Botão branco "Entrar com Google" (Credential Manager + ID token -> POST auth/social).
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gerrUnavailableFmt = stringResource(R.string.gerr_unavailable)
    val gerrFailedFmt = stringResource(R.string.gerr_failed)
    val googleStrings = GoogleUiStrings(
        token = stringResource(R.string.gerr_token),
        invalid = stringResource(R.string.gerr_invalid),
        cancelled = stringResource(R.string.gerr_cancelled),
        none = stringResource(R.string.gerr_none),
        unsupported = stringResource(R.string.gerr_unsupported),
        interrupted = stringResource(R.string.gerr_interrupted),
        unavailable = { type -> String.format(gerrUnavailableFmt, type ?: "?") },
        failed = { msg -> String.format(gerrFailedFmt, msg) }
    )

    WelcomeContent(
        loading = state.loading,
        error = state.error,
        emailMode = state.emailMode,
        showEmailForm = state.showEmailForm,
        onGoogleEnter = {
            viewModel.clearError()
            scope.launch {
                signInWithGoogle(context, viewModel, googleStrings)
            }
        },
        onShowEmail = viewModel::showEmailForm,
        onDismissEmail = viewModel::dismissEmailForm,
        onClearError = viewModel::clearError,
        onLogin = viewModel::login,
        onRegister = viewModel::register,
        modifier = modifier
    )
}

/** Textos localizados do fluxo Google (resolvidos no composable chamador). */
private data class GoogleUiStrings(
    val token: String,
    val invalid: String,
    val cancelled: String,
    val none: String,
    val unsupported: String,
    val interrupted: String,
    val unavailable: (String?) -> String,
    val failed: (String) -> String
)

/**
 * Fluxo Google: Credential Manager -> Google ID token -> backend (auth/social, provider="google").
 */
private suspend fun signInWithGoogle(context: Context, viewModel: WelcomeViewModel, strings: GoogleUiStrings) {
    val serverClientId = AuthRepository.GOOGLE_SERVER_CLIENT_ID
    if (serverClientId.startsWith("SEU_")) {
        viewModel.onSocialError("Configure o GOOGLE_SERVER_CLIENT_ID para ativar o login Google.")
        return
    }
    try {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(context, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val idToken = googleCredential.idToken
        if (idToken.isBlank()) {
            viewModel.onSocialError(strings.token)
        } else {
            viewModel.socialLoginGoogle(idToken)
        }
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG_GOOGLE, "parsing", e)
        viewModel.onSocialError(strings.invalid)
    } catch (_: GetCredentialCancellationException) {
        Log.d(TAG_GOOGLE, "cancelado pelo usuário")
        viewModel.onSocialError(strings.cancelled)
    } catch (e: NoCredentialException) {
        Log.e(TAG_GOOGLE, "sem credencial", e)
        viewModel.onSocialError(strings.none)
    } catch (e: GetCredentialUnsupportedException) {
        Log.e(TAG_GOOGLE, "não suportado", e)
        viewModel.onSocialError(strings.unsupported)
    } catch (e: GetCredentialInterruptedException) {
        Log.e(TAG_GOOGLE, "interrompido", e)
        viewModel.onSocialError(strings.interrupted)
    } catch (e: GetCredentialException) {
        Log.e(TAG_GOOGLE, "getCredential: ${e.type} ${e.message}", e)
        viewModel.onSocialError(strings.unavailable(e.type))
    } catch (e: Exception) {
        Log.e(TAG_GOOGLE, "falha", e)
        viewModel.onSocialError(strings.failed(e.message ?: "?"))
    }
}

private const val TAG_GOOGLE = "JogatinaGoogle"

@Suppress("UNUSED_PARAMETER")
@Composable
fun WelcomeContent(
    loading: Boolean,
    error: String?,
    emailMode: EmailMode,
    showEmailForm: Boolean,
    onGoogleEnter: () -> Unit,
    onShowEmail: (EmailMode) -> Unit,
    onDismissEmail: () -> Unit,
    onClearError: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JogatinaNavyTop, JogatinaNavyMid, JogatinaNavyBottom)
                )
            )
    ) {
        // Entrada (fade + subida) + idle do lutador (flutuar, respirar, halo, brilho).
        var entered by remember { mutableStateOf(false) }
        var enteredTexts by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            entered = true
            kotlinx.coroutines.delay(200.milliseconds)
            enteredTexts = true
        }
        val enterProgress by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 900),
            label = "enter"
        )
        val textsProgress by animateFloatAsState(
            targetValue = if (enteredTexts) 1f else 0f,
            animationSpec = tween(durationMillis = 700),
            label = "enterTexts"
        )
        val idle = rememberInfiniteTransition(label = "fighterIdle")
        val floatPx by idle.animateFloat(
            initialValue = 0f,
            targetValue = -26f,
            animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
            label = "float"
        )
        val breathe by idle.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
            label = "breathe"
        )
        val haloAlpha by idle.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
            label = "halo"
        )
        val shineX by idle.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(3800), RepeatMode.Restart),
            label = "shine"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Arte: faixas neon ao fundo + lutador grande por cima.
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (1f - enterProgress) * 120f
                        val scale = 0.92f + 0.08f * enterProgress
                        scaleX = scale
                        scaleY = scale
                        alpha = enterProgress
                    }
            ) {
                NeonStripes(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.TopCenter)
                )
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    JogatinaMagenta.copy(alpha = haloAlpha),
                                    JogatinaMagenta.copy(alpha = haloAlpha * 0.25f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                // Lutador animado: guarda (k0) + chute alto a cada ~4s.
                // (Fundo estático como fallback enquanto os frames carregam.)
                FighterKickCycle(
                    floatPx = floatPx,
                    breathe = breathe,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    rotate(degrees = -18f, pivot = center) {
                        val cx = center.x + shineX * w * 0.6f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.13f),
                                    Color.Transparent
                                ),
                                startX = cx - 90f,
                                endX = cx + 90f
                            ),
                            topLeft = Offset(0f, -h * 0.2f),
                            size = Size(w, h * 1.4f)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textsProgress
                    translationY = (1f - textsProgress) * 40f
                }
            ) {
            Text(
                text = stringResource(R.string.welcome_title),
                color = JogatinaWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                color = JogatinaSubtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            }
            Spacer(modifier = Modifier.height(26.dp))

            if (error != null) {
                Text(
                    text = error,
                    color = JogatinaMagentaGlow,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // Botão Google oculto até o OAuth funcionar (fluxo mantido no código).
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(textsProgress)
            ) {
                TextButton(onClick = { onShowEmail(EmailMode.LOGIN) }) {
                    Text(stringResource(R.string.email_enter), color = JogatinaWhite70, fontSize = 13.sp)
                }
                Text("•", color = JogatinaWhite70, fontSize = 13.sp)
                TextButton(onClick = { onShowEmail(EmailMode.REGISTER) }) {
                    Text(stringResource(R.string.create_account), color = JogatinaWhite, fontSize = 13.sp)
                }
            }
        }
    }

    if (showEmailForm) {
        EmailAuthDialog(
            mode = emailMode,
            loading = loading,
            onModeChange = onShowEmail,
            onDismiss = onDismissEmail,
            onLogin = onLogin,
            onRegister = onRegister
        )
    }
}

/** Botão branco padrão Google: logo "G" colorido + "Entrar com Google". */
@Suppress("unused")
@Composable
private fun GoogleEnterButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = JogatinaWhite,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = JogatinaWhite.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = JogatinaMagenta,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.google_enter),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F1F1F)
                )
            }
        }
    }
}

/** Painel de faixas angulares magenta neon atrás do lutador. */
private data class Stripe(val x: Float, val y: Float, val thickness: Float, val alpha: Float)

/**
 * Lutador animado a partir da sprite sheet (fighter_k0..k7):
 * guarda + chute alto a cada ~4s, pixel nítido (sem filtro).
 */
@Suppress("ContextGetResources", "LocalContextResources")
@Composable
private fun FighterKickCycle(
    floatPx: Float,
    breathe: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fighterDesc = stringResource(R.string.fighter_desc)
    var frames by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var kickFrame by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.Default) {
            val resources = context.resources
            FighterKickFrames.map { res ->
                BitmapFactory.decodeResource(resources, res).asImageBitmap()
            }
        }
        frames = loaded
        while (true) {
            kotlinx.coroutines.delay(4200.milliseconds)
            for (f in 1..7) {
                kickFrame = f
                kotlinx.coroutines.delay(95.milliseconds)
            }
            kickFrame = null
        }
    }

    val frameBitmap = kickFrame?.let { frames.getOrNull(it) }
    if (frameBitmap != null) {
        Image(
            bitmap = frameBitmap,
            contentDescription = fighterDesc,
            modifier = modifier
                .offset { IntOffset(0, floatPx.roundToInt()) }
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                },
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            filterQuality = FilterQuality.None
        )
    } else if (frames.isNotEmpty()) {
        Image(
            bitmap = frames[0],
            contentDescription = fighterDesc,
            modifier = modifier
                .offset { IntOffset(0, floatPx.roundToInt()) }
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                },
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            filterQuality = FilterQuality.None
        )
    } else {
        Image(
            painter = painterResource(R.drawable.fighter_pixel),
            contentDescription = fighterDesc,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}

private val FighterKickFrames = listOf(
    R.drawable.fighter_k0,
    R.drawable.fighter_k1,
    R.drawable.fighter_k2,
    R.drawable.fighter_k3,
    R.drawable.fighter_k4,
    R.drawable.fighter_k5,
    R.drawable.fighter_k6,
    R.drawable.fighter_k7
)

@Composable
private fun NeonStripes(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Faixa larga translúcida + barras sólidas diagonais.
        val bars = listOf(
            Stripe(-0.20f * w, 0.10f * h, 96f, 0.30f),
            Stripe(-0.10f * w, 0.02f * h, 44f, 1f),
            Stripe(0.12f * w, -0.04f * h, 44f, 1f),
            Stripe(0.34f * w, -0.10f * h, 26f, 1f)
        )
        bars.forEachIndexed { i, (x, y, thickness, alpha) ->
            rotate(degrees = -18f, pivot = Offset(w / 2, h / 2)) {
                drawRect(
                    color = JogatinaMagenta.copy(alpha = 0.25f),
                    topLeft = Offset(x - 10, y - 10),
                    size = Size(w * 1.2f, thickness + 20)
                )
                drawRect(
                    color = (if (i == 0) JogatinaMagentaGlow else JogatinaMagenta).copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(w * 1.2f, thickness)
                )
            }
        }
        // Filete vertical sólido na borda direita (como no mockup).
        drawRect(
            color = JogatinaMagenta,
            topLeft = Offset(w - 12.dp.toPx(), 0f),
            size = Size(12.dp.toPx(), h * 0.95f)
        )
        // Véu escuro para fundir com o fundo.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, JogatinaNavyTop.copy(alpha = 0.9f)),
                startY = h * 0.55f,
                endY = h
            )
        )
    }
}

@Composable
private fun EmailAuthDialog(
    mode: EmailMode,
    loading: Boolean,
    onModeChange: (EmailMode) -> Unit,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isRegister = mode == EmailMode.REGISTER
    val canSubmit = email.isNotBlank() && password.length >= 6 &&
        (!isRegister || (firstName.isNotBlank() && lastName.isNotBlank()))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JogatinaNavyMid)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isRegister) stringResource(R.string.auth_title_register)
                else stringResource(R.string.auth_title_login),
                color = JogatinaWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRegister) "POST auth/register" else "POST auth/login",
                color = JogatinaWhite70,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthField(stringResource(R.string.auth_field_email), email, { email = it })
            if (isRegister) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField(stringResource(R.string.auth_field_first), firstName, { firstName = it }, Modifier.weight(1f))
                    AuthField(stringResource(R.string.auth_field_last), lastName, { lastName = it }, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AuthField(stringResource(R.string.auth_field_password), password, { password = it }, isPassword = true)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isRegister) onRegister(email.trim(), firstName.trim(), lastName.trim(), password)
                    else onLogin(email.trim(), password)
                },
                enabled = canSubmit && !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JogatinaMagenta,
                    contentColor = JogatinaWhite
                )
            ) {
                if (loading) CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text(
                    if (isRegister) stringResource(R.string.auth_submit_register)
                    else stringResource(R.string.auth_submit_login),
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = { onModeChange(if (isRegister) EmailMode.LOGIN else EmailMode.REGISTER) }) {
                Text(
                    if (isRegister) stringResource(R.string.auth_have_account)
                    else stringResource(R.string.create_account),
                    color = JogatinaWhite70,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = JogatinaWhite70, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = JogatinaWhite,
            unfocusedTextColor = JogatinaWhite,
            focusedBorderColor = JogatinaMagenta,
            unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
            cursorColor = JogatinaMagenta
        )
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WelcomePreview() {
    JogatinaTheme {
        WelcomeContent(
            loading = false,
            error = null,
            emailMode = EmailMode.LOGIN,
            showEmailForm = false,
            onGoogleEnter = {},
            onShowEmail = {},
            onDismissEmail = {},
            onClearError = {},
            onLogin = { _, _ -> },
            onRegister = { _, _, _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Dialog registro")
@Composable
private fun WelcomeDialogPreview() {
    JogatinaTheme {
        Box(Modifier.fillMaxSize().background(JogatinaNavyBottom)) {
            EmailAuthDialog(
                mode = EmailMode.REGISTER,
                loading = false,
                onModeChange = {},
                onDismiss = {},
                onLogin = { _, _ -> },
                onRegister = { _, _, _, _ -> }
            )
        }
    }
}

// Fim da WelcomeScreen.
