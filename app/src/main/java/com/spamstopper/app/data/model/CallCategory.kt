package com.spamstopper.app.data.model

/**
 * ============================================================================
 * CallCategory.kt - Categorías de llamadas con etiquetado detallado
 * ============================================================================
 *
 * PROPÓSITO:
 * Define todas las categorías posibles de llamadas detectadas por SpamStopper.
 * Cada categoría incluye emoji, nombre, descripción y explicación detallada
 * para mostrar en el diálogo "Saber más..." del historial.
 *
 * ACTUALIZADO: Enero 2026 - Añadidas explicaciones detalladas
 * ============================================================================
 */
enum class CallCategory(
    val emoji: String,
    val displayName: String,
    val shortDescription: String,
    val detailedExplanation: String
) {
    // ═══════════════════════════════════════════════════════════════════════
    // 🤖 ROBOTS Y MARCADORES AUTOMÁTICOS
    // ═══════════════════════════════════════════════════════════════════════

    SPAM_ROBOT(
        emoji = "🤖",
        displayName = "Marcador automático",
        shortDescription = "Robot o sistema automatizado detectado",
        detailedExplanation = """
            Se detectó un sistema de marcación automática (robocall).
            
            📊 Indicadores detectados:
            • Pitido inicial característico de centralitas
            • Silencio prolongado al inicio
            • Mensaje pregrabado o voz sintetizada
            
            ✅ Acción: Llamada colgada automáticamente.
        """.trimIndent()
    ),

    // ═══════════════════════════════════════════════════════════════════════
    // 📞 TELEMARKETING Y VENTAS
    // ═══════════════════════════════════════════════════════════════════════

    SPAM_TELEMARKETING(
        emoji = "📞",
        displayName = "Telemarketing",
        shortDescription = "Intento de venta detectado",
        detailedExplanation = """
            Llamada comercial de telemarketing detectada.
            
            📊 Palabras clave detectadas:
            • Frases de venta: "oferta", "promoción", "descuento"
            • Presión: "solo hoy", "última oportunidad"
            
            ✅ Acción: Llamada clasificada como spam.
        """.trimIndent()
    ),

    SPAM_INSURANCE(
        emoji = "🛡️",
        displayName = "Venta de seguros",
        shortDescription = "Oferta de pólizas de seguro",
        detailedExplanation = """
            Llamada de venta de seguros detectada.
            
            📊 Palabras clave: "póliza", "cobertura", "seguro de vida"
            
            ⚠️ Si necesitas seguro, contacta tú a la aseguradora.
            
            ✅ Acción: Llamada clasificada como spam comercial.
        """.trimIndent()
    ),

    SPAM_ENERGY(
        emoji = "⚡",
        displayName = "Compañía energética",
        shortDescription = "Oferta de luz o gas",
        detailedExplanation = """
            Llamada de comercial de energía detectada.
            
            📊 Palabras clave: "luz", "gas", "factura", "ahorro"
            
            🚨 Tu compañía NUNCA te pedirá datos bancarios por teléfono.
            
            ✅ Acción: Llamada clasificada como spam comercial.
        """.trimIndent()
    ),

    SPAM_TELECOM(
        emoji = "📱",
        displayName = "Operadora telefónica",
        shortDescription = "Oferta de fibra o móvil",
        detailedExplanation = """
            Llamada comercial de telecomunicaciones detectada.
            
            📊 Palabras clave: "fibra", "móvil", "portabilidad"
            
            ⚠️ Pide ofertas por escrito antes de aceptar.
            
            ✅ Acción: Llamada clasificada como spam comercial.
        """.trimIndent()
    ),

    SPAM_FINANCIAL(
        emoji = "💰",
        displayName = "Servicios financieros",
        shortDescription = "Oferta de préstamos o créditos",
        detailedExplanation = """
            Llamada de servicios financieros detectada.
            
            📊 Palabras clave: "préstamo", "crédito", "financiación"
            
            🚨 NUNCA des datos bancarios por teléfono.
            
            ✅ Acción: Llamada clasificada como spam financiero.
        """.trimIndent()
    ),

    SPAM_SCAM(
        emoji = "⚠️",
        displayName = "Posible estafa",
        shortDescription = "Patrones de fraude detectados",
        detailedExplanation = """
            ⚠️ ALERTA: Posible intento de estafa.
            
            📊 Indicadores: premios falsos, urgencia artificial, solicitud de datos
            
            ❌ NUNCA proporciones datos bancarios, claves o dinero.
            
            ✅ Acción: Llamada bloqueada por seguridad.
        """.trimIndent()
    ),

    SPAM_SURVEYS(
        emoji = "📋",
        displayName = "Encuesta telefónica",
        shortDescription = "Solicitud de encuesta",
        detailedExplanation = """
            Llamada de encuesta detectada.
            
            📊 Palabras clave: "encuesta", "opinión", "satisfacción"
            
            ⚠️ Muchas encuestas esconden ventas.
            
            ✅ Acción: Llamada clasificada como no deseada.
        """.trimIndent()
    ),

    SPAM_POLITICAL(
        emoji = "🗳️",
        displayName = "Propaganda política",
        shortDescription = "Llamada de campaña política",
        detailedExplanation = """
            Llamada de contenido político detectada.
            
            📊 Palabras clave: "partido", "elecciones", "votar"
            
            ℹ️ Puedes pedir que te eliminen de sus listas.
            
            ✅ Acción: Llamada clasificada como propaganda.
        """.trimIndent()
    ),

    SPAM_RELIGIOUS(
        emoji = "⛪",
        displayName = "Propaganda religiosa",
        shortDescription = "Llamada de proselitismo",
        detailedExplanation = """
            Llamada de contenido religioso detectada.
            
            📊 Palabras clave: "iglesia", "Dios", "salvación"
            
            ✅ Acción: Llamada clasificada como no deseada.
        """.trimIndent()
    ),

    SPAM_GENERIC(
        emoji = "🚫",
        displayName = "Spam genérico",
        shortDescription = "Llamada no deseada",
        detailedExplanation = """
            Llamada clasificada como spam.
            
            📊 Se detectaron múltiples indicadores de spam
            sin categoría específica.
            
            ✅ Acción: Llamada bloqueada por patrones de spam.
        """.trimIndent()
    ),

    // ═══════════════════════════════════════════════════════════════════════
    // ✅ LLAMADAS LEGÍTIMAS
    // ═══════════════════════════════════════════════════════════════════════

    LEGITIMATE_CONTACT(
        emoji = "👤",
        displayName = "Contacto guardado",
        shortDescription = "Número en tu lista de contactos",
        detailedExplanation = """
            Llamada de contacto guardado.
            
            ✅ El número está en tu lista de contactos.
            No requirió análisis adicional.
        """.trimIndent()
    ),

    LEGITIMATE_MENTIONS_USER(
        emoji = "👤",
        displayName = "Mencionó tu nombre",
        shortDescription = "El llamante dijo tu nombre",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ El llamante mencionó tu nombre.
            Esto indica que la llamada era específicamente para ti.
            
            🔔 Acción: Te alertamos con el tono configurado.
        """.trimIndent()
    ),

    LEGITIMATE_FAMILY(
        emoji = "👨‍👩‍👧",
        displayName = "Mencionó familiar",
        shortDescription = "Mencionó nombre de familia",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Se detectó uno de los nombres de familia configurados.
            
            🔔 Acción: Te alertamos inmediatamente.
        """.trimIndent()
    ),

    LEGITIMATE_EMERGENCY(
        emoji = "🚨",
        displayName = "Emergencia detectada",
        shortDescription = "Palabras de urgencia",
        detailedExplanation = """
            ⚠️ LLAMADA DE EMERGENCIA DETECTADA
            
            ✅ Se detectaron palabras de emergencia:
            "urgente", "emergencia", "hospital", etc.
            
            🔔 Acción: Alerta prioritaria activada.
        """.trimIndent()
    ),

    LEGITIMATE_WORK(
        emoji = "💼",
        displayName = "Relacionada con trabajo",
        shortDescription = "Contexto laboral detectado",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Contexto de trabajo detectado:
            "trabajo", "oficina", "reunión", "cliente"
            
            🔔 Acción: Te alertamos con el tono configurado.
        """.trimIndent()
    ),

    LEGITIMATE_DELIVERY(
        emoji = "📦",
        displayName = "Entrega o paquete",
        shortDescription = "Servicio de mensajería",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Servicio de entrega detectado:
            "paquete", "entrega", "envío", "mensajero"
            
            🔔 Acción: Te alertamos con el tono configurado.
        """.trimIndent()
    ),

    LEGITIMATE_MEDICAL(
        emoji = "🏥",
        displayName = "Tema médico",
        shortDescription = "Hospital o centro de salud",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Contexto médico detectado:
            "hospital", "médico", "cita", "consulta"
            
            🔔 Acción: Te alertamos inmediatamente.
        """.trimIndent()
    ),

    LEGITIMATE_OFFICIAL(
        emoji = "🏛️",
        displayName = "Entidad oficial",
        shortDescription = "Organismo público",
        detailedExplanation = """
            Llamada posiblemente legítima.
            
            ✅ Entidad oficial mencionada:
            Hacienda, Seguridad Social, Ayuntamiento
            
            ⚠️ NUNCA te pedirán datos bancarios por teléfono.
            
            🔔 Acción: Te alertamos para que decidas.
        """.trimIndent()
    ),

    LEGITIMATE_SCHOOL(
        emoji = "🏫",
        displayName = "Colegio o escuela",
        shortDescription = "Centro educativo",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Contexto escolar detectado:
            "colegio", "profesor", "tutor", "niño"
            
            🔔 Acción: Te alertamos inmediatamente.
        """.trimIndent()
    ),

    LEGITIMATE_HUMAN(
        emoji = "💬",
        displayName = "Conversación personal",
        shortDescription = "Llamada humana legítima",
        detailedExplanation = """
            Llamada verificada como legítima.
            
            ✅ Patrón de conversación humana detectado.
            Ausencia de indicadores de spam.
            
            🔔 Acción: Te alertamos con el tono configurado.
        """.trimIndent()
    ),

    // ═══════════════════════════════════════════════════════════════════════
    // 🤔 INCIERTOS Y ERRORES
    // ═══════════════════════════════════════════════════════════════════════

    UNCERTAIN(
        emoji = "❓",
        displayName = "No determinado",
        shortDescription = "No se pudo clasificar",
        detailedExplanation = """
            Llamada sin clasificación definitiva.
            
            🤔 Posibles causas:
            • Audio insuficiente
            • Indicadores mixtos
            • Llamada muy corta
            
            🔔 Acción: Te alertamos por precaución.
        """.trimIndent()
    ),

    ERROR_NO_AUDIO(
        emoji = "🔇",
        displayName = "Sin audio",
        shortDescription = "No se captó audio",
        detailedExplanation = """
            Error: Sin audio para analizar.
            
            ❌ Posibles causas:
            • Llamada colgó antes del análisis
            • Problemas con el micrófono
            • El llamante no habló
            
            🔔 Acción: Te alertamos por precaución.
        """.trimIndent()
    ),

    ERROR_ANALYSIS_FAILED(
        emoji = "⚙️",
        displayName = "Error de análisis",
        shortDescription = "Fallo durante el procesamiento",
        detailedExplanation = """
            Error técnico durante el análisis.
            
            ❌ El sistema de análisis falló.
            
            ⚙️ Si ocurre frecuentemente, reinicia la app.
            
            🔔 Acción: Te alertamos por precaución.
        """.trimIndent()
    );

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ═══════════════════════════════════════════════════════════════════════

    fun isSpam(): Boolean = name.startsWith("SPAM_")
    fun isLegitimate(): Boolean = name.startsWith("LEGITIMATE_")
    fun isError(): Boolean = name.startsWith("ERROR_")
    fun isUncertain(): Boolean = this == UNCERTAIN
    fun wasBlocked(): Boolean = isSpam()
    fun wasAlerted(): Boolean = isLegitimate() || isUncertain() || isError()

    fun getColorHex(): String = when {
        isLegitimate() -> "#10B981"
        this == SPAM_SCAM -> "#DC2626"
        this == SPAM_ROBOT -> "#7C3AED"
        isSpam() -> "#EF4444"
        isError() -> "#F59E0B"
        else -> "#6B7280"
    }

    fun getActionText(): String = when {
        wasBlocked() -> "🛡️ Llamada bloqueada automáticamente"
        wasAlerted() -> "🔔 Se te alertó de esta llamada"
        else -> "ℹ️ Llamada registrada"
    }

    companion object {
        fun getSpamCategories(): List<CallCategory> = entries.filter { it.isSpam() }
        fun getLegitimateCategories(): List<CallCategory> = entries.filter { it.isLegitimate() }
    }
}
