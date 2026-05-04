package com.genspark.videotransform.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TempFileManager(context: Context) {
    val root: File = File(context.cacheDir, "video_transform_tmp").apply { mkdirs() }
    fun file(name: String) = File(root, name)
    fun cleanupLeftovers() {
        root.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }
}

object UriCopyHelper {
    suspend fun copyToCache(context: Context, uri: Uri, fileNameHint: String): File =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri)
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: fileNameHint.substringAfterLast('.', "bin")
            val dir = File(context.cacheDir, "video_transform_tmp").apply { mkdirs() }
            val dest = File(
                dir,
                "${System.currentTimeMillis()}_${fileNameHint.substringBeforeLast('.')}.$ext"
            )
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open source URI" }
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest
        }
}
