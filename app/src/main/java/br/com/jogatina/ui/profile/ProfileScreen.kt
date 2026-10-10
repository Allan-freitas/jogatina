package br.com.jogatina.ui.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.jogatina.data.users.UserProfile
import br.com.jogatina.ui.theme.JogatinaDiscordRed
import br.com.jogatina.ui.theme.JogatinaMagenta
import br.com.jogatina.ui.theme.JogatinaNavyBottom
import br.com.jogatina.ui.theme.JogatinaNavyMid
import br.com.jogatina.ui.theme.JogatinaSubtitle
import br.com.jogatina.ui.theme.JogatinaTheme
import br.com.jogatina.ui.theme.JogatinaWhite
import br.com.jogatina.ui.theme.JogatinaWhite70
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onFriendsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state.lastSavedAt) {
        if (state.lastSavedAt > 0) editing = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(br.com.jogatina.R.string.profile_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(br.com.jogatina.R.string.refresh_action))
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (state.error != null) {
                Text(state.error!!, color = JogatinaDiscordRed, fontSize = 13.sp)
                TextButton(onClick = { viewModel.refresh() }) {
                    Text(stringResource(br.com.jogatina.R.string.retry), color = JogatinaMagenta, fontSize = 13.sp)
                }
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JogatinaMagenta)
                }
            } else {
                state.profile?.let { profile ->
                    ProfileAvatar(
                        photoUrl = viewModel.photoUrl(profile.photoUrl),
                        name = profile.displayName,
                        size = 110.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(profile.fullName, color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(profile.email, color = JogatinaSubtitle, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = JogatinaNavyMid),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow(
                                icon = Icons.Filled.Cake,
                                label = stringResource(br.com.jogatina.R.string.birth_label),
                                value = profile.birthDateDisplay ?: stringResource(br.com.jogatina.R.string.not_informed)
                            )
                            InfoRow(
                                icon = Icons.Filled.Interests,
                                label = stringResource(br.com.jogatina.R.string.hobbies_label),
                                value = profile.hobbies?.ifBlank { null } ?: stringResource(br.com.jogatina.R.string.not_informed)
                            )
                            InfoRow(
                                icon = Icons.Filled.Place,
                                label = stringResource(br.com.jogatina.R.string.country_label),
                                value = profile.country?.let { code ->
                                    "${flagEmoji(code)} ${countryName(code) ?: code}"
                                } ?: stringResource(br.com.jogatina.R.string.not_informed)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { editing = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JogatinaMagenta,
                                contentColor = JogatinaWhite
                            )
                        ) {
                            Text(stringResource(br.com.jogatina.R.string.edit_profile), fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onFriendsClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JogatinaWhite.copy(alpha = 0.12f),
                                contentColor = JogatinaWhite
                            )
                        ) {
                            Text(stringResource(br.com.jogatina.R.string.friends_button), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        state.profile?.let { profile ->
            EditProfileDialog(
                viewModel = viewModel,
                profile = profile,
                saving = state.saving,
                onSave = { birthIso, hobbies, country, photo ->
                    viewModel.save(birthIso, hobbies, country, photo)
                },
                onDismiss = { editing = false }
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = JogatinaMagenta, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, color = JogatinaSubtitle, fontSize = 12.sp)
            Text(value, color = JogatinaWhite, fontSize = 15.sp)
        }
    }
}

@Composable
fun ProfileAvatar(
    photoUrl: String?,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    showBorder: Boolean = true
) {
    // Borda branca fina somente quando há foto de verdade.
    val withRing = showBorder && photoUrl != null
    Box(
        modifier = Modifier
            .size(size)
            .background(
                if (withRing) JogatinaWhite else androidx.compose.ui.graphics.Color.Transparent,
                CircleShape
            )
            .padding(if (withRing) 2.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(br.com.jogatina.R.string.profile_photo),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JogatinaMagenta.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = JogatinaWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 3).sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    viewModel: ProfileViewModel,
    profile: UserProfile,
    saving: Boolean,
    onSave: (birthIso: String?, hobbies: String?, country: String?, photo: PickedPhoto?) -> Unit,
    onDismiss: () -> Unit
) {
    var hobbies by remember { mutableStateOf(profile.hobbies.orEmpty()) }
    var birthIso by remember { mutableStateOf(profile.birthDate) }
    var countryCode by remember { mutableStateOf(profile.country) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoMime by remember { mutableStateOf("image/jpeg") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            photoMime = resolver.getType(uri) ?: "image/jpeg"
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 8 * 1024 * 1024) return@rememberLauncherForActivityResult
                photoBytes = bytes
                photoUri = uri
            }
        } catch (_: Exception) {
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JogatinaNavyMid)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(br.com.jogatina.R.string.edit_profile), color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Box(contentAlignment = Alignment.BottomEnd) {
                val preview = photoUri?.toString() ?: viewModel.photoUrl(profile.photoUrl)
                ProfileAvatar(photoUrl = preview, name = profile.displayName, size = 96.dp)
                IconButton(
                    onClick = { pickPhoto.launch("image/*") },
                    modifier = Modifier
                        .size(32.dp)
                        .background(JogatinaMagenta, CircleShape)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(br.com.jogatina.R.string.change_photo), tint = JogatinaWhite, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = birthIso?.toDisplay() ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(br.com.jogatina.R.string.birth_label), color = JogatinaWhite70, fontSize = 13.sp) },
                placeholder = { Text(stringResource(br.com.jogatina.R.string.birth_pick), color = JogatinaWhite70, fontSize = 14.sp) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Cake,
                        contentDescription = null,
                        tint = JogatinaMagenta,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                colors = profileFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hobbies,
                onValueChange = { if (it.length <= 500) hobbies = it },
                label = { Text(stringResource(br.com.jogatina.R.string.hobbies_hint), color = JogatinaWhite70, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                colors = profileFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            CountryDropdown(
                selectedCode = countryCode,
                onSelect = { countryCode = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(stringResource(br.com.jogatina.R.string.cancel), color = JogatinaWhite70)
                }
                Button(
                    onClick = {
                        val photo = photoBytes?.let { PickedPhoto(it, photoMime, photoUri!!) }
                        onSave(birthIso, hobbies.ifBlank { null }, countryCode, photo)
                    },
                    enabled = !saving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JogatinaMagenta, contentColor = JogatinaWhite)
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = JogatinaWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(br.com.jogatina.R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = birthIso?.toEpochMillis() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        birthIso = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(br.com.jogatina.R.string.ok), color = JogatinaMagenta)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(br.com.jogatina.R.string.cancel), color = JogatinaWhite70)
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

private fun String.toDisplay(): String =
    split("-").takeIf { it.size == 3 }?.reversed()?.joinToString("/") ?: this

private fun String.toEpochMillis(): Long? = try {
    LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryDropdown(
    selectedCode: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val selected = selectedCode?.let { code ->
        Countries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
    val visible = remember(filter) {
        if (filter.isBlank()) Countries
        else Countries.filter { it.displayName().contains(filter, ignoreCase = true) || it.nameEn.contains(filter, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.flagEmoji()}  ${it.displayName()}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(br.com.jogatina.R.string.country_label), color = JogatinaWhite70, fontSize = 13.sp) },
            placeholder = { Text(stringResource(br.com.jogatina.R.string.country_pick), color = JogatinaWhite70, fontSize = 14.sp) },
            leadingIcon = selected?.let {
                { Text(it.flagEmoji(), fontSize = 20.sp) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = profileFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(JogatinaNavyMid)
                .heightIn(max = 320.dp)
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(br.com.jogatina.R.string.country_search), color = JogatinaWhite70, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = profileFieldColors()
            )
            DropdownMenuItem(
                text = { Text(stringResource(br.com.jogatina.R.string.country_none), color = JogatinaWhite70, fontSize = 14.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                visible.take(249).forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${country.flagEmoji()}  ${country.displayName()}",
                                color = JogatinaWhite,
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = if (selected?.code == country.code) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = JogatinaMagenta,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = { onSelect(country.code); expanded = false },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JogatinaWhite,
    unfocusedTextColor = JogatinaWhite,
    focusedBorderColor = JogatinaMagenta,
    unfocusedBorderColor = JogatinaWhite.copy(alpha = 0.25f),
    cursorColor = JogatinaMagenta
)

@Preview(showBackground = true)
@Composable
private fun ProfilePreview() {
    JogatinaTheme {
        Box(Modifier.background(JogatinaNavyBottom).padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileAvatar(photoUrl = null, name = "Ada", size = 110.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ada Lovelace", color = JogatinaWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
    }
}