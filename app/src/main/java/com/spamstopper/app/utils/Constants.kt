package com.spamstopper.app.utils

/**
 * ============================================================================
 * Constants.kt - Constantes globales de SpamStopper
 * ============================================================================
 *
 * ACTUALIZADO: Enero 2026 - Añadidas constantes para tono de notificación
 * ============================================================================
 */
object Constants {
    // ═══════════════════════════════════════════════════════════════════════
    // SHARED PREFERENCES
    // ═══════════════════════════════════════════════════════════════════════

    const val PREFS_NAME = "spamstopper_prefs"
    const val PREF_AUTO_ANSWER_ENABLED = "auto_answer_enabled"
    const val PREF_ALLOW_CONTACTS = "allow_contacts"
    const val PREF_NOTIFICATION_RINGTONE = "notification_ringtone_uri"
    const val PREF_USER_NAME = "user_name"
    const val PREF_FAMILY_NAMES = "family_names"
    const val PREF_EMERGENCY_KEYWORDS = "emergency_keywords"
    const val PREF_ANALYSIS_DURATION = "analysis_duration"
    const val PREF_SPAM_SENSITIVITY = "spam_sensitivity"
    const val PREF_AUTO_BLOCK_ROBOTS = "auto_block_robots"
    const val PREF_AUTO_BLOCK_SPAM = "auto_block_spam"
    const val PREF_SHOW_ANALYSIS_NOTIFICATIONS = "show_analysis_notifications"
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    const val PREF_VIBRATION_ENABLED = "vibration_enabled"

    // ═══════════════════════════════════════════════════════════════════════
    // CANALES DE NOTIFICACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    const val CHANNEL_INCOMING_CALL = "incoming_call_channel"
    const val CHANNEL_SECRETARY_MODE = "secretary_mode_channel"
    const val CHANNEL_BLOCKED_CALL = "blocked_call_channel"
    const val CHANNEL_LEGITIMATE_CALL = "legitimate_call_channel"

    // ═══════════════════════════════════════════════════════════════════════
    // IDs DE NOTIFICACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    const val NOTIFICATION_INCOMING_CALL = 2001
    const val NOTIFICATION_ANALYZING = 2002
    const val NOTIFICATION_BLOCKED = 2003
    const val NOTIFICATION_LEGITIMATE = 2004

    // ═══════════════════════════════════════════════════════════════════════
    // TIEMPOS
    // ═══════════════════════════════════════════════════════════════════════

    const val DEFAULT_ANALYSIS_DURATION_MS = 12000L
    const val MIN_ANALYSIS_DURATION_MS = 5000L
    const val MAX_ANALYSIS_DURATION_MS = 20000L
    const val AUDIO_CHUNK_INTERVAL_MS = 2000L
    const val AUTO_ANSWER_DELAY_MS = 300L

    // ═══════════════════════════════════════════════════════════════════════
    // UMBRALES
    // ═══════════════════════════════════════════════════════════════════════

    const val SPAM_CONFIDENCE_THRESHOLD = 0.6f
    const val ROBOT_CONFIDENCE_THRESHOLD = 0.7f
    const val LEGITIMATE_CONFIDENCE_THRESHOLD = 0.5f
    const val EMERGENCY_CONFIDENCE_THRESHOLD = 0.4f

    // ═══════════════════════════════════════════════════════════════════════
    // BASE DE DATOS
    // ═══════════════════════════════════════════════════════════════════════

    const val DATABASE_NAME = "spamstopper_database"
    const val DATABASE_VERSION = 2

    // ═══════════════════════════════════════════════════════════════════════
    // VALORES PREDETERMINADOS
    // ═══════════════════════════════════════════════════════════════════════

    const val DEFAULT_ANALYSIS_SECONDS = 12
    const val DEFAULT_SENSITIVITY = 0.5f

    val DEFAULT_EMERGENCY_KEYWORDS = setOf(
        "urgente", "emergencia", "accidente", "hospital",
        "ambulancia", "policía", "ayuda", "grave"
    )
}
