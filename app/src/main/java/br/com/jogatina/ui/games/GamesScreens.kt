package br.com.jogatina.ui.games

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import br.com.jogatina.data.games.GameDto
import br.com.jogatina.data.games.GameStatus
import br.com.jogatina.data.games.MyGameDto
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaGold
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70
import coil.compose.AsyncImage

// ---------- Biblioteca (aba Favorites) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSearchCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<MyGameDto?>(null) }
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var coverMime by remember { mutableStateOf("image/jpeg") }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            coverMime = resolver.getType(uri) ?: "image/jpeg"
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 8 * 1024 * 1024) return@rememberLauncherForActivityResult
                coverBytes = bytes
                coverUri = uri
            }
        } catch (_: Exception) {
        }
    }

    fun closeEditor() {
        editing = null
        coverBytes = null
        coverUri = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Meus Jogos", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchCatalog) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar no catálogo")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusFilterChip(selected = state.filter == null, label = "Todos", onClick = { viewModel.setFilter(null) })
                GameStatus.entries.forEach { status ->
                    StatusFilterChip(
                        selected = state.filter == status,
                        label = status.label,
                        onClick = { viewModel.setFilter(status) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                TextButton(onClick = { viewModel.refresh() }) {
                    Text("Tentar de novo", color = JogatinaMagenta, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else if (state.visible.isEmpty()) {
                Text(
                    "Nenhum jogo aqui. Toque na lupa para buscar no catálogo!",
                    color = JogatinaSubtitle, fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.visible, key = { it.id }) { game ->
                        LibraryRow(
                            game = game,
                            coverUrl = viewModel.coverUrl(game.coverImageUrl),
                            busy = state.removing.contains(game.gameId) || state.updating.contains(game.gameId),
                            onToggleFavorite = {
                                viewModel.changeStatus(game, game.status, !game.isFavorite)
                            },
                            onDelete = { viewModel.remove(game) },
                            onEdit = { editing = game }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }

    editing?.let { game ->
        AddToLibraryDialog(
            title = game.title,
            initialStatus = game.status,
            initialFavorite = game.isFavorite,
            isUpdate = true,
            busy = state.updating.contains(game.gameId),
            coverUrl = viewModel.coverUrl(game.coverImageUrl),
            pickedCoverUri = coverUri,
            onPickCover = { pickCover.launch("image/*") },
            onConfirm = { status, fav ->
                val cover = coverBytes?.let { NewCover(it, coverMime) }
                viewModel.updateGame(game, status, fav, cover)
                closeEditor()
            },
            onDismiss = { closeEditor() }
        )
    }
}

@Composable
private fun StatusFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JogatinaMagenta,
            selectedLabelColor = JogatinaWhite,
            containerColor = JogatinaNavyMid,
            labelColor = JogatinaWhite70
        )
    )
}

@Composable
private fun LibraryRow(
    game: MyGameDto,
    coverUrl: String?,
    busy: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onEdit)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GameCover(url = coverUrl, title = game.title, modifier = Modifier.size(width = 56.dp, height = 76.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${game.genre} • ${game.platform}", color = JogatinaSubtitle, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                StatusChip(game.status)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleFavorite, enabled = !busy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (game.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (game.isFavorite) JogatinaGold else JogatinaWhite70
                    )
                }
                IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(36.dp)) {
                    if (busy) {
                        CircularProgressIndicator(color = JogatinaWhite70, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remover", tint = JogatinaSubtitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: GameStatus) {
    val color = when (status) {
        GameStatus.WISHLIST -> JogatinaSubtitle
        GameStatus.PLAYING -> JogatinaMagenta
        GameStatus.COMPLETED -> Color(0xFF4CAF50)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Catálogo ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var pendingGame by remember { mutableStateOf<GameDto?>(null) }
    var pendingFav by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Catálogo", fontWeight = FontWeight.Bold) },
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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Buscar jogo...", color = JogatinaWhite70, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JogatinaWhite70) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = gameFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.genre,
                    onValueChange = { viewModel.onFilterChange(it, state.platform) },
                    placeholder = { Text("Gênero", color = JogatinaWhite70, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = gameFieldColors()
                )
                OutlinedTextField(
                    value = state.platform,
                    onValueChange = { viewModel.onFilterChange(state.genre, it) },
                    placeholder = { Text("Plataforma", color = JogatinaWhite70, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = gameFieldColors()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                TextButton(onClick = { viewModel.retry() }) {
                    Text("Tentar de novo", color = JogatinaMagenta, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.games, key = { it.id }) { game ->
                        CatalogCard(
                            game = game,
                            coverUrl = viewModel.coverUrl(game.coverImageUrl),
                            inLibrary = viewModel.isInLibrary(game.id),
                            adding = state.adding.contains(game.id),
                            onAdd = { pendingGame = game; pendingFav = false }
                        )
                    }
                    if (state.hasMore && state.games.isNotEmpty()) {
                        item {
                            TextButton(onClick = { viewModel.loadMore() }, enabled = !state.loadingMore) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("Carregar mais", color = JogatinaMagenta, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingGame?.let { game ->
        val conflicted = state.conflict?.id == game.id
        AddToLibraryDialog(
            title = game.title,
            initialStatus = GameStatus.WISHLIST,
            initialFavorite = pendingFav,
            isUpdate = conflicted,
            conflictMessage = if (conflicted) "Já está na sua biblioteca." else null,
            onConfirm = { status, fav ->
                if (conflicted) viewModel.confirmUpdate(game, status, fav)
                else viewModel.add(game, status, fav)
                if (state.conflict?.id != game.id) pendingGame = null
                else pendingGame = null
            },
            onDismiss = {
                viewModel.dismissConflict()
                pendingGame = null
            }
        )
    }
}

@Composable
private fun CatalogCard(
    game: GameDto,
    coverUrl: String?,
    inLibrary: Boolean,
    adding: Boolean,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box {
                GameCover(
                    url = coverUrl,
                    title = game.title,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
                if (inLibrary) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Na biblioteca", color = JogatinaWhite, fontSize = 11.sp)
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(game.title, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${game.platform} • pop. ${game.popularity}", color = JogatinaSubtitle, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAdd,
                    enabled = !adding && !inLibrary,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JogatinaMagenta,
                        contentColor = JogatinaWhite,
                        disabledContainerColor = JogatinaMagenta.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (adding) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (inLibrary) "Adicionado" else "Adicionar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------- Compartilhados ----------

@Composable
fun GameCover(url: String?, title: String, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = title,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(JogatinaMagenta.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                color = JogatinaWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
    }
}

@Composable
private fun AddToLibraryDialog(
    title: String,
    initialStatus: GameStatus,
    initialFavorite: Boolean,
    isUpdate: Boolean,
    conflictMessage: String? = null,
    busy: Boolean = false,
    coverUrl: String? = null,
    pickedCoverUri: Uri? = null,
    onPickCover: (() -> Unit)? = null,
    onConfirm: (GameStatus, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf(initialStatus) }
    var favorite by remember { mutableStateOf(initialFavorite) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JogatinaNavyMid)
                .padding(20.dp)
        ) {
            Text(title, color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (conflictMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(conflictMessage, color = JogatinaGold, fontSize = 13.sp)
            }
            if (onPickCover != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Capa do jogo (vale para o catálogo)", color = JogatinaWhite70, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview = pickedCoverUri?.toString() ?: coverUrl
                    if (preview != null) {
                        AsyncImage(
                            model = preview,
                            contentDescription = "Capa",
                            modifier = Modifier
                                .size(width = 64.dp, height = 84.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JogatinaMagenta.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("?", color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(onClick = onPickCover) {
                        Text(
                            if (pickedCoverUri != null) "Trocar imagem" else "Enviar capa",
                            color = JogatinaMagenta, fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            GameStatus.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { status = option }
                ) {
                    RadioButton(
                        selected = status == option,
                        onClick = { status = option },
                        colors = RadioButtonDefaults.colors(selectedColor = JogatinaMagenta)
                    )
                    Text(option.label, color = JogatinaWhite, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favorito", color = JogatinaWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = favorite,
                    onCheckedChange = { favorite = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = JogatinaGold, checkedTrackColor = JogatinaGold.copy(alpha = 0.4f))
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Cancelar", color = JogatinaWhite70)
                }
                Button(
                    onClick = { onConfirm(status, favorite) },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JogatinaMagenta, contentColor = JogatinaWhite)
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(if (isUpdate) "Atualizar" else "Adicionar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun gameFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JogatinaWhite,
    unfocusedTextColor = JogatinaWhite,
    focusedBorderColor = JogatinaMagenta,
    unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
    cursorColor = JogatinaMagenta
);

@Preview(showBackground = true)
@Composable
private fun LibraryRowPreview() {
    JogatinaTheme {
        Box(Modifier.background(JogatinaNavyBottom).padding(12.dp)) {
            LibraryRow(
                game = MyGameDto(
                    id = "u1", gameId = "g1", title = "Elder Realms",
                    genre = "RPG", platform = "PC", status = GameStatus.PLAYING,
                    isFavorite = true, coverImageUrl = null, addedOnUtc = ""
                ),
                coverUrl = null, busy = false,
                onToggleFavorite = {}, onDelete = {}, onEdit = {}
            )
        }
    }
}
