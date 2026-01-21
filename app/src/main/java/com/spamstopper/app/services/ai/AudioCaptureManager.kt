package com.spamstopper.app.services.ai

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * AudioCaptureManager.kt - Gestor de captura de audio durante llamadas
 * ============================================================================
 *
 * PROPÓSITO:
 * Captura audio durante llamadas activas para análisis de spam.
 *
 * PROBLEMA DE ANDROID:
 * Android bloquea VOICE_DOWNLINK (audio del interlocutor) para apps no-sistema.
 * VOICE_COMMUNICATION solo captura el micrófono local.
 *
 * SOLUCIÓN:
 * Activar ALTAVOZ durante el análisis para que el micrófono capture
 * lo que dice el interlocutor a través del speaker.
 *
 * ACTUALIZADO: Enero 2026
 * ============================================================================
 */
@Singleton
class AudioCaptureManager @Inject constructor() {
    
    private var context: Context? = null

    private var audioRecord: AudioRecord? = null
    private var audioManager: AudioManager? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()
    
    // Estado original del audio para restaurar después
    private var originalSpeakerphoneState = false
    private var originalStreamVolume = 0
    private var audioModeChanged = false

    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000 // Hz - Estándar para Vosk
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        
        // Volumen del altavoz durante análisis (0.0 - 1.0)
        // 60% para asegurar que el micrófono capture bien
        private const val ANALYSIS_VOLUME_PERCENT = 0.6f
    }

    /**
     * Inicializa AudioRecord y configura audio para captura
     *
     * @param ctx Context de la aplicación (opcional, pero necesario para control de altavoz)
     * @return true si se inicializó correctamente
     */
    fun initialize(ctx: Context? = null): Boolean {
        // Guardar contexto si se proporciona
        if (ctx != null) {
            this.context = ctx
        }
        
        return try {
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🎙️ INICIALIZANDO CAPTURA DE AUDIO")
            Log.d(TAG, "═══════════════════════════════════════")
            
            // Obtener AudioManager si tenemos contexto
            val appContext = this.context
            if (appContext != null) {
                audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                
                // Guardar estado original
                saveOriginalAudioState()
                
                // Configurar altavoz para capturar audio del interlocutor
                setupSpeakerForCapture()
            } else {
                Log.w(TAG, "⚠️ Sin contexto - altavoz no disponible")
            }
            
            // Calcular tamaño del buffer
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Buffer size inválido: $minBufferSize")
                return false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
            Log.d(TAG, "📊 Buffer size: $bufferSize bytes")

            // Intentar diferentes fuentes de audio
            audioRecord = tryCreateAudioRecord(bufferSize)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord no se inicializó correctamente")
                audioRecord?.release()
                audioRecord = null
                restoreOriginalAudioState()
                return false
            }

            Log.d(TAG, "✅ AudioRecord inicializado correctamente")
            Log.d(TAG, "   Sample Rate: $SAMPLE_RATE Hz")
            Log.d(TAG, "   Channels: MONO")
            Log.d(TAG, "   Format: PCM 16-bit")
            Log.d(TAG, "═══════════════════════════════════════")
            true

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permiso RECORD_AUDIO denegado", e)
            restoreOriginalAudioState()
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando AudioRecord: ${e.message}", e)
            restoreOriginalAudioState()
            false
        }
    }

    /**
     * Intenta crear AudioRecord con diferentes fuentes
     */
    private fun tryCreateAudioRecord(bufferSize: Int): AudioRecord? {
        // Lista de fuentes a intentar en orden de preferencia
        // MIC primero porque VOICE_RECOGNITION tiene cancelación de ruido
        // que puede filtrar el audio del altavoz
        val audioSources = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT"
        )
        
        // En Android 10+ también intentar VOICE_RECOGNITION al final
        val sourcesToTry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioSources + listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
            )
        } else {
            audioSources
        }

        for ((source, sourceName) in sourcesToTry) {
            try {
                Log.d(TAG, "🔄 Intentando fuente: $sourceName")
                
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "✅ Fuente $sourceName funciona")
                    return record
                } else {
                    Log.w(TAG, "⚠️ Fuente $sourceName no inicializó")
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Fuente $sourceName falló: ${e.message}")
            }
        }

        return null
    }

    /**
     * Guarda el estado original del audio
     */
    private fun saveOriginalAudioState() {
        try {
            audioManager?.let { am ->
                originalSpeakerphoneState = am.isSpeakerphoneOn
                originalStreamVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                Log.d(TAG, "💾 Estado guardado - Altavoz: $originalSpeakerphoneState, Volumen: $originalStreamVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando estado de audio: ${e.message}")
        }
    }

    /**
     * Configura el altavoz para capturar audio del interlocutor
     */
    private fun setupSpeakerForCapture() {
        try {
            audioManager?.let { am ->
                // Activar modo de comunicación
                if (am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioModeChanged = true
                    Log.d(TAG, "🔊 Modo cambiado a IN_COMMUNICATION")
                }

                // Activar altavoz
                if (!am.isSpeakerphoneOn) {
                    am.isSpeakerphoneOn = true
                    Log.d(TAG, "🔊 Altavoz ACTIVADO")
                }

                // Ajustar volumen (bajo para ser discreto)
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val targetVolume = (maxVolume * ANALYSIS_VOLUME_PERCENT).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVolume, 0)
                Log.d(TAG, "🔊 Volumen ajustado a $targetVolume/$maxVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando altavoz: ${e.message}")
        }
    }

    /**
     * Restaura el estado original del audio
     */
    fun restoreOriginalAudioState() {
        try {
            audioManager?.let { am ->
                // Restaurar altavoz
                am.isSpeakerphoneOn = originalSpeakerphoneState
                Log.d(TAG, "🔊 Altavoz restaurado a: $originalSpeakerphoneState")

                // Restaurar volumen
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalStreamVolume, 0)
                Log.d(TAG, "🔊 Volumen restaurado a: $originalStreamVolume")

                // Restaurar modo si lo cambiamos
                if (audioModeChanged) {
                    am.mode = AudioManager.MODE_NORMAL
                    audioModeChanged = false
                    Log.d(TAG, "🔊 Modo restaurado a NORMAL")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando audio: ${e.message}")
        }
    }

    /**
     * Captura audio por duración especificada
     *
     * @param durationSeconds Duración en segundos
     * @param ctx Context opcional (si no se pasó en initialize)
     * @return Audio capturado en formato PCM o null si falla
     */
    suspend fun captureAudio(durationSeconds: Int, ctx: Context? = null): ByteArray? = withContext(Dispatchers.IO) {
        // Guardar contexto si se proporciona
        if (ctx != null) {
            this@AudioCaptureManager.context = ctx
        }
        
        try {
            if (audioRecord == null && !initialize(this@AudioCaptureManager.context)) {
                Log.e(TAG, "❌ No se pudo inicializar AudioRecord")
                return@withContext null
            }

            Log.d(TAG, "🎙️ Capturando $durationSeconds segundos de audio...")

            audioBuffer.reset()
            isRecording = true

            // Pausa más larga para que el altavoz se estabilice completamente
            delay(500)

            audioRecord?.startRecording()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val buffer = ByteArray(minBufferSize)

            val totalSamples = SAMPLE_RATE * durationSeconds
            var samplesRead = 0
            var silentChunks = 0
            var totalChunks = 0

            while (isRecording && samplesRead < totalSamples) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (read > 0) {
                    audioBuffer.write(buffer, 0, read)
                    samplesRead += read / 2 // 2 bytes por sample (16-bit)
                    totalChunks++

                    // Detectar si el chunk es silencio
                    if (isChunkSilent(buffer, read)) {
                        silentChunks++
                    }
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "❌ Error de operación inválida")
                    break
                } else if (read == AudioRecord.ERROR) {
                    Log.e(TAG, "❌ Error genérico de AudioRecord")
                    break
                }
            }

            audioRecord?.stop()
            isRecording = false

            val capturedAudio = audioBuffer.toByteArray()
            val silencePercentage = if (totalChunks > 0) (silentChunks * 100 / totalChunks) else 100
            
            // Calcular nivel de audio promedio para debug
            val avgLevel = calculateAverageLevel(capturedAudio)
            
            Log.d(TAG, "✅ Captura completa:")
            Log.d(TAG, "   Tamaño: ${capturedAudio.size} bytes")
            Log.d(TAG, "   Chunks: $totalChunks (silencio: $silencePercentage%)")
            Log.d(TAG, "   Nivel promedio: $avgLevel")

            // Advertir si hay mucho silencio
            if (silencePercentage > 80) {
                Log.w(TAG, "⚠️ Audio mayormente silencioso - posible problema de captura")
            }

            capturedAudio

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturando audio: ${e.message}", e)
            stopCapture()
            null
        }
    }

    /**
     * Detecta si un chunk de audio es silencio
     */
    private fun isChunkSilent(buffer: ByteArray, length: Int): Boolean {
        if (length < 2) return true

        var sum = 0L
        // Procesar como PCM 16-bit little-endian
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += kotlin.math.abs(sample)
            i += 2
        }

        val average = sum / (length / 2)
        // Umbral de silencio más bajo para detectar audio débil
        return average < 200
    }

    /**
     * Calcula nivel promedio de audio para debug
     */
    private fun calculateAverageLevel(buffer: ByteArray): Long {
        if (buffer.size < 2) return 0

        var sum = 0L
        var i = 0
        while (i < buffer.size - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += kotlin.math.abs(sample)
            i += 2
        }

        return sum / (buffer.size / 2)
    }

    /**
     * Captura en streaming con callback
     */
    suspend fun startStreamingCapture(callback: (ByteArray) -> Unit, ctx: Context? = null) = withContext(Dispatchers.IO) {
        // Guardar contexto si se proporciona
        if (ctx != null) {
            this@AudioCaptureManager.context = ctx
        }
        
        try {
            if (audioRecord == null && !initialize(this@AudioCaptureManager.context)) {
                Log.e(TAG, "❌ No se pudo inicializar para streaming")
                return@withContext
            }

            Log.d(TAG, "🎙️ Iniciando captura en streaming...")

            isRecording = true
            
            // Pausa para estabilizar altavoz
            delay(100)
            
            audioRecord?.startRecording()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val buffer = ByteArray(minBufferSize)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    callback(chunk)
                } else if (read < 0) {
                    Log.e(TAG, "❌ Error en streaming: $read")
                    break
                }
            }

            audioRecord?.stop()
            isRecording = false
            Log.d(TAG, "✅ Streaming detenido")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en streaming: ${e.message}", e)
            stopCapture()
        }
    }

    /**
     * Detiene la captura
     */
    fun stopCapture() {
        try {
            isRecording = false
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            Log.d(TAG, "🛑 Captura detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo captura: ${e.message}")
        }
    }

    /**
     * Libera recursos y restaura audio original
     */
    fun release() {
        try {
            Log.d(TAG, "🧹 Liberando recursos de audio...")
            stopCapture()
            
            audioRecord?.release()
            audioRecord = null
            
            audioBuffer.reset()
            
            // Restaurar configuración de audio original
            restoreOriginalAudioState()
            
            Log.d(TAG, "✅ Recursos liberados")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recursos: ${e.message}")
        }
    }

    /**
     * Verifica si está grabando
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Limpia el buffer de audio
     */
    fun clearBuffer() {
        audioBuffer.reset()
    }

    /**
     * Obtiene información de debug
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("═══ AUDIO CAPTURE DEBUG ═══")
            appendLine("Recording: $isRecording")
            appendLine("AudioRecord: ${if (audioRecord != null) "OK" else "NULL"}")
            appendLine("State: ${audioRecord?.state}")
            appendLine("Speakerphone: ${audioManager?.isSpeakerphoneOn}")
            appendLine("Audio Mode: ${audioManager?.mode}")
            appendLine("═══════════════════════════")
        }
    }
}
