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

    /** Cập nhật widget theo loại lấy từ AppWidgetInfo (dùng khi editor không biết layout). */
    fun updateWidgetByProvider(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val provider = runCatching {
            appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider?.className
        }.getOrNull() ?: return
        val layoutRes = when {
            provider.contains("Clock") -> R.layout.widget_clock
            provider.contains("Calendar") -> R.layout.widget_calendar
            provider.contains("Note") -> R.layout.widget_note
            provider.contains("Battery") -> R.layout.widget_battery
            else -> return
        }
        updateWidget(context, appWidgetManager, appWidgetId, layoutRes)
    }

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
        // Nội dung thật: đồng hồ/lịch tự cập nhật qua TextClock trong layout;
        // pin đọc mức hiện tại + chạm để làm mới.
        if (layoutRes == R.layout.widget_battery) {
            applyBatteryContent(context, appWidgetId, views)
        }
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
        val themeJson = loadThemeJson(context, appWidgetId, themeId) ?: return null
        val dark = isNightMode(context)
        // Material You (Giai đoạn 4, BR-17): Rust chỉ áp màu hệ thống khi
        // theme bật follow_system; máy < Android 12 thì systemJson null.
        val systemJson = runCatching { SystemColors.read(context, dark) }.getOrNull()
        ThemeEngine.resolveWithSystem(themeJson, dark, systemJson)
    }.getOrNull()

    /**
     * JSON theme của widget: ghi đè riêng (BR-12) thắng trước, rồi theme
     * người dùng trong filesDir, rồi theme có sẵn trong assets.
     */
    private fun loadThemeJson(context: Context, appWidgetId: Int, themeId: String): String? {
        runBlocking { WidgetThemeStore.getOverrideJson(context, appWidgetId) }?.let { return it }
        for (id in listOf(themeId, "default")) {
            com.widgettheme.store.ThemeRepository.loadThemeJson(context, id)?.let { return it }
        }
        return null
    }

    /** Hành động làm mới khi chạm vào widget pin. */
    const val ACTION_REFRESH = "com.widgettheme.ACTION_REFRESH"

    /**
     * Ghi % pin thật lên widget pin. Đọc qua sticky broadcast
     * ACTION_BATTERY_CHANGED (không cần quyền). Đọc lỗi thì giữ nguyên
     * chữ mẫu trong layout, không ghi đè bậy.
     */
    private fun applyBatteryContent(context: Context, appWidgetId: Int, views: RemoteViews) {
        val battery = runCatching {
            context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
            )
        }.getOrNull() ?: return
        val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        views.setTextViewText(R.id.widget_title, "$percent%")
        views.setTextViewText(
            R.id.widget_body,
            if (charging) "Đang sạc" else "Pin còn $percent%",
        )
        // Chạm vào widget để làm mới ngay: pin không tự cập nhật liên tục
        // (onUpdate của hệ thống tối thiểu 30 phút một lần).
        val refreshIntent = android.content.Intent(context, BatteryWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
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
