package com.genspark.videotransform.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "video_transform_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): SecureSettings = SecureSettings(
        youtubeClientId = prefs.getString("youtube_client_id", "") ?: "",
        youtubeClientSecret = prefs.getString("youtube_client_secret", "") ?: "",
        facebookPageId = prefs.getString("facebook_page_id", "") ?: "",
        facebookPageAccessToken = prefs.getString("facebook_page_access_token", "") ?: "",
        youtubeAccessToken = prefs.getString("youtube_access_token", "") ?: "",
        youtubeRefreshToken = prefs.getString("youtube_refresh_token", "") ?: "",
        youtubeTokenExpiry = prefs.getLong("youtube_token_expiry", 0L),
    )

    fun save(settings: SecureSettings) {
        prefs.edit()
            .putString("youtube_client_id", settings.youtubeClientId)
            .putString("youtube_client_secret", settings.youtubeClientSecret)
            .putString("facebook_page_id", settings.facebookPageId)
            .putString("facebook_page_access_token", settings.facebookPageAccessToken)
            .putString("youtube_access_token", settings.youtubeAccessToken)
            .putString("youtube_refresh_token", settings.youtubeRefreshToken)
            .putLong("youtube_token_expiry", settings.youtubeTokenExpiry)
            .apply()
    }

    fun updateYouTubeTokens(accessToken: String, refreshToken: String?, expiry: Long) {
        prefs.edit()
            .putString("youtube_access_token", accessToken)
            .putString("youtube_refresh_token", refreshToken ?: prefs.getString("youtube_refresh_token", ""))
            .putLong("youtube_token_expiry", expiry)
            .apply()
    }
}
