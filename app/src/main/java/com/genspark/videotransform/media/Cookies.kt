package com.genspark.videotransform.media

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

object CookieExporter {
    fun exportYoutubeCookies(context: Context): File {
        val cookieManager = CookieManager.getInstance()
        val targets = listOf(
            "https://accounts.google.com",
            "https://www.youtube.com",
            "https://youtube.com",
            "https://m.youtube.com",
        )
        val outDir = File(context.filesDir, "yt-dlp").apply { mkdirs() }
        val outFile = File(outDir, "cookies.txt")
        val expiry = (System.currentTimeMillis() / 1000L) + (365L * 24L * 60L * 60L)
        val lines = mutableListOf("# Netscape HTTP Cookie File", "# Exported by Video Transform")
        targets.forEach { url ->
            val host = Uri.parse(url).host ?: return@forEach
            val raw = cookieManager.getCookie(url) ?: return@forEach
            raw.split(";")
                .map { it.trim() }
                .filter { it.contains("=") }
                .forEach { pair ->
                    val idx = pair.indexOf('=')
                    val name = pair.substring(0, idx)
                    val value = pair.substring(idx + 1)
                    lines += listOf(
                        host,
                        "TRUE",
                        "/",
                        if (url.startsWith("https")) "TRUE" else "FALSE",
                        expiry.toString(),
                        name,
                        value,
                    ).joinToString("\t")
                }
        }
        outFile.writeText(lines.joinToString("\n"))
        return outFile
    }
}
