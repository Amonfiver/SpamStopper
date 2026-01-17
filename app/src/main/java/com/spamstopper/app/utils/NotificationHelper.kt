package com.spamstopper.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.spamstopper.app.R
import com.spamstopper.app.domain.model.CallDecision
import com.spamstopper.app.presentation.MainActivity
import com.spamstopper.app.services.ai.EmergencyType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper para gestionar notificaciones
 *
 * Tipos de notificaciones:
 * - Emergencia detectada (máxima prioridad)
 * - Robot bloqueado
 * - Spam bloqueado
 * - Llamada analizada (modo debug)
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Canales de notificación
        private const val CHANNEL_EMERGENCY = "emergency_channel"
        private const val CHANNEL_BLOCKED = "blocked_channel"
        private const val CHANNEL_ANALYSIS = "analysis_channel"

        // IDs de notificación
        private const val NOTIFICATION_EMERGENCY = 1001
        private const val NOTIFICATION_ROBOT = 1002
        private const val NOTIFICATION_SPAM = 1003
        private const val NOTIFICATION_ANALYSIS = 1004
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Crea los canales de notificación (Android 8+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal de emergencias (máxima prioridad)
            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergencias",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas de emergencia"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            // Canal de llamadas bloqueadas
            val blockedChannel = NotificationChannel(
                CHANNEL_BLOCKED,
                "Llamadas Bloqueadas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de spam y robots bloqueados"
                setShowBadge(true)
            }

            // Canal de análisis (debug)
            val analysisChannel = NotificationChannel(
                CHANNEL_ANALYSIS,
                "Análisis de Llamadas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Información sobre llamadas analizadas"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(emergencyChannel, blockedChannel, analysisChannel)
            )

            android.util.Log.d("NotificationHelper", "✅ Canales de notificación creados")
        }
    }

    /**
     * Notifica una emergencia detectada
     */
    fun notifyEmergency(
        phoneNumber: String,
        emergencyType: EmergencyType?,
        detectedKeywords: List<String>
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCIA DETECTADA")
            .setContentText("Llamada de $phoneNumber")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Número: $phoneNumber\n" +
                                "Tipo: ${emergencyType?.getDescription() ?: "Desconocido"}\n" +
                                "Palabras clave: ${detectedKeywords.joinToString(", ")}"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(NOTIFICATION_EMERGENCY, notification)

        android.util.Log.d("NotificationHelper", "🚨 Notificación de emergencia enviada")
    }

    /**
     * Notifica un robot bloqueado
     */
    fun notifyRobotBlocked(
        phoneNumber: String,
        confidence: Float,
        detectedPatterns: List<String>
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("🤖 Robot Bloqueado")
            .setContentText("Llamada automática de $phoneNumber")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Número: $phoneNumber\n" +
                                "Confianza: ${(confidence * 100).toInt()}%\n" +
                                "Patrones: ${detectedPatterns.joinToString(", ")}"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ROBOT, notification)

        android.util.Log.d("NotificationHelper", "🤖 Notificación de robot bloqueado")
    }

    /**
     * Notifica spam bloqueado
     */
    fun notifySpamBlocked(
        phoneNumber: String,
        spamScore: Float,
        reason: String
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("🚫 Spam Bloqueado")
            .setContentText("Llamada comercial de $phoneNumber")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Número: $phoneNumber\n" +
                                "Probabilidad de spam: ${(spamScore * 100).toInt()}%\n" +
                                "Razón: $reason"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_SPAM, notification)

        android.util.Log.d("NotificationHelper", "🚫 Notificación de spam bloqueado")
    }

    /**
     * Notifica análisis completado (modo debug)
     */
    fun notifyAnalysisComplete(
        phoneNumber: String,
        decision: CallDecision,
        details: String
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANALYSIS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${decision.getEmoji()} Llamada Analizada")
            .setContentText("$phoneNumber - ${decision.getDescription()}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Número: $phoneNumber\n" +
                                "Decisión: ${decision.getDescription()}\n" +
                                "Detalles: $details"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ANALYSIS, notification)

        android.util.Log.d("NotificationHelper", "ℹ️ Notificación de análisis enviada")
    }

    /**
     * Cancela todas las notificaciones
     */
    fun cancelAll() {
        notificationManager.cancelAll()
        android.util.Log.d("NotificationHelper", "🔕 Todas las notificaciones canceladas")
    }

    /**
     * Cancela una notificación específica
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}