package com.spamstopper.app.services.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detector de llamadas robotizadas (IVR)
 *
 * Identifica:
 * 1. Pitidos característicos de marcadores automáticos
 * 2. Patrones de menús IVR en el texto
 * 3. Mensajes pregrabados
 */
@Singleton
class RobotCallDetector @Inject constructor() {

    companion object {
        private const val TAG = "RobotDetector"

        // Frecuencias típicas de pitidos de marcadores automáticos
        private const val BEEP_FREQ_LOW = 400f   // Hz
        private const val BEEP_FREQ_HIGH = 1200f // Hz
        private const val SAMPLE_RATE = 16000    // Hz

        // Umbral de detección de pitido
        private const val BEEP_THRESHOLD = 0.6f
    }

    // Patrones de menús IVR en español
    private val ivrPatterns = listOf(
        Regex("\\bpulse\\b.*\\d+", RegexOption.IGNORE_CASE),
        Regex("\\bpresione\\b.*\\d+", RegexOption.IGNORE_CASE),
        Regex("\\bmarque\\b.*\\d+", RegexOption.IGNORE_CASE),
        Regex("\\boprima\\b.*\\d+", RegexOption.IGNORE_CASE),
        Regex("\\bpara\\b.*\\bpulse\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpara\\b.*\\bpresione\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmarcar\\b.*\\bopción\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsi desea\\b.*\\bmarque\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmenú principal\\b", RegexOption.IGNORE_CASE),
        Regex("\\bseleccione una opción\\b", RegexOption.IGNORE_CASE),
        Regex("\\bopción\\s+\\d+", RegexOption.IGNORE_CASE),
        Regex("\\bpara\\s+\\w+\\s+pulse\\b", RegexOption.IGNORE_CASE)
    )

    // Frases típicas de mensajes automatizados
    private val automatedPhrases = listOf(
        "este es un mensaje automático",
        "mensaje grabado",
        "sistema automático",
        "llamada automática",
        "no cuelgue",
        "permanezca en línea",
        "su llamada es importante",
        "el siguiente operador disponible",
        "todos nuestros operadores están ocupados",
        "gracias por llamar a",
        "su tiempo de espera estimado",
        "está siendo grabada",
        "con fines de calidad",
        "pulse asterisco",
        "pulse almohadilla"
    )

    // Palabras clave de robots
    private val robotKeywords = setOf(
        "pulse", "presione", "marque", "oprima", "opción",
        "menú", "seleccione", "teclee", "digite", "asterisco",
        "almohadilla", "espera", "transferir", "extensión"
    )

    /**
     * Detecta pitidos de marcador automático en audio raw
     *
     * @param audioData Audio en formato PCM 16-bit mono
     * @return true si se detecta un pitido característico
     */
    fun detectBeepInAudio(audioData: ByteArray): Boolean {
        if (audioData.size < 1000) return false

        try {
            // Convertir bytes a samples
            val samples = ShortArray(audioData.size / 2)
            for (i in samples.indices) {
                val low = audioData[i * 2].toInt() and 0xFF
                val high = audioData[i * 2 + 1].toInt()
                samples[i] = ((high shl 8) or low).toShort()
            }

            // Analizar energía en frecuencias de pitido
            val beepEnergy = analyzeBeepFrequencies(samples)

            if (beepEnergy > BEEP_THRESHOLD) {
                android.util.Log.d(TAG, "🔊 Pitido detectado! Energía: $beepEnergy")
                return true
            }

            // Detectar silencio seguido de tono (patrón típico de marcador)
            if (detectSilenceTonePattern(samples)) {
                android.util.Log.d(TAG, "🔊 Patrón silencio-tono detectado")
                return true
            }

            return false

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error analizando audio: ${e.message}")
            return false
        }
    }

    /**
     * Analiza la energía en frecuencias típicas de pitidos
     */
    private fun analyzeBeepFrequencies(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f

        // Calcular energía total
        var totalEnergy = 0.0
        for (sample in samples) {
            totalEnergy += sample.toDouble() * sample.toDouble()
        }
        totalEnergy = sqrt(totalEnergy / samples.size)

        if (totalEnergy < 100) return 0f // Silencio

        // Goertzel algorithm simplificado para detectar frecuencias específicas
        val targetFreqs = listOf(440f, 480f, 620f, 770f, 852f, 941f, 1000f) // Frecuencias DTMF y pitidos comunes
        var beepEnergy = 0f

        for (freq in targetFreqs) {
            val magnitude = goertzel(samples, freq, SAMPLE_RATE.toFloat())
            val normalizedMag = magnitude / totalEnergy.toFloat()
            if (normalizedMag > 0.3f) {
                beepEnergy += normalizedMag
            }
        }

        return (beepEnergy / targetFreqs.size).coerceIn(0f, 1f)
    }

    /**
     * Algoritmo Goertzel para detectar una frecuencia específica
     */
    private fun goertzel(samples: ShortArray, targetFreq: Float, sampleRate: Float): Float {
        val k = (0.5f + (samples.size * targetFreq / sampleRate)).toInt()
        val w = (2.0 * Math.PI * k / samples.size).toFloat()
        val cosine = kotlin.math.cos(w.toDouble()).toFloat()
        val coeff = 2f * cosine

        var s0 = 0f
        var s1 = 0f
        var s2 = 0f

        for (sample in samples) {
            s0 = coeff * s1 - s2 + sample.toFloat()
            s2 = s1
            s1 = s0
        }

        return sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2)
    }

    /**
     * Detecta patrón de silencio seguido de tono (marcador automático)
     */
    private fun detectSilenceTonePattern(samples: ShortArray): Boolean {
        if (samples.size < 4000) return false // Necesitamos al menos 250ms

        val chunkSize = samples.size / 4
        val energies = mutableListOf<Float>()

        for (i in 0 until 4) {
            val start = i * chunkSize
            val end = start + chunkSize
            var energy = 0f
            for (j in start until end) {
                energy += abs(samples[j].toFloat())
            }
            energies.add(energy / chunkSize)
        }

        // Buscar patrón: bajo -> alto o alto -> bajo -> alto
        val threshold = energies.maxOrNull()?.times(0.3f) ?: return false

        for (i in 0 until energies.size - 1) {
            val current = energies[i]
            val next = energies[i + 1]

            // Silencio seguido de tono
            if (current < threshold && next > threshold * 2) {
                return true
            }
        }

        return false
    }

    /**
     * Detecta si una llamada es de un robot/IVR basándose en el texto
     */
    fun isRobotCall(transcript: String): Boolean {
        if (transcript.isEmpty() || transcript.length < 10) {
            return false
        }

        val lowerTranscript = transcript.lowercase()

        // 1. Detectar patrones IVR
        if (ivrPatterns.any { it.containsMatchIn(lowerTranscript) }) {
            android.util.Log.d(TAG, "🤖 Patrón IVR detectado en texto")
            return true
        }

        // 2. Detectar frases automatizadas
        val automatedMatch = automatedPhrases.count {
            lowerTranscript.contains(it)
        }
        if (automatedMatch >= 1) {
            android.util.Log.d(TAG, "🤖 Mensaje automático detectado ($automatedMatch coincidencias)")
            return true
        }

        // 3. Detectar alta densidad de palabras clave de robot
        val words = lowerTranscript.split("\\s+".toRegex())
        val robotKeywordCount = words.count { it in robotKeywords }
        val robotKeywordRatio = if (words.isNotEmpty()) {
            robotKeywordCount.toFloat() / words.size
        } else 0f

        if (robotKeywordRatio > 0.15f) {
            android.util.Log.d(TAG, "🤖 Alta densidad de palabras robot (${robotKeywordRatio * 100}%)")
            return true
        }

        // 4. Detectar ritmo robótico (repetición excesiva)
        val uniqueWords = words.toSet()
        val repetitionRatio = if (words.size > 20) {
            uniqueWords.size.toFloat() / words.size
        } else 1f

        if (repetitionRatio < 0.5f && words.size > 20) {
            android.util.Log.d(TAG, "🤖 Patrón de repetición robótica (ratio: $repetitionRatio)")
            return true
        }

        return false
    }

    /**
     * Calcula un score de confianza de que es robot (0.0 - 1.0)
     */
    fun getRobotConfidence(transcript: String): Float {
        if (transcript.isEmpty()) return 0f

        val lowerTranscript = transcript.lowercase()
        var score = 0f

        // Patrones IVR (+0.5)
        if (ivrPatterns.any { it.containsMatchIn(lowerTranscript) }) {
            score += 0.5f
        }

        // Frases automatizadas (+0.3 por cada una, máx +0.6)
        val automatedCount = automatedPhrases.count { lowerTranscript.contains(it) }
        score += (automatedCount * 0.3f).coerceAtMost(0.6f)

        // Palabras clave robot (+0.2)
        val words = lowerTranscript.split("\\s+".toRegex())
        val robotKeywordCount = words.count { it in robotKeywords }
        if (robotKeywordCount >= 3) {
            score += 0.2f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Obtiene los patrones detectados en la transcripción
     */
    fun getDetectedPatterns(transcript: String): List<String> {
        val patterns = mutableListOf<String>()
        val lowerTranscript = transcript.lowercase()

        ivrPatterns.forEach { regex ->
            regex.find(lowerTranscript)?.let {
                patterns.add("IVR: ${it.value}")
            }
        }

        automatedPhrases.forEach { phrase ->
            if (lowerTranscript.contains(phrase)) {
                patterns.add("Auto: $phrase")
            }
        }

        return patterns
    }
}