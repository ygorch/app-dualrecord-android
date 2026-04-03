package expo.modules.dualcammodule.core.media

import android.media.MediaFormat

data class VideoCodecConfig(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitRate: Int = calculateBitRate(width, height, frameRate),
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC // H.264 is the robust fallback/baseline
) {
    companion object {
        private fun calculateBitRate(width: Int, height: Int, fps: Int): Int {
            // Rough estimate: BPP (Bits Per Pixel) = 0.1 for decent quality H.264
            val bpp = 0.1
            return (width * height * fps * bpp).toInt()
        }
    }
}

data class AudioCodecConfig(
    val sampleRate: Int = 48000, // 48kHz standard for video sync
    val channelCount: Int = 1,   // Mono usually fine for generic recording
    val bitRate: Int = 128000,   // 128kbps AAC-LC
    val mimeType: String = MediaFormat.MIMETYPE_AUDIO_AAC
)
