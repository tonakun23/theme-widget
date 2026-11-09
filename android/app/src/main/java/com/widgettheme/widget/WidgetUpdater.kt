package com.widgettheme.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import com.widgettheme.R
import com.widgettheme.store.WidgetThemeStore
import kotlinx.coroutines.runBlocking

/**
 * Cập nhật một widget instance: đọc themeId từ DataStore, gọi Rust gộp
 * theme (qua FFI, Giai đoạn 2), rồi dùng ThemeApplier vẽ lại.
 *
 * LƯU Ý 10 GIÂY: AppWidgetProvider là BroadcastReceiver, chỉ có ~10 giây.
 * Không vẽ ảnh nền ở đây — việc nặng đẩy sang WorkManager (Giai đoạn 4).
 * Mọi lỗi (theme thiếu, FFI lỗi, launcher không nhận) đều rơi về theme
 * Mặc định hard-code — widget không bao giờ trống/vỡ (E-04, E-06).
 */
object WidgetUpdater {

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutRes: Int,
    ) {
        val theme = resolveForWidget(context, appWidgetId) ?: defaultTheme()
        // Ảnh nền riêng từng widget (Giai đoạn 3): đã thu nhỏ lúc lưu nên
        // đọc nhanh; lỗi thì null -> rơi về nền màu, widget không vỡ.
        val backgroundBitmap = runCatching {
            com.widgettheme.store.BackgroundImageStore.load(context, appWidgetId)
        }.getOrNull()
        val views: RemoteViews =
            ThemeApplier.apply(layoutRes, theme, context.packageName, backgroundBitmap)
        runCatching {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }.onFailure {
            // E-06: launcher không nhận cập nhật — thử lại tối đa 3 lần
            // ở Giai đoạn 3; V1 widget giữ theme Mặc định, không crash.
        }
    }

    /** Đọc themeId + JSON theme, gọi Rust gộp. Lỗi bất kỳ thì `null`. */
    private fun resolveForWidget(
        context: Context,
        appWidgetId: Int,
    ): ThemeApplier.ResolvedTheme? = runCatching {
        val themeId = runBlocking { WidgetThemeStore.getThemeId(context, appWidgetId) }
        val themeJson = loadThemeJson(context, themeId) ?: return null
        val dark = isNightMode(context)
        ThemeEngine.resolve(themeJson, dark)
    }.getOrNull()

    /** JSON theme có sẵn trong assets/themes/<id>.json (đóng gói theo app). */
    private fun loadThemeJson(context: Context, themeId: String): String? {
        for (id in listOf(themeId, "default")) {
            val json = runCatching {
                context.assets.open("themes/$id.json").bufferedReader().use { it.readText() }
            }.getOrNull()
            if (json != null) return json
        }
        return null
    }

    private fun isNightMode(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }

    /** Theme Mặc định hard-code — phao cứu sinh cuối cùng (E-04). */
    fun defaultTheme() = ThemeApplier.ResolvedTheme(
        themeId = "default",
        themeName = "Mặc định",
        background = "#FFFFFF",
        surface = "#F2F2F7",
        primary = "#3366FF",
        accent = "#FF8A00",
        textPrimary = "#1A1A1A",
        textSecondary = "#6B7280",
        border = "#E5E7EB",
        titleSize = 20f,
        bodySize = 14f,
        captionSize = 12f,
        titleBold = true,
        bodyBold = false,
        radius = 16f,
        padding = 12f,
        borderWidth = 1f,
        opacity = 100f,
    )
}
