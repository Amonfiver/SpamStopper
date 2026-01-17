package com.spamstopper.app.domain.model

/**
 * Decisión sobre una llamada entrante
 */
enum class CallDecision {
    /**
     * Contacto guardado - Pasar directo sin análisis
     */
    ALLOW_DIRECT,

    /**
     * Número desconocido analizado - No es spam, permitir
     */
    ALLOW,

    /**
     * Detectado como spam - Bloquear
     */
    BLOCK_SPAM,

    /**
     * Detectado como robot/IVR - Bloquear automáticamente
     */
    BLOCK_ROBOT,

    /**
     * Palabras clave de emergencia detectadas - Alertar con urgencia
     */
    ALERT_EMERGENCY;

    /**
     * Indica si la llamada debe ser bloqueada
     */
    fun shouldBlock(): Boolean = this in listOf(BLOCK_SPAM, BLOCK_ROBOT)

    /**
     * Indica si la llamada debe permitirse
     */
    fun shouldAllow(): Boolean = this in listOf(ALLOW_DIRECT, ALLOW, ALERT_EMERGENCY)

    /**
     * Indica si requiere notificación al usuario
     */
    fun requiresNotification(): Boolean = this != ALLOW_DIRECT

    /**
     * Obtiene el mensaje descriptivo de la decisión
     */
    fun getDescription(): String = when (this) {
        ALLOW_DIRECT -> "Contacto guardado - Llamada permitida"
        ALLOW -> "Llamada analizada - No es spam"
        BLOCK_SPAM -> "Llamada bloqueada - Detectado como spam"
        BLOCK_ROBOT -> "Llamada bloqueada - Robot/IVR detectado"
        ALERT_EMERGENCY -> "⚠️ EMERGENCIA - Palabras clave detectadas"
    }

    /**
     * Obtiene el emoji representativo
     */
    fun getEmoji(): String = when (this) {
        ALLOW_DIRECT -> "✅"
        ALLOW -> "✔️"
        BLOCK_SPAM -> "🚫"
        BLOCK_ROBOT -> "🤖"
        ALERT_EMERGENCY -> "🚨"
    }
}