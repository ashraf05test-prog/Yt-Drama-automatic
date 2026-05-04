package com.genspark.videotransform.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.genspark.videotransform.data.PublishPayload
import com.genspark.videotransform.data.SecureSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GalleryExporter(private val context: Context) {
    suspend fun export(file: File, displayName: String = file.name): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoTransform")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "MediaStore openOutputStream returned null" }
            file.inputStream().use { it.copyTo(output) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val update = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, update, null, null)
        }
        uri
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.MINUTES)
    .writeTimeout(15, TimeUnit.MINUTES)
    .build()

class YouTubePublisher {
    suspend fun upload(
        settings: SecureSettings,
        payload: PublishPayload,
        progress: (Int, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(settings.youtubeAccessToken.isNotBlank()) {
            "Connect YouTube (Settings -> Connect YouTube OAuth2) first."
        }
        val initiateBody = """{
          "snippet":{"title":${json(payload.title)},"description":${json(payload.description)},"categoryId":"22"},
          "status":{"privacyStatus":${json(payload.privacyStatus)}}
        }""".trimIndent()
        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer ${settings.youtubeAccessToken}")
            .header("X-Upload-Content-Type", "video/mp4")
            .post(initiateBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val sessionUrl = httpClient.newCall(initReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("YouTube init failed: HTTP ${resp.code} ${resp.body?.string().orEmpty()}")
            resp.header("Location") ?: throw IOException("Missing resumable upload URL")
        }
        progress(30, "Uploading to YouTube...")
        val file = File(payload.filePath)
        val uploadReq = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer ${settings.youtubeAccessToken}")
            .header("Content-Length", file.length().toString())
            .header("Content-Type", "video/mp4")
            .put(file.asRequestBody("video/mp4".toMediaType()))
            .build()
        val body = httpClient.newCall(uploadReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("YouTube upload failed: HTTP ${resp.code} ${resp.body?.string().orEmpty()}")
            resp.body?.string().orEmpty()
        }
        progress(100, "YouTube upload complete")
        val id = Gson().fromJson(body, JsonObject::class.java)?.get("id")?.asString.orEmpty()
        "https://www.youtube.com/watch?v=$id"
    }
}

class FacebookPublisher {
    suspend fun upload(
        settings: SecureSettings,
        payload: PublishPayload,
        progress: (Int, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(settings.facebookPageId.isNotBlank()) { "Facebook Page ID missing in Settings." }
        require(settings.facebookPageAccessToken.isNotBlank()) { "Facebook Page Access Token missing in Settings." }
        val baseHttp = "https://graph.facebook.com/v19.0/${settings.facebookPageId}/video_reels".toHttpUrl()

        val startUrl = baseHttp.newBuilder()
            .addQueryParameter("upload_phase", "start")
            .addQueryParameter("access_token", settings.facebookPageAccessToken)
            .build()
        val startReq = Request.Builder().url(startUrl).post(ByteArray(0).toRequestBody(null)).build()
        val startBody = httpClient.newCall(startReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Facebook start failed: HTTP ${resp.code} ${resp.body?.string().orEmpty()}")
            resp.body?.string().orEmpty()
        }
        val startJson = Gson().fromJson(startBody, JsonObject::class.java)
        val uploadUrl = startJson.get("upload_url")?.asString ?: throw IOException("No upload_url returned")
        val videoId = startJson.get("video_id")?.asString ?: throw IOException("No video_id returned")

        progress(40, "Uploading Facebook Reel...")
        val videoFile = File(payload.filePath)
        val uploadReq = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "OAuth ${settings.facebookPageAccessToken}")
            .header("offset", "0")
            .header("file_size", videoFile.length().toString())
            .post(videoFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        httpClient.newCall(uploadReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Facebook binary upload failed: HTTP ${resp.code} ${resp.body?.string().orEmpty()}")
        }

        val finishUrl = baseHttp.newBuilder()
            .addQueryParameter("upload_phase", "finish")
            .addQueryParameter("video_id", videoId)
            .addQueryParameter("description", payload.description)
            .addQueryParameter("title", payload.title)
            .addQueryParameter("video_state", "PUBLISHED")
            .addQueryParameter("access_token", settings.facebookPageAccessToken)
            .build()
        val finishReq = Request.Builder().url(finishUrl).post(ByteArray(0).toRequestBody(null)).build()
        httpClient.newCall(finishReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Facebook finish failed: HTTP ${resp.code} ${resp.body?.string().orEmpty()}")
        }
        progress(100, "Facebook upload complete")
        "https://www.facebook.com/reel/$videoId"
    }
}

private fun json(value: String): String = Gson().toJson(value)
