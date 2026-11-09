package com.widgettheme.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // BR-18: xóa liên kết + ghi đè của widget; theme giữ nguyên.
        // TODO Giai đoạn 2: gọi WidgetThemeStore.removeWidget() (DataStore).
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
