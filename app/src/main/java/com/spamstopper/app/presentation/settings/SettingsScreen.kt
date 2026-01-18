package com.spamstopper.app.presentation.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spamstopper.app.utils.PermissionsHelper

/**
 * ============================================================================
 * SettingsScreen.kt - Pantalla de configuración de SpamStopper
 * ============================================================================
 *
 * PROPÓSITO:
 * Permite al usuario configurar SpamStopper:
 * - Modo Secretaria (auto-contestar)
 * - Tono de notificación para llamadas legítimas
 * - Palabras clave de emergencia
 * - Nombres de familia
 * - Permisos del sistema
 *
 * ACTUALIZADO: Enero 2026 - Añadido selector de tono de notificación
 * ============================================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher para permisos
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.checkPermissions()
        if (permissions.values.all { it } && !PermissionsHelper.isDefaultDialer(context)) {
            PermissionsHelper.requestDefaultDialer(context)
        }
    }

    // Launcher para selector de tono
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        viewModel.setNotificationRingtone(uri)
    }

    LaunchedEffect(state.needsPermissionRequest) {
        if (state.needsPermissionRequest) {
            permissionsLauncher.launch(PermissionsHelper.SECRETARY_PERMISSIONS)
            viewModel.clearPermissionRequest()
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    DisposableEffect(Unit) {
        viewModel.checkPermissions()
        onDispose { }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PERMISOS
            PermissionsCard(
                hasPermissions = state.hasSecretaryPermissions,
                missingPermissions = state.missingPermissions,
                onRequestPermissions = { viewModel.requestPermissions() }
            )

            // MODO SECRETARIA
            SecretaryModeCard(
                enabled = state.autoAnswerEnabled,
                hasPermissions = state.hasSecretaryPermissions,
                onToggle = { viewModel.setAutoAnswer(it) },
                onRequestPermissions = { viewModel.requestPermissions() }
            )

            // TONO DE NOTIFICACIÓN
            RingtoneCard(
                currentRingtoneName = state.notificationRingtoneName,
                onSelectRingtone = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono para llamadas legítimas")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        state.notificationRingtoneUri?.let { uri ->
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                        }
                    }
                    ringtoneLauncher.launch(intent)
                },
                onTestRingtone = { viewModel.testNotificationRingtone() }
            )

            // PERMITIR CONTACTOS
            AllowContactsCard(
                enabled = state.allowContactsEnabled,
                onToggle = { viewModel.setAllowContacts(it) }
            )

            // PALABRAS CLAVE
            EmergencyKeywordsCard(
                keywords = state.customKeywords,
                onSave = { viewModel.setCustomKeywords(it) }
            )

            // NOMBRES DE FAMILIA
            FamilyNamesCard(
                names = state.familyNames,
                onSave = { viewModel.setFamilyNames(it) }
            )

            // NOMBRE DEL USUARIO
            UserNameCard(
                userName = state.userName,
                onSave = { viewModel.setUserName(it) }
            )

            // AVANZADO
            AdvancedSettingsCard(
                onOpenSystemSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                },
                onClearHistory = { viewModel.clearCallHistory() }
            )

            // INFO
            AppInfoCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionsCard(
    hasPermissions: Boolean,
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !hasPermissions) { onRequestPermissions() },
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermissions) Color(0xFF10B981).copy(alpha = 0.1f)
            else Color(0xFFEF4444).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (hasPermissions) "✅" else "⚠️", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (hasPermissions) "Sistema listo" else "Permisos necesarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPermissions) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                        Text(
                            text = if (hasPermissions) "Todos los permisos concedidos"
                            else "${missingPermissions.size} permisos faltantes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (!hasPermissions) {
                    Button(onClick = onRequestPermissions) { Text("Solicitar") }
                }
            }
            if (!hasPermissions && missingPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                missingPermissions.forEach { permission ->
                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getPermissionDisplayName(permission), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretaryModeCard(
    enabled: Boolean,
    hasPermissions: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "🎙️", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Modo Secretaria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Contesta y analiza llamadas automáticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Switch(checked = enabled, enabled = hasPermissions, onCheckedChange = onToggle)
            }
            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onRequestPermissions() },
                    color = Color(0xFFFFF3E0),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = Color(0xFFFF9800))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Se requieren permisos. Toca para configurar.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                    }
                }
            }
        }
    }
}

@Composable
private fun RingtoneCard(
    currentRingtoneName: String,
    onSelectRingtone: () -> Unit,
    onTestRingtone: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔔", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tono de notificación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Sonará cuando detecte una llamada legítima",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelectRingtone() },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tono seleccionado", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(currentRingtoneName.ifEmpty { "Tono predeterminado" },
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, "Cambiar", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestRingtone, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Probar")
                }
                Button(onClick = onSelectRingtone, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.MusicNote, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cambiar")
                }
            }
        }
    }
}

@Composable
private fun AllowContactsCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = "📇", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Permitir contactos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Los contactos guardados pasan sin análisis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmergencyKeywordsCard(keywords: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(keywords) }
    LaunchedEffect(keywords) { text = keywords }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🚨", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Palabras de emergencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Estas palabras harán sonar tu teléfono siempre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("urgente, emergencia, hospital") },
                label = { Text("Separadas por comas") }, maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun FamilyNamesCard(names: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(names) }
    LaunchedEffect(names) { text = names }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "👨‍👩‍👧", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Nombres de familia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Si mencionan estos nombres, te alertamos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mamá, papá, hijo, María") },
                label = { Text("Separados por comas") }, maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun UserNameCard(userName: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(userName) }
    LaunchedEffect(userName) { text = userName }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "👤", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Tu nombre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Si lo mencionan, la llamada es para ti",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tu nombre") },
                label = { Text("Nombre") }, singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun AdvancedSettingsCard(onOpenSystemSettings: () -> Unit, onClearHistory: () -> Unit) {
    var showClearDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚙️", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Configuración avanzada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onOpenSystemSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir configuración del sistema")
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
            TextButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Limpiar historial de llamadas")
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("¿Eliminar historial?") },
            text = { Text("Se eliminarán todas las llamadas registradas. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { onClearHistory(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { showClearDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AppInfoCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🛡️", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("SpamStopper 2.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tu secretaria personal contra el spam",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun getPermissionDisplayName(permission: String): String {
    return when {
        permission.contains("PHONE") -> "Teléfono"
        permission.contains("CALL_LOG") -> "Registro de llamadas"
        permission.contains("CONTACTS") -> "Contactos"
        permission.contains("RECORD_AUDIO") -> "Micrófono"
        permission.contains("NOTIFICATION") -> "Notificaciones"
        else -> permission.substringAfterLast(".")
    }
}
