package expo.modules.dualcammodule.core.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer


class AudioEngine constructor() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    // We need to notify multiple multiplexers (or audio encoders)
    // Here we'll take a callback that distributes the raw PCM data to the encoders
    private var onAudioDataCallback: ((ByteBuffer, Int, Long) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(
        config: AudioCodecConfig,
        callback: (ByteBuffer, Int, Long) -> Unit
    ) {
        if (isRecording) return
        this.onAudioDataCallback = callback

        val channelConfig = if (config.channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            // Handle initialization error (could throw an exception in a real app)
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isRecording = true
    }

    /**
     * Reads audio data continuously. Should be called inside a coroutine running on Dispatchers.IO
     */
    suspend fun readAudioLoop() = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ByteBuffer.allocateDirect(minBufferSize)

        while (isActive && isRecording) {
            buffer.clear()
            val readBytes = audioRecord?.read(buffer, minBufferSize) ?: 0
            if (readBytes > 0) {
                buffer.limit(readBytes)
                // Use nanoTime for base presentation time, adjust as needed in encoder
                val presentationTimeNs = System.nanoTime()
                onAudioDataCallback?.invoke(buffer, readBytes, presentationTimeNs)
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        onAudioDataCallback = null
    }
}
