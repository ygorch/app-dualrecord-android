package com.dualcam.app.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Handles encoding Video (from Surface) and Audio (from raw PCM) and muxing into a single MP4 file.
 * We will instantiate TWO of these (one for 16:9, one for 9:16).
 */
class MultiplexerEngine(
    private val videoConfig: VideoCodecConfig,
    private val audioConfig: AudioCodecConfig,
    private val outputFd: FileDescriptor,
    private val isPortrait: Boolean // Tells if the video should be marked as portrait (9:16)
) {
    private var muxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    private val bufferInfo = MediaCodec.BufferInfo()
    private var isEncoding = false

    // The surface provided to Camera2 API for the video encoder to read frames
    var inputSurface: Surface? = null
        private set

    fun prepare() {
        // 1. Setup Video Encoder
        val videoFormat = MediaFormat.createVideoFormat(videoConfig.mimeType, videoConfig.width, videoConfig.height)
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoConfig.bitRate)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoConfig.frameRate)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        videoEncoder = MediaCodec.createEncoderByType(videoConfig.mimeType)
        videoEncoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEncoder?.createInputSurface()

        // 2. Setup Audio Encoder
        val audioFormat = MediaFormat.createAudioFormat(audioConfig.mimeType, audioConfig.sampleRate, audioConfig.channelCount)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioConfig.bitRate)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

        audioEncoder = MediaCodec.createEncoderByType(audioConfig.mimeType)
        audioEncoder?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // 3. Setup Muxer
        muxer = MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (isPortrait) {
            // Set orientation hint for 9:16. Typically 90 or 270 depending on sensor. We'll assume 90 for simplicity.
            muxer?.setOrientationHint(90)
        }
    }

    fun start() {
        videoEncoder?.start()
        audioEncoder?.start()
        isEncoding = true
    }

    /**
     * Feeds raw PCM audio data into the audio encoder.
     */
    fun encodeAudio(pcmBuffer: ByteBuffer, size: Int, presentationTimeNs: Long) {
        if (!isEncoding) return
        val encoder = audioEncoder ?: return

        val inputBufferIndex = encoder.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmBuffer)

            // Convert nanoseconds to microseconds
            val ptsUs = presentationTimeNs / 1000
            encoder.queueInputBuffer(inputBufferIndex, 0, size, ptsUs, 0)
        }
    }

    /**
     * Drains encoded data from both video and audio encoders and feeds to muxer.
     * Call this in a loop on a background thread.
     */
    suspend fun drainEncoders() = withContext(Dispatchers.IO) {
        while (isEncoding) {
            drainEncoder(videoEncoder, true)
            drainEncoder(audioEncoder, false)
        }
    }

    private fun drainEncoder(encoder: MediaCodec?, isVideo: Boolean) {
        if (encoder == null) return

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                if (isVideo) {
                    videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                } else {
                    audioTrackIndex = muxer?.addTrack(newFormat) ?: -1
                }
                if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !isMuxerStarted) {
                    muxer?.start()
                    isMuxerStarted = true
                }
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                if (encodedData != null && isMuxerStarted) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        val trackIndex = if (isVideo) videoTrackIndex else audioTrackIndex
                        muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }

    fun stop() {
        isEncoding = false

        try {
            videoEncoder?.signalEndOfInputStream()
        } catch (e: Exception) { Log.e("Muxer", "Error signaling EOS", e) }

        // Let drain finish (in a real app we wait for EOS flag, but keeping MVP simple)
        Thread.sleep(100)

        videoEncoder?.stop()
        videoEncoder?.release()
        audioEncoder?.stop()
        audioEncoder?.release()

        if (isMuxerStarted) {
            muxer?.stop()
            muxer?.release()
            isMuxerStarted = false
        }

        videoEncoder = null
        audioEncoder = null
        muxer = null
    }
}
