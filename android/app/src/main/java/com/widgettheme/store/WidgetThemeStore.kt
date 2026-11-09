package com.widgettheme.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore — CHỈ lưu liên kết appWidgetId -> themeId (và ghi đè widget
 * dạng JSON khi chỉnh riêng, BR-12). Nội dung theme do Rust ghi thành
 * file JSON trong filesDir; Kotlin không lưu nội dung theme ở đây.
 *
 * Giai đoạn 2: hoàn thiện đọc/ghi bất đồng bộ + xóa liên kết khi widget
 * bị gỡ (BR-18) + hỏi giữ/bỏ ghi đè khi đổi theme (BR-14).
 */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_themes",
)

object WidgetThemeStore {
    private fun themeKey(appWidgetId: Int) =
        stringPreferencesKey("widget_${appWidgetId}_theme")

    private fun overrideKey(appWidgetId: Int) =
        stringPreferencesKey("widget_${appWidgetId}_override")

    suspend fun getThemeId(context: Context, appWidgetId: Int): String =
        context.widgetDataStore.data
            .map { it[themeKey(appWidgetId)] ?: "default" }
            .first()

    suspend fun setThemeId(context: Context, appWidgetId: Int, themeId: String) {
        context.widgetDataStore.edit { it[themeKey(appWidgetId)] = themeId }
    }

    suspend fun getOverrideJson(context: Context, appWidgetId: Int): String? =
        context.widgetDataStore.data
            .map { it[overrideKey(appWidgetId)] }
            .first()

    suspend fun setOverrideJson(context: Context, appWidgetId: Int, json: String?) {
        context.widgetDataStore.edit {
            if (json == null) it.remove(overrideKey(appWidgetId))
            else it[overrideKey(appWidgetId)] = json
        }
    }

    suspend fun removeWidget(context: Context, appWidgetId: Int) {
        context.widgetDataStore.edit {
            it.remove(themeKey(appWidgetId))
            it.remove(overrideKey(appWidgetId))
        }
        // BR-18: gỡ widget thì xóa luôn ảnh nền riêng của nó.
        BackgroundImageStore.delete(context, appWidgetId)
    }
}
