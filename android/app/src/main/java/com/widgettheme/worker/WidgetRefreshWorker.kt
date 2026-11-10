package com.widgettheme.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.widgettheme.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Làm mới mọi widget ở chế độ nền (Giai đoạn 4).
 *
 * BroadcastReceiver chỉ có ~10 giây và widget chỉ được hệ thống gọi
 * `onUpdate` tối thiểu 30 phút/lần, nên việc nặng + làm mới định kỳ
 * đẩy sang đây:
 * - Một lần: sau khi sửa theme trong thư viện — mọi widget dùng theme
 *   đó tự cập nhật mà không chặn màn hình chỉnh sửa.
 * - Định kỳ 15 phút: pin % tươi hơn (hệ thống chỉ gọi tối thiểu 30
 *   phút), nền gradient vẽ lại khi hệ thống đổi Sáng/Tối.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            WidgetUpdater.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_ONE_TIME = "widget-refresh-once"
        private const val UNIQUE_PERIODIC = "widget-refresh-periodic"

        /** Làm mới mọi widget một lần, nền (sau khi sửa theme trong thư viện). */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Làm mới định kỳ 15 phút (mức tối thiểu của WorkManager). */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Hủy làm mới định kỳ (khi widget cuối cùng bị gỡ). */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
