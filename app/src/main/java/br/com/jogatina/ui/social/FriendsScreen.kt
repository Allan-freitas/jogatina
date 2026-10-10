package br.com.jogatina.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jogatina.data.social.FriendDto
import br.com.jogatina.data.social.PendingRequest
import br.com.jogatina.data.social.SearchedUser
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onChatWith: (FriendDto) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Amigos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FriendsTabChip(
                    selected = state.tab == FriendsTab.FRIENDS,
                    label = "Meus amigos (${state.friends.size})",
                    onClick = { viewModel.setTab(FriendsTab.FRIENDS) }
                )
                FriendsTabChip(
                    selected = state.tab == FriendsTab.REQUESTS,
                    label = "Pedidos" + if (state.incomingCount > 0) " (${state.incomingCount})" else "",
                    onClick = { viewModel.setTab(FriendsTab.REQUESTS) }
                )
                FriendsTabChip(
                    selected = state.tab == FriendsTab.SEARCH,
                    label = "Buscar",
                    onClick = { viewModel.setTab(FriendsTab.SEARCH) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            when (state.tab) {
                FriendsTab.FRIENDS -> FriendsList(
                    loading = state.loading,
                    friends = state.friends,
                    busyIds = state.busyIds,
                    onChat = onChatWith,
                    onRemove = viewModel::removeFriend
                )
                FriendsTab.REQUESTS -> RequestsList(
                    loading = state.loading,
                    requests = state.requests,
                    busyIds = state.busyIds,
                    onRespond = viewModel::respond
                )
                FriendsTab.SEARCH -> SearchTab(
                    query = state.searchQuery,
                    searching = state.searching,
                    results = state.searchResults,
                    busyIds = state.busyIds,
                    relationOf = state::relationOf,
                    onQuery = viewModel::onSearchChange,
                    onAdd = viewModel::sendRequest
                )
            }
        }
    }
}

@Composable
private fun FriendsTabChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JogatinaMagenta,
            selectedLabelColor = JogatinaWhite,
            containerColor = JogatinaNavyMid,
            labelColor = JogatinaWhite70
        )
    )
}

@Composable
private fun FriendsList(
    loading: Boolean,
    friends: List<FriendDto>,
    busyIds: Set<String>,
    onChat: (FriendDto) -> Unit,
    onRemove: (FriendDto) -> Unit
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = JogatinaMagenta)
        }
        return
    }
    if (friends.isEmpty()) {
        Text(
            "Nenhum amigo ainda. Vá em Buscar para encontrar jogadores!",
            color = JogatinaSubtitle, fontSize = 14.sp,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(friends, key = { it.friendId }) { friend ->
            Card(
                colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FriendAvatar(name = friend.fullName)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.fullName.ifBlank { "Jogador" }, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (friend.isOnline) "Online" else "Offline",
                            color = if (friend.isOnline) Color(0xFF4CAF50) else JogatinaSubtitle,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { onChat(friend) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Conversar", tint = JogatinaMagenta)
                    }
                    IconButton(
                        onClick = { onRemove(friend) },
                        enabled = !busyIds.contains(friend.friendId),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remover", tint = JogatinaSubtitle)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun RequestsList(
    loading: Boolean,
    requests: List<PendingRequest>,
    busyIds: Set<String>,
    onRespond: (PendingRequest, Boolean) -> Unit
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = JogatinaMagenta)
        }
        return
    }
    if (requests.isEmpty()) {
        Text(
            "Nenhum pedido pendente.",
            color = JogatinaSubtitle, fontSize = 14.sp,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(requests, key = { it.requestId }) { request ->
            val busy = busyIds.contains(request.requestId)
            Card(
                colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FriendAvatar(name = request.otherName)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(request.otherName, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (request.incoming) "Quer ser seu amigo" else "Aguardando resposta",
                                color = JogatinaSubtitle, fontSize = 12.sp
                            )
                        }
                        if (request.incoming) {
                            if (busy) {
                                CircularProgressIndicator(color = JogatinaWhite70, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                IconButton(onClick = { onRespond(request, true) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Check, contentDescription = "Aceitar", tint = Color(0xFF4CAF50))
                                }
                                IconButton(onClick = { onRespond(request, false) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Recusar", tint = JogatinaDiscordRed)
                                }
                            }
                        } else {
                            Badge(containerColor = JogatinaWhite.copy(alpha = 0.15f), contentColor = JogatinaWhite70) {
                                Text("Enviado", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun SearchTab(
    query: String,
    searching: Boolean,
    results: List<SearchedUser>,
    busyIds: Set<String>,
    relationOf: (String) -> Relation,
    onQuery: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Nome ou e-mail (mín. 2 letras)...", color = JogatinaWhite70, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JogatinaWhite70) },
        trailingIcon = {
            if (searching) {
                CircularProgressIndicator(color = JogatinaMagenta, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = JogatinaWhite,
            unfocusedTextColor = JogatinaWhite,
            focusedBorderColor = JogatinaMagenta,
            unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
            cursorColor = JogatinaMagenta
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(results, key = { it.id }) { user ->
            val busy = busyIds.contains(user.id)
            Card(
                colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FriendAvatar(name = user.fullName)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.fullName, color = JogatinaWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(user.email, color = JogatinaSubtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    when (relationOf(user.id)) {
                        Relation.FRIEND -> Badge(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            contentColor = Color(0xFF4CAF50)
                        ) {
                            Text("Amigos", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        Relation.SENT -> Badge(
                            containerColor = JogatinaWhite.copy(alpha = 0.15f),
                            contentColor = JogatinaWhite70
                        ) {
                            Text("Enviado", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        Relation.NONE -> IconButton(
                            onClick = { onAdd(user.id) },
                            enabled = !busy,
                            modifier = Modifier.size(38.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = JogatinaWhite70, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.PersonAdd, contentDescription = "Adicionar", tint = JogatinaMagenta)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun FriendAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(JogatinaMagenta.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = JogatinaWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}
