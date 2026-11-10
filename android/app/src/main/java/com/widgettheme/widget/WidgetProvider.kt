package com.widgettheme.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.widgettheme.store.WidgetThemeStore
import kotlinx.coroutines.runBlocking

/**
 * 4 provider widget V1 (F-01..F-05). Mỗi provider chỉ là điểm vào:
 * mọi logic theme nằm ở Rust (theme-core) + WidgetUpdater.
 *
 * Vòng đời (UC-07): launcher báo widget bị gỡ -> onDeleted xóa liên kết
 * và ghi đè của widget đó (BR-18); đổi kích thước -> onAppWidgetOptionsChanged
 * chọn bố cục Nhỏ/Vừa/Lớn, giữ nguyên theme (BR-19).
 */
abstract class BaseWidgetProvider(
    private val layoutRes: Int,
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            WidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId, layoutRes)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget đầu tiên được thêm: bật làm mới định kỳ 15 phút (G4).
        com.widgettheme.worker.WidgetRefreshWorker.enqueuePeriodic(context)
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        // Widget cuối cùng bị gỡ: tắt làm mới định kỳ.
        com.widgettheme.worker.WidgetRefreshWorker.cancelPeriodic(context)
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        // Chạm vào widget pin để làm mới % pin ngay.
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetUpdater.updateWidget(
                    context,
                    AppWidgetManager.getInstance(context),
                    appWidgetId,
                    layoutRes,
                )
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // BR-18: xóa liên kết + ghi đè của widget; theme giữ nguyên.
        runBlocking {
            for (appWidgetId in appWidgetIds) {
                runCatching { WidgetThemeStore.removeWidget(context, appWidgetId) }
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        // BR-19: đổi kích thước -> chọn bố cục phù hợp, giữ nguyên theme.
        WidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId, layoutRes)
    }
}

class ClockWidgetProvider : BaseWidgetProvider(com.widgettheme.R.layout.widget_clock)
class CalendarWidgetProvider : BaseWidgetProvider(com.widgettheme.R.layout.widget_calendar)
class NoteWidgetProvider : BaseWidgetProvider(com.widgettheme.R.layout.widget_note)
class BatteryWidgetProvider : BaseWidgetProvider(com.widgettheme.R.layout.widget_battery)
