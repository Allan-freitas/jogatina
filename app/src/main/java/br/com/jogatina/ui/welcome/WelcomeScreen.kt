package br.com.jogatina.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.jogatina.R
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaMagentaGlow
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaNavyTop
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70

/**
 * Tela de boas-vindas — segue o mockup:
 * - Fundo gradiente azul-marinho profundo.
 * - Faixas angulares magenta neon atrás do lutador (terço superior).
 * - Lutador grande centralizado (drawable/fighter_pixel.png, sem fundo).
 * - Título "Conecte-se e organize suas jogatinas" (branco, negrito).
 * - Subtítulo "Crie grupos para jogar seus games favoritos com seus amigos".
 * - Botão vermelho dividido: ícone Discord + "Entrar com Discord".
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    WelcomeContent(
        loading = state.loading,
        error = state.error,
        emailMode = state.emailMode,
        showEmailForm = state.showEmailForm,
        onDiscordEnter = viewModel::onDiscordEnter,
        onShowEmail = viewModel::showEmailForm,
        onDismissEmail = viewModel::dismissEmailForm,
        onClearError = viewModel::clearError,
        onLogin = viewModel::login,
        onRegister = viewModel::register,
        modifier = modifier
    )
}

@Composable
fun WelcomeContent(
    loading: Boolean,
    error: String?,
    emailMode: EmailMode,
    showEmailForm: Boolean,
    onDiscordEnter: () -> Unit,
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
            ) {
                NeonStripes(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.TopCenter)
                )
                Image(
                    painter = painterResource(R.drawable.fighter_pixel),
                    contentDescription = "Lutador",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            }

            Text(
                text = "Conecte-se e organize suas jogatinas",
                color = JogatinaWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Crie grupos para jogar seus games favoritos com seus amigos",
                color = JogatinaSubtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
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
            DiscordEnterButton(
                loading = loading,
                onClick = { onClearError(); onDiscordEnter() }
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onShowEmail(EmailMode.LOGIN) }) {
                    Text("Entrar com e-mail", color = JogatinaWhite70, fontSize = 13.sp)
                }
                Text("•", color = JogatinaWhite70, fontSize = 13.sp)
                TextButton(onClick = { onShowEmail(EmailMode.REGISTER) }) {
                    Text("Criar conta", color = JogatinaWhite, fontSize = 13.sp)
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

/** Botão vermelho dividido do mockup: segmento do ícone + "Entrar com Discord". */
@Composable
private fun DiscordEnterButton(
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
            containerColor = JogatinaDiscordRed,
            contentColor = JogatinaWhite,
            disabledContainerColor = JogatinaDiscordRed.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = JogatinaWhite,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        modifier = Modifier.size(26.dp),
                        tint = JogatinaWhite
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.6f)
                        .background(JogatinaWhite.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Entrar com Discord",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Painel de faixas angulares magenta neon atrás do lutador. */
private data class Stripe(val x: Float, val y: Float, val thickness: Float, val alpha: Float)

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
                    size = androidx.compose.ui.geometry.Size(w * 1.2f, thickness + 20)
                )
                drawRect(
                    color = (if (i == 0) JogatinaMagentaGlow else JogatinaMagenta).copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w * 1.2f, thickness)
                )
            }
        }
        // Filete vertical sólido na borda direita (como no mockup).
        drawRect(
            color = JogatinaMagenta,
            topLeft = Offset(w - 12.dp.toPx(), 0f),
            size = androidx.compose.ui.geometry.Size(12.dp.toPx(), h * 0.95f)
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
                text = if (isRegister) "Criar conta" else "Entrar com e-mail",
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
            AuthField("E-mail", email, { email = it })
            if (isRegister) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField("Nome", firstName, { firstName = it }, Modifier.weight(1f))
                    AuthField("Sobrenome", lastName, { lastName = it }, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AuthField("Senha (mín. 6)", password, { password = it }, isPassword = true)
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
                else Text(if (isRegister) "Cadastrar" else "Entrar", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { onModeChange(if (isRegister) EmailMode.LOGIN else EmailMode.REGISTER) }) {
                Text(
                    if (isRegister) "Já tenho conta" else "Criar conta",
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
            onDiscordEnter = {},
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
