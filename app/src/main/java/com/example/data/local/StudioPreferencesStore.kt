package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "xaviour_studio_prefs")

class StudioPreferencesStore(private val context: Context) {

    private val customLogoUriKey = stringPreferencesKey("custom_logo_uri")
    private val logoPositionKey = stringPreferencesKey("logo_position")
    private val logoSizeKey = stringPreferencesKey("logo_size")
    private val bgMusicUriKey = stringPreferencesKey("bg_music_uri")
    private val bgMusicNameKey = stringPreferencesKey("bg_music_name")

    val customLogoUri: Flow<String?> = context.studioDataStore.data.map { prefs ->
        prefs[customLogoUriKey]
    }

    val logoPosition: Flow<String> = context.studioDataStore.data.map { prefs ->
        prefs[logoPositionKey] ?: "top_center"
    }

    val logoSize: Flow<String> = context.studioDataStore.data.map { prefs ->
        prefs[logoSizeKey] ?: "medium"
    }

    val backgroundMusicUri: Flow<String?> = context.studioDataStore.data.map { prefs ->
        prefs[bgMusicUriKey]
    }

    val backgroundMusicName: Flow<String?> = context.studioDataStore.data.map { prefs ->
        prefs[bgMusicNameKey]
    }

    suspend fun setCustomLogoUri(uriString: String?) {
        context.studioDataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) prefs.remove(customLogoUriKey) else prefs[customLogoUriKey] = uriString
        }
    }

    suspend fun setLogoPlacement(position: String, size: String) {
        context.studioDataStore.edit { prefs ->
            prefs[logoPositionKey] = position
            prefs[logoSizeKey] = size
        }
    }

    suspend fun setBackgroundMusic(uriString: String?, displayName: String?) {
        context.studioDataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(bgMusicUriKey)
                prefs.remove(bgMusicNameKey)
            } else {
                prefs[bgMusicUriKey] = uriString
                prefs[bgMusicNameKey] = displayName ?: "Selected Music Track"
            }
        }
    }
}
