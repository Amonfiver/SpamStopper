package com.spamstopper.app.services.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ============================================================================
 * VoskSTTEngine.kt - Motor de Speech-to-Text usando Vosk
 * ============================================================================
 *
 * PROPÓSITO:
 * Transcribe audio a texto de forma OFFLINE usando el modelo Vosk.
 * Optimizado para detección de spam en llamadas telefónicas.
 *
 * REQUISITOS:
 * - Modelo Vosk en assets/model/
 * - Permisos de RECORD_AUDIO
 *
 * ACTUALIZADO: Enero 2026 - Corregida inicialización asíncrona
 * ============================================================================
 */
@Singleton
class VoskSTTEngine @Inject constructor(
    private val context: Context
) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var isInitializing = false

    companion object {
        private const val TAG = "VoskSTT"
        private const val SAMPLE_RATE = 16000f
        
        // IMPORTANTE: Debe coincidir con la carpeta en assets/
        private const val MODEL_NAME = "model"
        
        // Timeout para inicialización (el modelo puede tardar en desempaquetar)
        private const val INIT_TIMEOUT_MS = 30000L
    }

    /**
     * Inicializa el modelo Vosk de forma SÍNCRONA.
     * Espera a que el modelo esté completamente cargado antes de retornar.
     *
     * @return true si se inicializó correctamente, false en caso contrario
     */
    suspend fun initialize(): Boolean {
        // Ya inicializado
        if (isInitialized && model != null && recognizer != null) {
            Log.d(TAG, "✅ Vosk ya está inicializado")
            return true
        }

        // Evitar inicializaciones concurrentes
        if (isInitializing) {
            Log.d(TAG, "⏳ Inicialización en progreso, esperando...")
            // Esperar a que termine la inicialización actual
            var waitCount = 0
            while (isInitializing && waitCount < 60) {
                kotlinx.coroutines.delay(500)
                waitCount++
            }
            return isInitialized
        }

        isInitializing = true

        return try {
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "📦 INICIANDO VOSK STT ENGINE")
            Log.d(TAG, "═══════════════════════════════════════")

            // Verificar que existe la carpeta del modelo en assets
            val assetExists = checkModelInAssets()
            if (!assetExists) {
                Log.e(TAG, "❌ Modelo no encontrado en assets/$MODEL_NAME")
                Log.e(TAG, "   Descarga el modelo de: https://alphacephei.com/vosk/models")
                Log.e(TAG, "   Y colócalo en: app/src/main/assets/model/")
                isInitializing = false
                return false
            }

            Log.d(TAG, "✅ Modelo encontrado en assets")

            // Usar timeout para evitar bloqueos infinitos
            val result = withTimeoutOrNull(INIT_TIMEOUT_MS) {
                initializeWithCoroutine()
            }

            if (result == true) {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "✅ VOSK INICIALIZADO CORRECTAMENTE")
                Log.d(TAG, "   Modelo: $MODEL_NAME")
                Log.d(TAG, "   Sample Rate: $SAMPLE_RATE Hz")
                Log.d(TAG, "═══════════════════════════════════════")
                isInitialized = true
                true
            } else {
                Log.e(TAG, "❌ Timeout o error inicializando Vosk")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción inicializando Vosk: ${e.message}", e)
            false
        } finally {
            isInitializing = false
        }
    }

    /**
     * Inicialización usando coroutines para esperar el callback asíncrono
     */
    private suspend fun initializeWithCoroutine(): Boolean = 
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "📦 Desempaquetando modelo desde assets...")

                StorageService.unpack(
                    context,
                    MODEL_NAME,
                    MODEL_NAME,
                    { modelObj ->
                        // SUCCESS CALLBACK - modelObj es el Model ya creado por Vosk
                        try {
                            Log.d(TAG, "✅ Modelo Vosk recibido del callback")
                            
                            // La nueva API de Vosk devuelve el Model directamente
                            if (modelObj is Model) {
                                Log.d(TAG, "🔧 Modelo recibido como Model object")
                                this.model = modelObj
                            } else {
                                // Fallback: si es un path, crear el modelo
                                Log.d(TAG, "🔧 Creando modelo desde path: $modelObj")
                                this.model = Model(modelObj.toString())
                            }
                            
                            // Crear el recognizer
                            Log.d(TAG, "🔧 Creando recognizer con sample rate: $SAMPLE_RATE")
                            this.recognizer = Recognizer(this.model, SAMPLE_RATE)

                            Log.d(TAG, "✅ Modelo y recognizer creados correctamente")
                            
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error creando modelo/recognizer: ${e.message}", e)
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    },
                    { exception ->
                        // ERROR CALLBACK
                        Log.e(TAG, "❌ Error desempaquetando modelo: ${exception.message}")
                        exception.printStackTrace()
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción en unpack: ${e.message}", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

    /**
     * Verifica si el modelo existe en assets
     */
    private fun checkModelInAssets(): Boolean {
        return try {
            val assets = context.assets.list(MODEL_NAME)
            val exists = assets != null && assets.isNotEmpty()
            
            if (exists) {
                Log.d(TAG, "📁 Contenido de assets/$MODEL_NAME:")
                assets?.forEach { Log.d(TAG, "   - $it") }
            }
            
            exists
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error verificando assets: ${e.message}")
            false
        }
    }

    /**
     * Verifica si el motor está listo para transcribir
     */
    fun isReady(): Boolean {
        return isInitialized && model != null && recognizer != null
    }

    /**
     * Transcribe audio PCM a texto
     *
     * @param audioData Audio en formato PCM 16-bit, 16kHz, mono
     * @return Texto transcrito o null si falla
     */
    fun transcribe(audioData: ByteArray): String? {
        if (!isReady()) {
            Log.e(TAG, "❌ STT no inicializado - no se puede transcribir")
            return null
        }

        if (audioData.isEmpty()) {
            Log.w(TAG, "⚠️ Audio vacío recibido")
            return null
        }

        return try {
            Log.d(TAG, "🎤 Transcribiendo ${audioData.size} bytes de audio...")
            
            // Resetear recognizer para nueva transcripción
            recognizer?.reset()

            // Procesar audio
            val accepted = recognizer?.acceptWaveForm(audioData, audioData.size)

            val result = if (accepted == true) {
                recognizer?.result
            } else {
                recognizer?.partialResult
            }

            // Parsear JSON de Vosk
            val text = parseVoskResult(result)

            if (!text.isNullOrBlank()) {
                Log.d(TAG, "📝 Transcripción: \"$text\"")
            } else {
                Log.d(TAG, "📝 Sin texto detectado")
            }
            
            text

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error transcribiendo: ${e.message}", e)
            null
        }
    }

    /**
     * Transcribe audio en tiempo real (streaming)
     * Útil para procesar chunks de audio mientras se graba
     *
     * @param audioChunk Chunk de audio PCM
     * @return Transcripción parcial o null
     */
    fun transcribeStream(audioChunk: ByteArray): String? {
        if (!isReady()) {
            return null
        }

        if (audioChunk.isEmpty()) {
            return null
        }

        return try {
            val accepted = recognizer?.acceptWaveForm(audioChunk, audioChunk.size)
            
            val result = if (accepted == true) {
                // Resultado completo disponible
                val finalText = recognizer?.result
                Log.d(TAG, "📝 Resultado final: $finalText")
                finalText
            } else {
                // Solo resultado parcial
                recognizer?.partialResult
            }
            
            parseVoskResult(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en transcripción stream: ${e.message}")
            null
        }
    }

    /**
     * Obtiene el resultado final acumulado de la transcripción
     */
    fun getFinalResult(): String? {
        if (!isReady()) {
            return null
        }

        return try {
            val result = recognizer?.finalResult
            val text = parseVoskResult(result)
            Log.d(TAG, "📝 Resultado final completo: \"$text\"")
            text
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo resultado final: ${e.message}")
            null
        }
    }

    /**
     * Parsea el JSON de respuesta de Vosk
     *
     * Vosk devuelve JSON como:
     * - Resultado completo: {"text": "hola mundo"}
     * - Resultado parcial: {"partial": "hola"}
     */
    private fun parseVoskResult(json: String?): String? {
        if (json.isNullOrBlank()) return null

        return try {
            // Parseo con regex (evita dependencia de librería JSON)
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(json)
            val partialMatch = Regex("\"partial\"\\s*:\\s*\"([^\"]*)\"").find(json)

            val result = textMatch?.groupValues?.get(1) 
                ?: partialMatch?.groupValues?.get(1)
            
            // Limpiar resultado (puede tener espacios extra)
            result?.trim()?.takeIf { it.isNotEmpty() }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando JSON Vosk: ${e.message}")
            null
        }
    }

    /**
     * Resetea el recognizer para comenzar nueva transcripción
     */
    fun reset() {
        try {
            recognizer?.reset()
            Log.d(TAG, "🔄 Recognizer reseteado")
        } catch (e: Exception) {
            Log.e(TAG, "Error reseteando recognizer: ${e.message}")
        }
    }

    /**
     * Libera todos los recursos
     * Llamar cuando ya no se necesite el motor STT
     */
    fun release() {
        try {
            Log.d(TAG, "🧹 Liberando recursos de Vosk...")
            
            recognizer?.close()
            recognizer = null
            
            model?.close()
            model = null
            
            isInitialized = false
            
            Log.d(TAG, "✅ Recursos liberados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error liberando recursos: ${e.message}")
        }
    }

    /**
     * Información de debug sobre el estado del motor
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("═══ VOSK STT DEBUG ═══")
            appendLine("Initialized: $isInitialized")
            appendLine("Initializing: $isInitializing")
            appendLine("Model: ${if (model != null) "OK" else "NULL"}")
            appendLine("Recognizer: ${if (recognizer != null) "OK" else "NULL"}")
            appendLine("Ready: ${isReady()}")
            appendLine("Model Name: $MODEL_NAME")
            appendLine("Sample Rate: $SAMPLE_RATE Hz")
            appendLine("══════════════════════")
        }
    }
}
