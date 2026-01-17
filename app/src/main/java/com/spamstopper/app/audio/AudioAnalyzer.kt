package com.spamstopper.app.audio

import kotlin.math.*

/**
 * AudioAnalyzer - Detecta pitidos de máquinas automáticas
 *
 * Analiza frecuencias para identificar:
 * - Pitidos sostenidos (800-2000 Hz)
 * - Tonos robóticos
 * - Señales de IVR (Interactive Voice Response)
 */
object AudioAnalyzer {

    private const val SAMPLE_RATE = 8000 // Hz típico de telefonía
    private const val BEEP_FREQ_MIN = 800.0 // Hz
    private const val BEEP_FREQ_MAX = 2000.0 // Hz
    private const val ENERGY_THRESHOLD = 0.7 // 70% de energía concentrada
    private const val MIN_DURATION_MS = 300 // Pitido mínimo 300ms

    /**
     * Detecta si el audio contiene un pitido de robot/máquina
     */
    fun detectBeep(audioChunk: ShortArray): Boolean {
        if (audioChunk.isEmpty()) return false

        try {
            // Convertir a doubles para FFT
            val signal = audioChunk.map { it.toDouble() }.toDoubleArray()

            // Aplicar ventana de Hamming para reducir ruido
            val windowed = applyHammingWindow(signal)

            // Calcular FFT
            val fft = performFFT(windowed)

            // Analizar espectro de frecuencias
            val dominantFrequency = findDominantFrequency(fft)
            val energyRatio = calculateEnergyRatio(fft, dominantFrequency)

            // Detectar si hay un pitido sostenido en rango sospechoso
            val isBeepFrequency = dominantFrequency in BEEP_FREQ_MIN..BEEP_FREQ_MAX
            val isHighEnergy = energyRatio > ENERGY_THRESHOLD

            if (isBeepFrequency && isHighEnergy) {
                android.util.Log.d(
                    "AudioAnalyzer",
                    "🤖 PITIDO DETECTADO: ${dominantFrequency.toInt()} Hz (energía: ${(energyRatio * 100).toInt()}%)"
                )
                return true
            }

            return false

        } catch (e: Exception) {
            android.util.Log.e("AudioAnalyzer", "Error en análisis: ${e.message}")
            return false
        }
    }

    /**
     * Aplica ventana de Hamming para reducir efectos de borde
     */
    private fun applyHammingWindow(signal: DoubleArray): DoubleArray {
        val n = signal.size
        return signal.mapIndexed { i, sample ->
            val windowValue = 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
            sample * windowValue
        }.toDoubleArray()
    }

    /**
     * FFT simplificada usando algoritmo Cooley-Tukey
     */
    private fun performFFT(signal: DoubleArray): Array<Complex> {
        val n = signal.size

        // Asegurar que n es potencia de 2
        val paddedSize = nextPowerOf2(n)
        val padded = signal.copyOf(paddedSize)

        return fft(padded.map { Complex(it, 0.0) }.toTypedArray())
    }

    /**
     * FFT recursiva
     */
    private fun fft(x: Array<Complex>): Array<Complex> {
        val n = x.size

        if (n == 1) return x

        if (n % 2 != 0) {
            throw IllegalArgumentException("n must be power of 2")
        }

        // Dividir en pares e impares
        val even = fft(x.filterIndexed { i, _ -> i % 2 == 0 }.toTypedArray())
        val odd = fft(x.filterIndexed { i, _ -> i % 2 == 1 }.toTypedArray())

        val result = Array(n) { Complex(0.0, 0.0) }

        for (k in 0 until n / 2) {
            val kth = -2.0 * PI * k / n
            val wk = Complex(cos(kth), sin(kth))
            val t = wk * odd[k]

            result[k] = even[k] + t
            result[k + n / 2] = even[k] - t
        }

        return result
    }

    /**
     * Encuentra la frecuencia dominante en el espectro
     */
    private fun findDominantFrequency(fft: Array<Complex>): Double {
        val n = fft.size
        val magnitudes = fft.map { it.magnitude() }

        // Buscar el pico máximo (ignorar DC component en índice 0)
        val maxIndex = magnitudes.drop(1).withIndex().maxByOrNull { it.value }?.index?.plus(1) ?: 1

        // Convertir índice a frecuencia
        return maxIndex * SAMPLE_RATE.toDouble() / n
    }

    /**
     * Calcula qué porcentaje de energía está concentrado en la frecuencia dominante
     */
    private fun calculateEnergyRatio(fft: Array<Complex>, dominantFreq: Double): Double {
        val n = fft.size
        val magnitudes = fft.map { it.magnitude() }
        val totalEnergy = magnitudes.sumOf { it }

        if (totalEnergy == 0.0) return 0.0

        // Calcular energía en banda de ±50Hz alrededor de frecuencia dominante
        val freqIndex = (dominantFreq * n / SAMPLE_RATE).toInt()
        val bandWidth = (50 * n / SAMPLE_RATE).toInt() // ±50 Hz

        val startIndex = max(1, freqIndex - bandWidth)
        val endIndex = min(n / 2, freqIndex + bandWidth)

        val bandEnergy = magnitudes.slice(startIndex..endIndex).sumOf { it }

        return bandEnergy / totalEnergy
    }

    /**
     * Encuentra la siguiente potencia de 2
     */
    private fun nextPowerOf2(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }

    /**
     * Clase auxiliar para números complejos
     */
    private data class Complex(val real: Double, val imag: Double) {
        operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
        operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
        operator fun times(other: Complex) = Complex(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
        )

        fun magnitude() = sqrt(real * real + imag * imag)
    }
}