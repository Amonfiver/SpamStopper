package com.spamstopper.app.services.ai

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor de captura de audio durante llamadas
 *
 * Captura audio del micrófono durante la llamada activa
 * y lo prepara para análisis STT.
 *
 * IMPORTANTE: Solo captura audio EN MEMORIA, NO lo guarda a disco.
 */
@Singleton
class AudioCaptureManager @Inject constructor() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()

    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000 // Hz - Estándar para speech
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    /**
     * Inicializa AudioRecord
     *
     * @return true si se inicializó correctamente
     */
    fun initialize(): Boolean {
        return try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Buffer size inválido")
                return false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Fuente optimizada para llamadas
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord no se inicializó")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            Log.d(TAG, "✅ AudioRecord inicializado (buffer: $bufferSize bytes)")
            true

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permiso RECORD_AUDIO denegado", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando AudioRecord", e)
            false
        }
    }

    /**
     * Inicia captura de audio
     *
     * @param durationSeconds Duración en segundos
     * @return Audio capturado en formato PCM o null si falla
     */
    suspend fun captureAudio(durationSeconds: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (audioRecord == null && !initialize()) {
                Log.e(TAG, "❌ No se pudo inicializar AudioRecord")
                return@withContext null
            }

            Log.d(TAG, "🎙️ Iniciando captura de $durationSeconds segundos...")

            audioBuffer.reset()
            isRecording = true

            audioRecord?.startRecording()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val buffer = ByteArray(minBufferSize)

            val totalSamples = SAMPLE_RATE * durationSeconds
            var samplesRead = 0

            while (isRecording && samplesRead < totalSamples) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (read > 0) {
                    audioBuffer.write(buffer, 0, read)
                    samplesRead += read / 2 // 2 bytes por sample (16-bit)
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "❌ Error de operación inválida")
                    break
                }
            }

            audioRecord?.stop()
            isRecording = false

            val capturedAudio = audioBuffer.toByteArray()
            Log.d(TAG, "✅ Captura completa: ${capturedAudio.size} bytes")

            capturedAudio

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturando audio", e)
            stopCapture()
            null
        }
    }

    /**
     * Inicia captura en streaming (para procesamiento en tiempo real)
     *
     * @param callback Llamado con cada chunk de audio
     */
    suspend fun startStreamingCapture(callback: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        try {
            if (audioRecord == null && !initialize()) {
                Log.e(TAG, "❌ No se pudo inicializar para streaming")
                return@withContext
            }

            Log.d(TAG, "🎙️ Iniciando captura en streaming...")

            isRecording = true
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
                    // Copiar buffer para evitar sobrescritura
                    val chunk = buffer.copyOf(read)
                    callback(chunk)
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "❌ Error en streaming")
                    break
                }
            }

            audioRecord?.stop()
            isRecording = false
            Log.d(TAG, "✅ Streaming detenido")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en streaming", e)
            stopCapture()
        }
    }

    /**
     * Detiene la captura
     */
    fun stopCapture() {
        try {
            isRecording = false
            audioRecord?.stop()
            Log.d(TAG, "🛑 Captura detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo captura", e)
        }
    }

    /**
     * Libera recursos
     */
    fun release() {
        try {
            stopCapture()
            audioRecord?.release()
            audioRecord = null
            audioBuffer.reset()
            Log.d(TAG, "🧹 Recursos liberados")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recursos", e)
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
}