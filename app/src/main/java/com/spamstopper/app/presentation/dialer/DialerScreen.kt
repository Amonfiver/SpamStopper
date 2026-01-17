package com.spamstopper.app.presentation.dialer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DialerScreen(
    phoneNumber: String,
    onNumberChange: (String) -> Unit,
    onCallClick: () -> Unit,
    onHangUpClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onContactsClick: () -> Unit,
    isCallInProgress: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Display del número (área fija superior)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = phoneNumber.ifEmpty { "Marca un número" },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (phoneNumber.isEmpty()) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Teclado numérico (área fija central)
        DialPad(
            onDigitClick = { digit ->
                onNumberChange(digit)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botones de acción (área fija inferior)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de contactos
            IconButton(
                onClick = onContactsClick,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Contactos",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Botón de llamar/colgar
            FloatingActionButton(
                onClick = {
                    if (isCallInProgress) {
                        onHangUpClick()
                    } else {
                        onCallClick()
                    }
                },
                modifier = Modifier.size(64.dp),
                containerColor = if (isCallInProgress) {
                    Color.Red
                } else {
                    Color(0xFF4CAF50)
                },
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isCallInProgress) {
                        Icons.Default.CallEnd
                    } else {
                        Icons.Default.Call
                    },
                    contentDescription = if (isCallInProgress) "Colgar" else "Llamar",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }

            // Botón de borrar (SIEMPRE VISIBLE)
            IconButton(
                onClick = onDeleteClick,
                enabled = phoneNumber.isNotEmpty(),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Borrar",
                    modifier = Modifier.size(32.dp),
                    tint = if (phoneNumber.isNotEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
        }
    }
}

@Composable
private fun DialPad(
    onDigitClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fila 1: 1, 2, 3
        DialRow(
            buttons = listOf("1", "2", "3"),
            onDigitClick = onDigitClick
        )

        // Fila 2: 4, 5, 6
        DialRow(
            buttons = listOf("4", "5", "6"),
            onDigitClick = onDigitClick
        )

        // Fila 3: 7, 8, 9
        DialRow(
            buttons = listOf("7", "8", "9"),
            onDigitClick = onDigitClick
        )

        // Fila 4: *, 0, #
        DialRow(
            buttons = listOf("*", "0", "#"),
            onDigitClick = onDigitClick
        )
    }
}

@Composable
private fun DialRow(
    buttons: List<String>,
    onDigitClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        buttons.forEach { digit ->
            DialButton(
                digit = digit,
                onClick = { onDigitClick(digit) }
            )
        }
    }
}

@Composable
private fun DialButton(
    digit: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = digit,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}