package com.spamstopper.app.presentation.settings

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spamstopper.app.utils.PermissionsHelper

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher para solicitar permisos
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("SettingsScreen", "📋 Permisos recibidos: $permissions")

        // Verificar permisos después de solicitar
        viewModel.checkPermissions()

        // Si todos fueron concedidos, solicitar marcador por defecto
        if (permissions.values.all { it }) {
            if (!PermissionsHelper.isDefaultDialer(context)) {
                android.util.Log.d("SettingsScreen", "📞 Solicitando marcador por defecto...")
                PermissionsHelper.requestDefaultDialer(context)
            }
        }
    }

    // Solicitar permisos cuando sea necesario
    LaunchedEffect(state.needsPermissionRequest) {
        if (state.needsPermissionRequest) {
            android.util.Log.d("SettingsScreen", "🔐 Solicitando permisos...")
            permissionsLauncher.launch(PermissionsHelper.SECRETARY_PERMISSIONS)
            viewModel.clearPermissionRequest()
        }
    }

    // Mostrar mensaje si existe
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Recargar permisos cuando se vuelve a la pantalla
    DisposableEffect(Unit) {
        viewModel.checkPermissions()
        onDispose {
            viewModel.checkPermissions()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // INDICADOR DE PERMISOS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.hasSecretaryPermissions) {
                        viewModel.requestPermissions()
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (state.hasSecretaryPermissions) {
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    } else {
                        Color(0xFFF44336).copy(alpha = 0.1f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.hasSecretaryPermissions) {
                                "✅ Sistema listo"
                            } else {
                                "⚠️ Permisos necesarios"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.hasSecretaryPermissions) {
                                Color(0xFF4CAF50)
                            } else {
                                Color(0xFFF44336)
                            }
                        )

                        if (!state.hasSecretaryPermissions) {
                            Button(
                                onClick = { viewModel.requestPermissions() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Solicitar")
                            }
                        }
                    }

                    if (!state.hasSecretaryPermissions) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "📋 Permisos faltantes:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        state.missingPermissions.forEach { permission ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFFF44336)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = PermissionsHelper.getPermissionName(permission),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = getPermissionDescription(permission),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "💡 Toca aquí o pulsa 'Solicitar' para otorgar permisos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "✅ Todos los permisos concedidos. Sistema listo para detectar spam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // MODO SECRETARIA
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎙️ Modo Secretaria",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Contestar y analizar llamadas automáticamente",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Switch(
                            checked = state.autoAnswerEnabled,
                            enabled = state.hasSecretaryPermissions,
                            onCheckedChange = { viewModel.setAutoAnswer(it) }
                        )
                    }

                    if (!state.hasSecretaryPermissions) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.requestPermissions() },
                            color = Color(0xFFFFF3E0),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Se requieren permisos adicionales. Toca para ver detalles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
            }

            // PERMITIR CONTACTOS REGISTRADOS
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📞 Permitir contactos registrados",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Los contactos guardados tienen paso libre sin análisis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Switch(
                            checked = state.allowContactsEnabled,
                            onCheckedChange = { viewModel.setAllowContacts(it) }
                        )
                    }
                }
            }

            // KEYWORDS PERSONALIZADAS
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔑 Palabras clave de emergencia",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Añade palabras que harán sonar el teléfono (separadas por comas)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    var keywordsText by remember { mutableStateOf(state.customKeywords) }

                    LaunchedEffect(state.customKeywords) {
                        keywordsText = state.customKeywords
                    }

                    OutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ej: trabajo, oficina, banco") },
                        maxLines = 3
                    )

                    Button(
                        onClick = { viewModel.setCustomKeywords(keywordsText) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar")
                    }
                }
            }

            // LIMPIAR HISTORIAL
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🗑️ Limpiar historial",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Eliminar todas las llamadas registradas del sistema",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    var showDialog by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eliminar historial")
                    }

                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("¿Eliminar historial?") },
                            text = { Text("Esta acción no se puede deshacer. Se eliminarán todas las llamadas registradas.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.clearCallHistory()
                                        showDialog = false
                                    }
                                ) {
                                    Text("Eliminar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = false }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }
                }
            }

            // CONFIGURACIÓN AVANZADA
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚙️ Configuración avanzada",
                        style = MaterialTheme.typography.titleMedium
                    )

                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir configuración del sistema")
                    }
                }
            }

            // INFO DE LA APP
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ℹ️ Información",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "SpamStopper v2.0",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Sistema avanzado de detección de spam",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Obtiene descripción detallada de cada permiso
 */
private fun getPermissionDescription(permission: String): String {
    return when (permission) {
        android.Manifest.permission.READ_PHONE_STATE -> "Necesario para detectar llamadas entrantes"
        android.Manifest.permission.ANSWER_PHONE_CALLS -> "Permite contestar llamadas automáticamente"
        android.Manifest.permission.READ_CALL_LOG -> "Lee el historial para analizar patrones"
        android.Manifest.permission.RECORD_AUDIO -> "Captura audio para detectar spam"
        "DEFAULT_DIALER" -> "Debe ser la app de llamadas predeterminada"
        else -> "Requerido para el funcionamiento"
    }
}