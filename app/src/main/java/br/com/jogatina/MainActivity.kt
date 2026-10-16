package br.com.jogatina

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthRepository
import br.com.jogatina.data.auth.AuthResult
import br.com.jogatina.data.auth.TokenStore
import br.com.jogatina.data.feed.FeedRepository
import br.com.jogatina.ui.feed.FeedScreen
import br.com.jogatina.ui.feed.FeedViewModel
import br.com.jogatina.data.users.UserRepository
import br.com.jogatina.data.games.GamesRepository
import br.com.jogatina.ui.games.CatalogScreen
import br.com.jogatina.ui.games.CatalogViewModel
import br.com.jogatina.ui.games.LibraryScreen
import br.com.jogatina.ui.games.LibraryViewModel
import br.com.jogatina.data.notifications.NotificationsRepository
import br.com.jogatina.notifications.SystemNotifier
import br.com.jogatina.ui.notifications.NotificationsScreen
import br.com.jogatina.ui.notifications.NotificationsViewModel
import br.com.jogatina.ui.social.FriendsScreen
import br.com.jogatina.ui.social.FriendsViewModel
import br.com.jogatina.data.chat.ChatRepository
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.data.social.FriendsRepository
import br.com.jogatina.ui.chat.ChatListScreen
import br.com.jogatina.ui.chat.ChatListViewModel
import br.com.jogatina.ui.chat.ConversationScreen
import br.com.jogatina.ui.chat.ConversationViewModel
import br.com.jogatina.ui.profile.AuthorProfileScreen
import br.com.jogatina.ui.profile.AuthorProfileViewModel
import br.com.jogatina.ui.profile.ProfileScreen
import br.com.jogatina.ui.profile.ProfileViewModel
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.welcome.WelcomeScreen
import br.com.jogatina.ui.welcome.WelcomeViewModel

class MainActivity : ComponentActivity() {

    private val openNotifications = mutableStateOf(false)

    private val apiClient by lazy { ApiClient(AuthRepository.DEFAULT_BASE_URL) }
    private val authRepository by lazy { AuthRepository() }
    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val feedRepository by lazy {
        FeedRepository(apiClient) { tokenStore.accessToken }
    }
    private val userRepository by lazy {
        UserRepository(apiClient) { tokenStore.accessToken }
    }
    private val gamesRepository by lazy {
        GamesRepository(apiClient) { tokenStore.accessToken }
    }
    private val notificationsRepository by lazy {
        NotificationsRepository(apiClient) { tokenStore.accessToken }
    }
    private val chatRepository by lazy {
        ChatRepository(apiClient) { tokenStore.accessToken }
    }
    private val friendsRepository by lazy {
        FriendsRepository(apiClient) { tokenStore.accessToken }
    }
    private val welcomeViewModel: WelcomeViewModel by viewModels {
        WelcomeViewModel.factory(authRepository, tokenStore, applicationContext)
    }
    private val feedViewModel: FeedViewModel by viewModels {
        FeedViewModel.factory(feedRepository, userRepository, tokenStore, applicationContext) {
            welcomeViewModel.logout()
        }
    }
    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(
            gamesRepository,
            friendsRepository,
            applicationContext,
            onPostPublished = { feedViewModel.refresh() }
        ) {
            welcomeViewModel.logout()
        }
    }
    private val notificationsViewModel: NotificationsViewModel by viewModels {
        NotificationsViewModel.factory(
            notificationsRepository,
            AuthRepository.DEFAULT_BASE_URL,
            { tokenStore.accessToken },
            { apiClient.tokenRefresher?.invoke() },
            applicationContext
        ) {
            welcomeViewModel.logout()
        }
    }
    private val chatListViewModel: ChatListViewModel by viewModels {
        ChatListViewModel.factory(chatRepository, friendsRepository, tokenStore) {
            welcomeViewModel.logout()
        }
    }
    private val friendsViewModel: FriendsViewModel by viewModels {
        FriendsViewModel.factory(friendsRepository) {
            welcomeViewModel.logout()
        }
    }
    private val profileViewModel: ProfileViewModel by viewModels {
        ProfileViewModel.factory(userRepository) {
            welcomeViewModel.logout()
        }
    }
    private val catalogViewModel: CatalogViewModel by viewModels {
        CatalogViewModel.factory(
            gamesRepository,
            inLibraryIds = { libraryViewModel.state.value.games.map { it.gameId }.toSet() },
            onLibraryChanged = { libraryViewModel.refresh() },
            onAuthExpired = { welcomeViewModel.logout() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sessão com renovação automática: 401 -> POST auth/refresh-token -> retry.
        // O AuthRepository usa um ApiClient interno próprio (sem hooks), sem risco de loop.
        apiClient.tokenProvider = { tokenStore.accessToken }
        apiClient.tokenRefresher = {
            val currentRefresh = tokenStore.refreshToken
            if (currentRefresh.isNullOrBlank()) {
                null
            } else when (val r = authRepository.refresh(currentRefresh)) {
                is AuthResult.Success -> {
                    tokenStore.save(r.value)
                    r.value.accessToken
                }
                is AuthResult.Error -> null
            }
        }
        enableEdgeToEdge()
        SystemNotifier.ensureChannel(applicationContext)
        openNotifications.value = intent.getBooleanExtra(SystemNotifier.EXTRA_OPEN_NOTIFICATIONS, false)
        setContent {
            JogatinaTheme {
                val welcomeState by welcomeViewModel.state.collectAsState()

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(welcomeState.loggedIn) {
                    if (welcomeState.loggedIn &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (welcomeState.loggedIn) {
                    JogatinaApp(
                        feedViewModel = feedViewModel,
                        libraryViewModel = libraryViewModel,
                        catalogViewModel = catalogViewModel,
                        notificationsViewModel = notificationsViewModel,
                        profileViewModel = profileViewModel,
                        chatListViewModel = chatListViewModel,
                        friendsViewModel = friendsViewModel,
                        userRepository = userRepository,
                        gamesRepository = gamesRepository,
                        chatRepository = chatRepository,
                        tokenStore = tokenStore,
                        apiBaseUrl = AuthRepository.DEFAULT_BASE_URL,
                        tokenRefresher = { apiClient.tokenRefresher?.invoke() },
                        openNotifications = openNotifications.value,
                        onOpenNotificationsHandled = { openNotifications.value = false },
                        onLogout = { welcomeViewModel.logout() }
                    )
                } else {
                    WelcomeScreen(viewModel = welcomeViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(SystemNotifier.EXTRA_OPEN_NOTIFICATIONS, false)) {
            openNotifications.value = true
        }
    }
}

@PreviewScreenSizes
@Composable
fun JogatinaApp(
    feedViewModel: FeedViewModel? = null,
    libraryViewModel: LibraryViewModel? = null,
    catalogViewModel: CatalogViewModel? = null,
    notificationsViewModel: NotificationsViewModel? = null,
    profileViewModel: ProfileViewModel? = null,
    chatListViewModel: ChatListViewModel? = null,
    friendsViewModel: FriendsViewModel? = null,
    userRepository: UserRepository? = null,
    gamesRepository: GamesRepository? = null,
    chatRepository: ChatRepository? = null,
    tokenStore: TokenStore? = null,
    apiBaseUrl: String = "",
    tokenRefresher: (suspend () -> String?)? = null,
    openNotifications: Boolean = false,
    onOpenNotificationsHandled: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showChatList by rememberSaveable { mutableStateOf(false) }
    var showFriends by rememberSaveable { mutableStateOf(false) }
    var selectedAuthorId by rememberSaveable { mutableStateOf<String?>(null) }
    var openConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var newChatFriend by remember { mutableStateOf<FriendDto?>(null) }
    val notificationsState = notificationsViewModel?.state?.collectAsState()?.value
    val chatListState = chatListViewModel?.state?.collectAsState()?.value

    // Veio de um toque na notificação do sistema: abre a tela de notificações.
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            notificationsViewModel?.refresh()
            showNotifications = true
            onOpenNotificationsHandled()
        }
    }

    // Recarrega o feed ao entrar na Home, para mostrar posts criados em outras telas
    // (ex.: post gerado ao marcar amigos para jogar na biblioteca).
    LaunchedEffect(currentDestination) {
        if (currentDestination == AppDestinations.HOME) {
            feedViewModel?.refresh()
        }
    }

    // Recarrega as notificações a cada navegação (home/perfil/favoritos/abrir telas),
    // então o sino reflete o que chegou mesmo sem abrir a lista.
    LaunchedEffect(
        currentDestination,
        showNotifications,
        showCatalog,
        showChatList,
        showFriends,
        selectedAuthorId,
        openConversationId
    ) {
        notificationsViewModel?.refresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = br.com.jogatina.ui.theme.JogatinaNavyBottom,
        bottomBar = {
            GamerBottomBar(
                selected = currentDestination,
                onSelect = { currentDestination = it }
            )
        }
    ) { innerPadding ->
            val conversationKey = openConversationId ?: newChatFriend?.let { "new:${it.friendId}" }
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            val authorKey = selectedAuthorId
            when {
                authorKey != null && userRepository != null && gamesRepository != null -> {
                    val authorVm: AuthorProfileViewModel = viewModel(
                        key = "author:$authorKey",
                        factory = AuthorProfileViewModel.factory(
                            userRepository,
                            gamesRepository,
                            authorKey,
                            onLogout
                        )
                    )
                    AuthorProfileScreen(
                        viewModel = authorVm,
                        onBack = { selectedAuthorId = null },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                conversationKey != null && chatRepository != null && tokenStore != null -> {
                    val conversationVm: ConversationViewModel = viewModel(
                        key = conversationKey,
                        factory = ConversationViewModel.factory(
                            chat = chatRepository,
                            tokens = tokenStore,
                            baseUrl = apiBaseUrl,
                            myUserId = tokenStore.userId,
                            conversationId = openConversationId,
                            recipient = newChatFriend,
                            appContext = appContext,
                            onAuthExpired = onLogout,
                            onConversationOpened = { id -> openConversationId = id },
                            tokenRefresher = tokenRefresher
                        )
                    )
                    val title = openConversationId?.let { id ->
                        chatListState?.conversations?.firstOrNull { it.conversationId == id }
                            ?.title(tokenStore.userId)
                    } ?: newChatFriend?.fullName?.ifBlank { stringResource(br.com.jogatina.R.string.conversation_fallback) } ?: stringResource(br.com.jogatina.R.string.conversation_fallback)
                    ConversationScreen(
                        viewModel = conversationVm,
                        title = title,
                        onBack = {
                            openConversationId = null
                            newChatFriend = null
                            chatListViewModel?.refresh()
                        },
                        bottomLift = androidx.compose.foundation.layout.WindowInsets.ime
                            .asPaddingValues()
                            .let { ime ->
                                val need = ime.calculateBottomPadding() - innerPadding.calculateBottomPadding()
                                if (need > 0.dp) need else 0.dp
                            },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                showChatList && chatListViewModel != null -> {
                    ChatListScreen(
                        viewModel = chatListViewModel,
                        onOpenConversation = { openConversationId = it.conversationId },
                        onNewConversation = { newChatFriend = it },
                        onBack = { showChatList = false },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                showNotifications && notificationsViewModel != null -> {
                    NotificationsScreen(
                        viewModel = notificationsViewModel,
                        onBack = {
                            showNotifications = false
                            notificationsViewModel.refresh()
                        },
                        onOpenConversation = {
                            showNotifications = false
                            openConversationId = it
                            newChatFriend = null
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                showCatalog && catalogViewModel != null -> {
                    CatalogScreen(
                        viewModel = catalogViewModel,
                        onBack = { showCatalog = false },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                showFriends && friendsViewModel != null -> {
                    FriendsScreen(
                        viewModel = friendsViewModel,
                        onChatWith = {
                            showFriends = false
                            openConversationId = null
                            newChatFriend = it
                        },
                        onBack = { showFriends = false },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                currentDestination == AppDestinations.HOME && feedViewModel != null -> {
                    FeedScreen(
                        viewModel = feedViewModel,
                        onLogout = onLogout,
                        unreadCount = notificationsState?.unreadCount ?: 0,
                        onNotificationsClick = {
                            // Recarrega para exibir notificações recém-chegadas.
                            notificationsViewModel?.refresh()
                            showNotifications = true
                        },
                        unreadChatCount = chatListState?.unreadTotal ?: 0,
                        onChatClick = {
                            chatListViewModel?.refresh()
                            showChatList = true
                        },
                        onAuthorClick = { selectedAuthorId = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                currentDestination == AppDestinations.FAVORITES && libraryViewModel != null -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onSearchCatalog = {
                            // Recarrega para exibir capas recém-enviadas e novos jogos.
                            catalogViewModel?.retry()
                            showCatalog = true
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                currentDestination == AppDestinations.PROFILE && profileViewModel != null -> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onFriendsClick = {
                            // Recarrega para exibir pedidos recém-chegados.
                            friendsViewModel?.refresh()
                            showFriends = true
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                else -> {
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
}

enum class AppDestinations(
    val labelRes: Int,
    val icon: Int,
) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    FAVORITES(R.string.nav_favorites, R.drawable.ic_favorite),
    PROFILE(R.string.nav_profile, R.drawable.ic_account_box),
}

/**
 * Bottom bar gamer flutuante: pílula arredondada com margem dos botões do
 * sistema, ícones compactos (22dp), fundo navy e indicador neon no ativo.
 */
@Composable
private fun GamerBottomBar(
    selected: AppDestinations,
    onSelect: (AppDestinations) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = br.com.jogatina.ui.theme.JogatinaMagenta.copy(alpha = 0.35f),
                spotColor = br.com.jogatina.ui.theme.JogatinaMagenta.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(br.com.jogatina.ui.theme.JogatinaNavyMid)
            .border(
                width = 1.dp,
                color = br.com.jogatina.ui.theme.JogatinaMagenta.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                br.com.jogatina.ui.theme.JogatinaMagenta.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDestinations.entries.forEach { destination ->
                    val isSelected = destination == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(destination) }
                            .padding(vertical = 6.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 3.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(2.dp),
                                        ambientColor = br.com.jogatina.ui.theme.JogatinaMagenta,
                                        spotColor = br.com.jogatina.ui.theme.JogatinaMagenta
                                    )
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(br.com.jogatina.ui.theme.JogatinaMagenta)
                            )
                        } else {
                            Box(modifier = Modifier.size(width = 22.dp, height = 3.dp))
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = stringResource(destination.labelRes),
                            tint = if (isSelected) {
                                br.com.jogatina.ui.theme.JogatinaMagenta
                            } else {
                                br.com.jogatina.ui.theme.JogatinaWhite.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(destination.labelRes),
                            color = if (isSelected) {
                                br.com.jogatina.ui.theme.JogatinaWhite
                            } else {
                                br.com.jogatina.ui.theme.JogatinaWhite.copy(alpha = 0.45f)
                            },
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JogatinaTheme {
        Greeting("Android")
    }
}
