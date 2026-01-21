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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spamstopper.app.services.SpamInCallService
import com.spamstopper.app.ui.theme.SpamStopperTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * ============================================================================
 * InCallActivity.kt - Pantalla de llamada en curso
 * ============================================================================
 *
 * PROPÓSITO:
 * Muestra la UI durante llamadas entrantes, verificadas y en curso.
 * Se comunica con SpamInCallService para controlar la llamada.
 *
 * ACTUALIZADO: Enero 2026 - Añadido estado "verificada esperando contestar"
 * ============================================================================
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_IS_VERIFIED = "is_verified"
        const val EXTRA_VERIFICATION_REASON = "verification_reason"
        const val EXTRA_VERIFICATION_EMOJI = "verification_emoji"
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrar sobre pantalla de bloqueo
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
        val isVerified = intent.getBooleanExtra(EXTRA_IS_VERIFIED, false)
        val verificationReason = intent.getStringExtra(EXTRA_VERIFICATION_REASON)
        val verificationEmoji = intent.getStringExtra(EXTRA_VERIFICATION_EMOJI) ?: "✅"

        // También obtener datos del análisis
        val analysisDecision = intent.getStringExtra("analysis_decision")
        val analysisReason = intent.getStringExtra("analysis_reason")

        // Determinar si viene de verificación de Secretary Mode
        val fromSecretaryMode = analysisDecision != null || isVerified

        android.util.Log.d("InCallActivity", "═══════════════════════════════════════")
        android.util.Log.d("InCallActivity", "🖥️ InCallActivity CREADA")
        android.util.Log.d("InCallActivity", "   Número: $phoneNumber")
        android.util.Log.d("InCallActivity", "   Contacto: $contactName")
        android.util.Log.d("InCallActivity", "   Entrante: $isIncoming")
        android.util.Log.d("InCallActivity", "   Verificada: $fromSecretaryMode")
        android.util.Log.d("InCallActivity", "   Razón: $analysisReason")
        android.util.Log.d("InCallActivity", "═══════════════════════════════════════")

        setContent {
            SpamStopperTheme {
                InCallScreen(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    initialIsIncoming = isIncoming,
                    isFromSecretaryMode = fromSecretaryMode,
                    verificationReason = analysisReason ?: verificationReason,
                    verificationEmoji = verificationEmoji
                )
            }
        }
    }

    @Composable
    private fun InCallScreen(
        phoneNumber: String,
        contactName: String?,
        initialIsIncoming: Boolean,
        isFromSecretaryMode: Boolean,
        verificationReason: String?,
        verificationEmoji: String
    ) {
        // Estado de la llamada
        var callState by remember { mutableStateOf(SpamInCallService.getCallState()) }
        var duration by remember { mutableStateOf(0) }
        var isSpeakerOn by remember { mutableStateOf(false) }
        var isMuteOn by remember { mutableStateOf(false) }
        
        // Estado especial: llamada verificada esperando que el usuario conteste
        var isWaitingUserAnswer by remember { mutableStateOf(isFromSecretaryMode) }
        var hasUserAnswered by remember { mutableStateOf(false) }

        // Actualizar estado cada 500ms
        LaunchedEffect(Unit) {
            while (true) {
                callState = SpamInCallService.getCallState()

                // Contar duración si el usuario ya contestó
                if (hasUserAnswered && callState == Call.STATE_ACTIVE) {
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

        // Determinar el modo de visualización
        val showVerifiedWaiting = isWaitingUserAnswer && !hasUserAnswered && isActive
        val showRingingControls = isRinging
        val showActiveControls = (isActive && hasUserAnswered) || (isActive && !isFromSecretaryMode && !isRinging)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = when {
                showVerifiedWaiting -> Color(0xFF1565C0) // Azul para verificada
                showRingingControls -> Color(0xFF1B5E20) // Verde para entrante
                else -> MaterialTheme.colorScheme.background
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // ═══════════════════════════════════════════════════════════════
                // BADGE DE VERIFICACIÓN (si viene de Secretary Mode)
                // ═══════════════════════════════════════════════════════════════
                
                if (showVerifiedWaiting) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🛡️",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verificada por SpamStopper",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Estado de la llamada
                Text(
                    text = when {
                        showVerifiedWaiting -> "📞 Llamada verificada"
                        isRinging -> "Llamada entrante"
                        isDialing -> "Llamando..."
                        isActive -> "En llamada"
                        else -> "Conectando..."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        showVerifiedWaiting || showRingingControls -> Color.White.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Nombre del contacto o número
                Text(
                    text = contactName ?: formatPhoneNumber(phoneNumber),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = when {
                        showVerifiedWaiting || showRingingControls -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                // Si hay contactName, mostrar número debajo
                if (contactName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatPhoneNumber(phoneNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            showVerifiedWaiting || showRingingControls -> Color.White.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // ═══════════════════════════════════════════════════════════════
                // RAZÓN DE VERIFICACIÓN
                // ═══════════════════════════════════════════════════════════════
                
                if (showVerifiedWaiting && verificationReason != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = getReasonEmoji(verificationReason),
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = getReasonDisplayName(verificationReason),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = getReasonDescription(verificationReason),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Duración (solo si el usuario contestó)
                if (hasUserAnswered && isActive) {
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Animación de llamando
                if (showVerifiedWaiting || showRingingControls) {
                    Spacer(modifier = Modifier.height(32.dp))
                    PulsingIcon(
                        isVerified = showVerifiedWaiting
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // ═══════════════════════════════════════════════════════════════
                // CONTROLES
                // ═══════════════════════════════════════════════════════════════

                when {
                    // CASO 1: Llamada verificada esperando que el usuario conteste
                    showVerifiedWaiting -> {
                        Text(
                            text = "🔔 Toca Contestar para hablar",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Rechazar
                            CallActionButton(
                                icon = Icons.Default.CallEnd,
                                label = "Colgar",
                                backgroundColor = Color(0xFFD32F2F),
                                onClick = {
                                    SpamInCallService.instance?.hangUp()
                                }
                            )

                            // Contestar (para el tono y continúa la llamada)
                            CallActionButton(
                                icon = Icons.Default.Call,
                                label = "Contestar",
                                backgroundColor = Color(0xFF4CAF50),
                                onClick = {
                                    // Parar el tono
                                    SpamInCallService.instance?.userAnsweredVerifiedCall()
                                    hasUserAnswered = true
                                    isWaitingUserAnswer = false
                                    
                                    android.util.Log.d("InCallActivity", "✅ Usuario contestó llamada verificada")
                                }
                            )
                        }
                    }
                    
                    // CASO 2: Llamada entrante normal (sin verificar)
                    showRingingControls -> {
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
                    }
                    
                    // CASO 3: Llamada activa (usuario ya contestó o llamada saliente)
                    else -> {
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

                            // Teclado
                            SmallActionButton(
                                icon = Icons.Default.Dialpad,
                                label = "Teclado",
                                isActive = false,
                                onClick = { /* TODO */ }
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
                color = Color.White
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
    private fun PulsingIcon(isVerified: Boolean = false) {
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
                imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Call,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
    }

    private fun getReasonEmoji(reason: String?): String {
        return when (reason) {
            "SAID_USER_NAME" -> "👤"
            "SAID_FAMILY_NAME" -> "👨‍👩‍👧"
            "WORK_RELATED" -> "💼"
            "EMERGENCY_KEYWORDS" -> "🚨"
            "OFFICIAL_ENTITY" -> "🏛️"
            "MEDICAL" -> "🏥"
            "SCHOOL" -> "🏫"
            "DELIVERY" -> "📦"
            "HUMAN_CONVERSATION" -> "💬"
            else -> "✅"
        }
    }

    private fun getReasonDisplayName(reason: String?): String {
        return when (reason) {
            "SAID_USER_NAME" -> "Mencionó tu nombre"
            "SAID_FAMILY_NAME" -> "Mencionó a un familiar"
            "WORK_RELATED" -> "Relacionado con trabajo"
            "EMERGENCY_KEYWORDS" -> "Palabras de emergencia"
            "OFFICIAL_ENTITY" -> "Entidad oficial"
            "MEDICAL" -> "Tema médico"
            "SCHOOL" -> "Colegio o escuela"
            "DELIVERY" -> "Entrega o paquete"
            "HUMAN_CONVERSATION" -> "Conversación humana"
            else -> "Llamada verificada"
        }
    }

    private fun getReasonDescription(reason: String?): String {
        return when (reason) {
            "SAID_USER_NAME" -> "El llamante dijo tu nombre configurado"
            "SAID_FAMILY_NAME" -> "Mencionó uno de tus contactos familiares"
            "WORK_RELATED" -> "Se detectó contexto laboral"
            "EMERGENCY_KEYWORDS" -> "Se detectaron palabras de urgencia"
            "OFFICIAL_ENTITY" -> "Posible entidad gubernamental"
            "MEDICAL" -> "Contexto médico o sanitario"
            "SCHOOL" -> "Relacionado con educación"
            "DELIVERY" -> "Servicio de mensajería"
            "HUMAN_CONVERSATION" -> "Patrón de conversación normal"
            else -> "SpamStopper verificó esta llamada"
        }
    }

    private fun formatPhoneNumber(number: String): String {
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
