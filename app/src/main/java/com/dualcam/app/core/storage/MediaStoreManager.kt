package com.dualcam.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileDescriptor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Creates an entry in MediaStore for a new video and returns its FileDescriptor.
     * This is needed to pass to MediaMuxer using Scoped Storage on Android 10+.
     *
     * @param fileName The name of the video file (e.g., "video_16x9.mp4").
     * @return Pair containing the Uri and the writable FileDescriptor.
     */
    fun createVideoFileDescriptor(fileName: String): Pair<Uri, FileDescriptor>? {
        val resolver = context.contentResolver

        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Keep the file pending while we record
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(videoCollection, contentValues) ?: return null

        // Open the file descriptor in "rw" (read-write) mode
        val pfd = resolver.openFileDescriptor(uri, "rw") ?: return null
        return Pair(uri, pfd.fileDescriptor)
    }

    /**
     * Marks the video as no longer pending so it becomes visible to other apps.
     */
    fun markVideoReady(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        }
    }

    /**
     * Deletes the video entry if recording fails.
     */
    fun deleteVideo(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }
}
