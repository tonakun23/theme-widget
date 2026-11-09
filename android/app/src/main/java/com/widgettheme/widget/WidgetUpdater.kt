package com.widgettheme.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.widgettheme.R

/**
 * Cập nhật một widget instance: đọc themeId từ DataStore, gọi Rust gộp
 * theme (qua FFI, Giai đoạn 2), rồi dùng ThemeApplier vẽ lại.
 *
 * Giai đoạn 1: hàm này mới là khung — `resolveTheme()` đang trả theme
 * Mặc định hard-code; Giai đoạn 2 sẽ thay bằng gọi `theme-ffi`
 * (resolve_theme_json) và đọc bảng màu hệ thống khi bật follow_system.
 *
 * LƯU Ý 10 GIÂY: AppWidgetProvider là BroadcastReceiver, chỉ có ~10 giây.
 * Không vẽ ảnh nền ở đây — việc nặng đẩy sang WorkManager (Giai đoạn 4).
 */
object WidgetUpdater {

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutRes: Int,
    ) {
        // TODO Giai đoạn 2:
        // 1. themeId = WidgetThemeStore.getThemeId(appWidgetId) ?: "default"
        // 2. resolvedJson = ThemeFfi.resolveThemeJson(themeJson, darkMode)
        // 3. theme = parse ResolvedTheme từ JSON
        // 4. Thử lại tối đa 3 lần nếu launcher không nhận cập nhật (E-06);
        //    lỗi thì widget hiển thị theme Mặc định, không bao giờ trống.
        val fallback = ThemeApplier.ResolvedTheme(
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
        val views: RemoteViews =
            ThemeApplier.apply(layoutRes, fallback, context.packageName)
        runCatching {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }.onFailure {
            // E-06: launcher không nhận cập nhật — thử lại 3 lần ở Giai đoạn 2.
            // V1: widget giữ theme Mặc định, không crash.
        }
    }
}
