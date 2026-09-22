package com.muhipo.exambrowser.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

/**
 * High-decibel Police Siren Sound Synthesizer using PCM AudioTrack.
 * Generates an unmistakable emergency police siren wail/yelp pitch sweep
 * to immediately notify exam proctors/supervisors.
 */
class PoliceSirenPlayer {

    private var audioTrack: AudioTrack? = null
    private var sirenThread: Thread? = null

    @Volatile
    private var isPlaying = false

    companion object {
        private const val TAG = "PoliceSirenPlayer"
        private const val SAMPLE_RATE = 44100
        private const val MIN_FREQ = 600.0   // 600 Hz (Low police siren pitch)
        private const val MAX_FREQ = 1600.0  // 1600 Hz (High police siren pitch)
    }

    @Synchronized
    fun start() {
        if (isPlaying) return
        isPlaying = true

        sirenThread = Thread {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, 4096)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val buffer = ShortArray(1024)
                var phase = 0.0
                var time = 0.0
                val twoPi = 2.0 * Math.PI

                while (isPlaying) {
                    for (i in buffer.indices) {
                        // Switch between Police Wail (1.8 Hz slow sweep) and Police Yelp (5.0 Hz fast sweep) every 3 seconds
                        val cycleSec = (time % 6.0)
                        val sweepSpeed = if (cycleSec < 3.0) 1.8 else 5.0

                        // Frequency modulation: Smooth sine wave glide between 600 Hz and 1600 Hz
                        val sweep = 0.5 + (0.5 * sin(twoPi * sweepSpeed * time))
                        val currentFreq = MIN_FREQ + ((MAX_FREQ - MIN_FREQ) * sweep)

                        // Synthesize police siren timbre using fundamental tone + 3rd harmonic
                        val fundamental = sin(phase)
                        val harmonic3 = 0.35 * sin(3.0 * phase)
                        val compositeSample = (fundamental + harmonic3) / 1.35

                        // Scale to maximum 16-bit PCM amplitude for maximum audio noticeability
                        val pcmShort = (compositeSample * Short.MAX_VALUE * 0.98)
                            .toInt()
                            .coerceIn(-32768, 32767)
                            .toShort()

                        buffer[i] = pcmShort

                        phase += twoPi * currentFreq / SAMPLE_RATE
                        if (phase > twoPi) {
                            phase -= twoPi
                        }
                        time += 1.0 / SAMPLE_RATE
                    }

                    val track = audioTrack ?: break
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.write(buffer, 0, buffer.size)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing police siren sound: ${e.message}", e)
            } finally {
                cleanupAudioTrack()
            }
        }
        sirenThread?.start()
    }

    @Synchronized
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        try {
            sirenThread?.interrupt()
            sirenThread = null
            cleanupAudioTrack()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping police siren: ${e.message}", e)
        }
    }

    private fun cleanupAudioTrack() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}", e)
        } finally {
            audioTrack = null
        }
    }

    fun isPlaying(): Boolean = isPlaying
}
