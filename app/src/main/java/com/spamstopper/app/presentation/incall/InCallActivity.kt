package com.spamstopper.app.presentation.incall

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spamstopper.app.services.SpamInCallService
import com.spamstopper.app.ui.theme.SpamStopperTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * InCallActivity - Pantalla de llamada
 *
 * Muestra la UI durante llamadas entrantes y en curso.
 * Se comunica con SpamInCallService para controlar la llamada.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CONTACT_NAME = "contact_name"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var callDurationSeconds = 0
    private var durationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrar sobre pantalla de bloqueo (API 27+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        val isIncoming = intent.getBooleanExtra("is_incoming", false)

        android.util.Log.d("InCallActivity", "═══════════════════════════════════════")
        android.util.Log.d("InCallActivity", "🖥️ InCallActivity CREADA")
        android.util.Log.d("InCallActivity", "   Número: $phoneNumber")
        android.util.Log.d("InCallActivity", "   Contacto: $contactName")
        android.util.Log.d("InCallActivity", "   Entrante: $isIncoming")
        android.util.Log.d("InCallActivity", "═══════════════════════════════════════")

        setContent {
            SpamStopperTheme {
                InCallScreen(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    initialIsIncoming = isIncoming
                )
            }
        }
    }

    @Composable
    private fun InCallScreen(
        phoneNumber: String,
        contactName: String?,
        initialIsIncoming: Boolean
    ) {
        // Estado de la llamada
        var callState by remember { mutableStateOf(SpamInCallService.getCallState()) }
        var duration by remember { mutableStateOf(0) }
        var isSpeakerOn by remember { mutableStateOf(false) }
        var isMuteOn by remember { mutableStateOf(false) }

        // Actualizar estado cada 500ms
        LaunchedEffect(Unit) {
            while (true) {
                callState = SpamInCallService.getCallState()

                // Contar duración si está activa
                if (callState == Call.STATE_ACTIVE) {
                    duration++
                }

                // Cerrar si desconectada
                if (callState == Call.STATE_DISCONNECTED) {
                    kotlinx.coroutines.delay(1000)
                    finish()
                    break
                }

                kotlinx.coroutines.delay(1000)
            }
        }

        val isRinging = callState == Call.STATE_RINGING
        val isActive = callState == Call.STATE_ACTIVE
        val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isRinging) Color(0xFF1B5E20) else MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                // Estado de la llamada
                Text(
                    text = when {
                        isRinging -> "Llamada entrante"
                        isDialing -> "Llamando..."
                        isActive -> "En llamada"
                        else -> "Conectando..."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isRinging) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Nombre del contacto o número
                Text(
                    text = contactName ?: formatPhoneNumber(phoneNumber),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = if (isRinging) Color.White else MaterialTheme.colorScheme.onSurface
                )

                // Si hay contactName, mostrar número debajo
                if (contactName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatPhoneNumber(phoneNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isRinging) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Duración (solo si activa)
                if (isActive) {
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Animación de llamando (si está sonando)
                if (isRinging) {
                    Spacer(modifier = Modifier.height(32.dp))
                    PulsingIcon()
                }

                Spacer(modifier = Modifier.weight(1f))

                // ═══════════════════════════════════════
                // CONTROLES
                // ═══════════════════════════════════════

                if (isRinging) {
                    // Botones de CONTESTAR / RECHAZAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Rechazar
                        CallActionButton(
                            icon = Icons.Default.CallEnd,
                            label = "Rechazar",
                            backgroundColor = Color(0xFFD32F2F),
                            onClick = {
                                SpamInCallService.instance?.rejectCall()
                            }
                        )

                        // Contestar
                        CallActionButton(
                            icon = Icons.Default.Call,
                            label = "Contestar",
                            backgroundColor = Color(0xFF4CAF50),
                            onClick = {
                                SpamInCallService.instance?.answerCall()
                            }
                        )
                    }
                } else {
                    // Controles de llamada activa
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Mute
                        SmallActionButton(
                            icon = if (isMuteOn) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Mute",
                            isActive = isMuteOn,
                            onClick = {
                                isMuteOn = !isMuteOn
                                SpamInCallService.instance?.setMuteOn(isMuteOn)
                            }
                        )

                        // Speaker
                        SmallActionButton(
                            icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp
                            else Icons.AutoMirrored.Filled.VolumeDown,
                            label = "Altavoz",
                            isActive = isSpeakerOn,
                            onClick = {
                                isSpeakerOn = !isSpeakerOn
                                SpamInCallService.instance?.setSpeakerOn(isSpeakerOn)
                            }
                        )

                        // Teclado (placeholder)
                        SmallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Teclado",
                            isActive = false,
                            onClick = { /* TODO: Mostrar dialpad */ }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Botón de colgar
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        label = "Colgar",
                        backgroundColor = Color(0xFFD32F2F),
                        onClick = {
                            SpamInCallService.instance?.hangUp()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    @Composable
    private fun CallActionButton(
        icon: ImageVector,
        label: String,
        backgroundColor: Color,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.size(72.dp),
                containerColor = backgroundColor,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @Composable
    private fun SmallActionButton(
        icon: ImageVector,
        label: String,
        isActive: Boolean,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun PulsingIcon() {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
    }

    private fun formatPhoneNumber(number: String): String {
        // Formato básico para números españoles
        return if (number.length == 9) {
            "${number.substring(0, 3)} ${number.substring(3, 6)} ${number.substring(6)}"
        } else {
            number
        }
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        android.util.Log.d("InCallActivity", "🛑 InCallActivity DESTRUIDA")
    }
}