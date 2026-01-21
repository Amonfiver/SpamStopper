package com.spamstopper.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

/**
 * ============================================================================
 * HistoryScreen.kt - Pantalla de historial de llamadas
 * ============================================================================
 *
 * PROPÓSITO:
 * Muestra el historial de llamadas del sistema con filtros y botón "Saber más"
 * para ver detalles de las llamadas.
 *
 * NOTA: Los tipos CallHistoryItem, CallType y CallTypeFilter están definidos
 * en HistoryViewModel.kt en el mismo paquete.
 *
 * ACTUALIZADO: Enero 2026 - Añadido botón "Saber más" y diálogo de detalles
 * ============================================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyItems: List<CallHistoryItem>,
    onCallClick: (CallHistoryItem) -> Unit,
    onFilterChange: (CallTypeFilter) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CallTypeFilter.ALL) }
    var selectedItem by remember { mutableStateOf<CallHistoryItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // FILTROS
        // ═══════════════════════════════════════════════════════════════════

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == CallTypeFilter.ALL,
                onClick = {
                    selectedFilter = CallTypeFilter.ALL
                    onFilterChange(CallTypeFilter.ALL)
                },
                label = { Text("📋 Todas") }
            )
            FilterChip(
                selected = selectedFilter == CallTypeFilter.INCOMING,
                onClick = {
                    selectedFilter = CallTypeFilter.INCOMING
                    onFilterChange(CallTypeFilter.INCOMING)
                },
                label = { Text("📥") }
            )
            FilterChip(
                selected = selectedFilter == CallTypeFilter.OUTGOING,
                onClick = {
                    selectedFilter = CallTypeFilter.OUTGOING
                    onFilterChange(CallTypeFilter.OUTGOING)
                },
                label = { Text("📤") }
            )
            FilterChip(
                selected = selectedFilter == CallTypeFilter.MISSED,
                onClick = {
                    selectedFilter = CallTypeFilter.MISSED
                    onFilterChange(CallTypeFilter.MISSED)
                },
                label = { Text("📵") }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // LISTA DE LLAMADAS
        // ═══════════════════════════════════════════════════════════════════

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "📋",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "No hay llamadas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "El historial aparecerá aquí",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = historyItems,
                    key = { it.id }
                ) { item ->
                    CallHistoryCard(
                        item = item,
                        onCallClick = { onCallClick(item) },
                        onDetailsClick = { selectedItem = item }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIÁLOGO DE DETALLES
    // ═══════════════════════════════════════════════════════════════════════

    if (selectedItem != null) {
        CallDetailDialog(
            item = selectedItem!!,
            onDismiss = { selectedItem = null },
            onCallBack = {
                onCallClick(selectedItem!!)
                selectedItem = null
            }
        )
    }
}

/**
 * Tarjeta de llamada individual
 */
@Composable
private fun CallHistoryCard(
    item: CallHistoryItem,
    onCallClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val emoji = when (item.type) {
        CallType.INCOMING -> "📥"
        CallType.OUTGOING -> "📤"
        CallType.MISSED -> "📵"
        CallType.BLOCKED -> "🛡️"
    }
    
    val color = when (item.type) {
        CallType.INCOMING -> Color(0xFF10B981)
        CallType.OUTGOING -> Color(0xFF3B82F6)
        CallType.MISSED -> Color(0xFFEF4444)
        CallType.BLOCKED -> Color(0xFF7C3AED)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetailsClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji de tipo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info de la llamada
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.contactName ?: item.phoneNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.contactName != null) {
                    Text(
                        text = item.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    if (item.duration > 0) {
                        Text(
                            text = "• ${formatDuration(item.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    if (item.isBlocked) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Bloqueada",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Botones de acción
            Column(
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Llamar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                TextButton(
                    onClick = onDetailsClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Saber más...",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Diálogo con detalles de la llamada - "Saber más..."
 */
@Composable
private fun CallDetailDialog(
    item: CallHistoryItem,
    onDismiss: () -> Unit,
    onCallBack: () -> Unit
) {
    val emoji = when (item.type) {
        CallType.INCOMING -> "📥"
        CallType.OUTGOING -> "📤"
        CallType.MISSED -> "📵"
        CallType.BLOCKED -> "🛡️"
    }
    
    val typeName = when (item.type) {
        CallType.INCOMING -> "Llamada entrante"
        CallType.OUTGOING -> "Llamada saliente"
        CallType.MISSED -> "Llamada perdida"
        CallType.BLOCKED -> "Llamada bloqueada"
    }
    
    val color = when (item.type) {
        CallType.INCOMING -> Color(0xFF10B981)
        CallType.OUTGOING -> Color(0xFF3B82F6)
        CallType.MISSED -> Color(0xFFEF4444)
        CallType.BLOCKED -> Color(0xFF7C3AED)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Cabecera
                Surface(
                    color = color.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Info del contacto
                        Text(
                            text = item.contactName ?: "Número desconocido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Contenido
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Fecha y hora
                    DetailRow(
                        icon = "📅",
                        label = "Fecha y hora",
                        value = formatTimestampFull(item.timestamp)
                    )

                    // Duración
                    if (item.duration > 0) {
                        DetailRow(
                            icon = "⏱️",
                            label = "Duración",
                            value = formatDurationFull(item.duration)
                        )
                    }

                    // Estado
                    DetailRow(
                        icon = if (item.isBlocked) "🛡️" else "📞",
                        label = "Estado",
                        value = if (item.isBlocked) "Bloqueada por SpamStopper" else "Completada"
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Explicación según tipo
                    Text(
                        text = "📖 Información",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = getExplanationForType(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                // Botones
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar")
                    }

                    Button(
                        onClick = onCallBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Llamar")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: String,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun getExplanationForType(item: CallHistoryItem): String {
    return when {
        item.isBlocked -> """
            Esta llamada fue bloqueada automáticamente por SpamStopper.
            
            🛡️ El sistema detectó patrones de spam o comportamiento sospechoso y decidió bloquear la llamada para protegerte.
            
            Si crees que fue un error, puedes llamar de vuelta desde este menú.
        """.trimIndent()

        item.type == CallType.MISSED -> """
            No pudiste contestar esta llamada.
            
            📵 La llamada entró pero no fue respondida. Puede que estuvieras ocupado o que el teléfono estuviera en silencio.
            
            Puedes devolver la llamada tocando el botón "Llamar".
        """.trimIndent()

        item.type == CallType.INCOMING -> """
            Llamada entrante que contestaste.
            
            📥 Esta llamada fue recibida y atendida correctamente.
        """.trimIndent()

        item.type == CallType.OUTGOING -> """
            Llamada que realizaste.
            
            📤 Tú iniciaste esta llamada hacia ${item.contactName ?: "este número"}.
        """.trimIndent()

        else -> "Información de la llamada no disponible."
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Hace un momento"
        diff < 3600_000 -> "Hace ${diff / 60_000} min"
        diff < 86400_000 -> "Hace ${diff / 3600_000} h"
        diff < 604800_000 -> "Hace ${diff / 86400_000} días"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatTimestampFull(timestamp: Long): String {
    return SimpleDateFormat("EEEE, d 'de' MMMM 'a las' HH:mm", Locale("es", "ES"))
        .format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private fun formatDurationFull(seconds: Long): String {
    return when {
        seconds < 60 -> "$seconds segundos"
        seconds < 3600 -> "${seconds / 60} minutos y ${seconds % 60} segundos"
        else -> "${seconds / 3600} horas y ${(seconds % 3600) / 60} minutos"
    }
}
