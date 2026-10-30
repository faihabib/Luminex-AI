package com.luminex.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val IS_MENU_LEFT = booleanPreferencesKey("is_menu_left")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_update_version")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")

        val THEME = stringPreferencesKey("theme")
        val DEFAULT_AI_INDEX = intPreferencesKey("default_ai_index")
        val AUTO_LOAD_LAST = booleanPreferencesKey("auto_load_last")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val SPLIT_SCREEN = booleanPreferencesKey("split_screen")
    val SECONDARY_SITE_INDEX = intPreferencesKey("secondary_site_index")
        val CUSTOM_UA = stringPreferencesKey("custom_ua")
        val FONT_SIZE = intPreferencesKey("font_size")
        val PREVENT_SCREENSHOTS = booleanPreferencesKey("prevent_screenshots")
    }

    val isMenuLeftFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.IS_MENU_LEFT] ?: false }

    val skippedVersionFlow: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.SKIPPED_VERSION] }

    val lastUpdateCheckTimeFlow: Flow<Long> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L }

    // === New setting flows ===
    val themeFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.THEME] ?: "system" }

    val defaultAiIndexFlow: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.DEFAULT_AI_INDEX] ?: 0 }

    val autoLoadLastFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.AUTO_LOAD_LAST] ?: true }

    val desktopModeFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.DESKTOP_MODE] ?: false }

    val splitScreenFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.SPLIT_SCREEN] ?: false }

    val secondarySiteIndexFlow: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.SECONDARY_SITE_INDEX] ?: 0 }

    val customUaFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.CUSTOM_UA] ?: "" }

    val fontSizeFlow: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.FONT_SIZE] ?: 17 }

    val preventScreenshotsFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.PREVENT_SCREENSHOTS] ?: false }

    // === Save functions ===
    suspend fun saveMenuPosition(isLeft: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.IS_MENU_LEFT] = isLeft }
    }

    suspend fun setSkippedVersion(version: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.SKIPPED_VERSION] = version }
    }

    suspend fun saveLastUpdateCheckTime(time: Long) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = time }
    }

    suspend fun saveTheme(theme: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.THEME] = theme }
    }

    suspend fun saveDefaultAiIndex(index: Int) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.DEFAULT_AI_INDEX] = index }
    }

    suspend fun saveAutoLoadLast(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.AUTO_LOAD_LAST] = enabled }
    }

    suspend fun saveDesktopMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.DESKTOP_MODE] = enabled }
    }

    suspend fun saveSplitScreen(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.SPLIT_SCREEN] = enabled }
    }

    suspend fun saveCustomUa(ua: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.CUSTOM_UA] = ua }
    }

    suspend fun saveFontSize(size: Int) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.FONT_SIZE] = size }
    }

    suspend fun savePreventScreenshots(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.PREVENT_SCREENSHOTS] = enabled }
    }

    suspend fun saveSecondarySiteIndex(index: Int) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.SECONDARY_SITE_INDEX] = index }
    }
}
