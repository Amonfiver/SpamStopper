package com.spamstopper.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyItems: List<CallHistoryItem>,
    onCallClick: (CallHistoryItem) -> Unit,
    onFilterChange: (CallTypeFilter) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CallTypeFilter.ALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filtros
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
                label = { Text("Todas") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            FilterChip(
                selected = selectedFilter == CallTypeFilter.INCOMING,
                onClick = {
                    selectedFilter = CallTypeFilter.INCOMING
                    onFilterChange(CallTypeFilter.INCOMING)
                },
                label = { Text("Entrantes") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            FilterChip(
                selected = selectedFilter == CallTypeFilter.OUTGOING,
                onClick = {
                    selectedFilter = CallTypeFilter.OUTGOING
                    onFilterChange(CallTypeFilter.OUTGOING)
                },
                label = { Text("Salientes") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            FilterChip(
                selected = selectedFilter == CallTypeFilter.MISSED,
                onClick = {
                    selectedFilter = CallTypeFilter.MISSED
                    onFilterChange(CallTypeFilter.MISSED)
                },
                label = { Text("Perdidas") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        // Lista de llamadas
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No hay llamadas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El historial de llamadas aparecerá aquí",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyItems) { item ->
                    CallHistoryCard(
                        item = item,
                        onCallClick = { onCallClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHistoryCard(
    item: CallHistoryItem,
    onCallClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCallClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icono según tipo de llamada
                Icon(
                    imageVector = when (item.type) {
                        CallType.INCOMING -> Icons.Default.Phone
                        CallType.OUTGOING -> Icons.Default.Phone
                        CallType.MISSED -> Icons.Default.Phone
                        CallType.BLOCKED -> Icons.Default.Cancel
                    },
                    contentDescription = null,
                    tint = when (item.type) {
                        CallType.INCOMING -> Color(0xFF4CAF50)
                        CallType.OUTGOING -> Color(0xFF2196F3)
                        CallType.MISSED -> Color(0xFFF44336)
                        CallType.BLOCKED -> Color(0xFF9E9E9E)
                    },
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = item.contactName ?: item.phoneNumber,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    if (item.duration > 0) {
                        Text(
                            text = formatDuration(item.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Botón de llamar
            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Llamar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${secs}s"
    } else {
        "${secs}s"
    }
}