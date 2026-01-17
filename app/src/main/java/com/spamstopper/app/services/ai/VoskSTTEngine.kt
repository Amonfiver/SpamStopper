package com.spamstopper.app.services.ai

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de Speech-to-Text usando Vosk
 *
 * Transcribe audio a texto de forma offline.
 * Usa modelo pequeño en español optimizado para llamadas.
 */
@Singleton
class VoskSTTEngine @Inject constructor(
    private val context: Context
) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "VoskSTT"
        private const val SAMPLE_RATE = 16000f
        private const val MODEL_NAME = "vosk-model-small-es-0.42"
    }

    /**
     * Inicializa el modelo Vosk
     * Debe llamarse antes de transcribir
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "✅ Ya inicializado")
            return true
        }

        return try {
            Log.d(TAG, "📦 Desempaquetando modelo Vosk...")

            // Desempaquetar modelo desde assets
            StorageService.unpack(
                context,
                MODEL_NAME,
                "model",
                { modelFile ->
                    this.model = Model(modelFile.toString())
                    this.recognizer = Recognizer(this.model, SAMPLE_RATE)
                    isInitialized = true
                    Log.d(TAG, "✅ Vosk inicializado correctamente")
                },
                { exception ->
                    Log.e(TAG, "❌ Error desempaquetando Vosk: ${exception.message}")
                }
            )

            isInitialized
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error inicializando Vosk: ${e.message}", e)
            false
        }
    }

    /**
     * Transcribe audio PCM a texto
     *
     * @param audioData Audio en formato PCM 16-bit
     * @return Texto transcrito o null si falla
     */
    fun transcribe(audioData: ByteArray): String? {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "❌ STT no inicializado")
            return null
        }

        return try {
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

            Log.d(TAG, "📝 Transcripción: $text")
            text

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error transcribiendo: ${e.message}", e)
            null
        }
    }

    /**
     * Transcribe audio en tiempo real (stream)
     *
     * @param audioChunk Chunk de audio
     * @return Transcripción parcial
     */
    fun transcribeStream(audioChunk: ByteArray): String? {
        if (!isInitialized || recognizer == null) {
            return null
        }

        return try {
            recognizer?.acceptWaveForm(audioChunk, audioChunk.size)
            val partial = recognizer?.partialResult
            parseVoskResult(partial)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en stream: ${e.message}")
            null
        }
    }

    /**
     * Obtiene resultado final de la transcripción
     */
    fun getFinalResult(): String? {
        return try {
            val result = recognizer?.finalResult
            parseVoskResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo resultado final: ${e.message}")
            null
        }
    }

    /**
     * Parsea JSON de respuesta de Vosk
     *
     * Vosk devuelve: {"text": "hola mundo"}
     * o: {"partial": "hola"}
     */
    private fun parseVoskResult(json: String?): String? {
        if (json.isNullOrBlank()) return null

        return try {
            // Parseo simple sin librería JSON
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(json)
            val partialMatch = Regex("\"partial\"\\s*:\\s*\"([^\"]+)\"").find(json)

            textMatch?.groupValues?.get(1) ?: partialMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando JSON: ${e.message}")
            null
        }
    }

    /**
     * Resetea el recognizer para nueva transcripción
     */
    fun reset() {
        recognizer?.reset()
    }

    /**
     * Libera recursos
     */
    fun release() {
        try {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            isInitialized = false
            Log.d(TAG, "🧹 Recursos liberados")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recursos: ${e.message}")
        }
    }
}