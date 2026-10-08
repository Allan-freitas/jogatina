package br.com.jogatina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import br.com.jogatina.data.api.ApiClient
import br.com.jogatina.data.auth.AuthRepository
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
import br.com.jogatina.ui.notifications.NotificationsScreen
import br.com.jogatina.ui.notifications.NotificationsViewModel
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.welcome.WelcomeScreen
import br.com.jogatina.ui.welcome.WelcomeViewModel

class MainActivity : ComponentActivity() {

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
    private val welcomeViewModel: WelcomeViewModel by viewModels {
        WelcomeViewModel.factory(authRepository, tokenStore)
    }
    private val feedViewModel: FeedViewModel by viewModels {
        FeedViewModel.factory(feedRepository, userRepository, tokenStore) {
            welcomeViewModel.logout()
        }
    }
    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(gamesRepository) {
            welcomeViewModel.logout()
        }
    }
    private val notificationsViewModel: NotificationsViewModel by viewModels {
        NotificationsViewModel.factory(notificationsRepository) {
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
        enableEdgeToEdge()
        setContent {
            JogatinaTheme {
                val welcomeState by welcomeViewModel.state.collectAsState()
                if (welcomeState.loggedIn) {
                    JogatinaApp(
                        feedViewModel = feedViewModel,
                        libraryViewModel = libraryViewModel,
                        catalogViewModel = catalogViewModel,
                        notificationsViewModel = notificationsViewModel,
                        onLogout = { welcomeViewModel.logout() }
                    )
                } else {
                    WelcomeScreen(viewModel = welcomeViewModel)
                }
            }
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
    onLogout: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationsState = notificationsViewModel?.state?.collectAsState()?.value

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when {
                showNotifications && notificationsViewModel != null -> {
                    NotificationsScreen(
                        viewModel = notificationsViewModel,
                        onBack = {
                            showNotifications = false
                            notificationsViewModel.refresh()
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
                currentDestination == AppDestinations.HOME && feedViewModel != null -> {
                    FeedScreen(
                        viewModel = feedViewModel,
                        onLogout = onLogout,
                        unreadCount = notificationsState?.unreadCount ?: 0,
                        onNotificationsClick = { showNotifications = true },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                currentDestination == AppDestinations.FAVORITES && libraryViewModel != null -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onSearchCatalog = { showCatalog = true },
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
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
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
