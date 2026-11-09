package com.widgettheme.editor

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ThemeEditorActivity — Trình chỉnh sửa theme SC-05 (khung Giai đoạn 1).
 *
 * Yêu cầu chi tiết (BA §9, SC-05):
 * - Ảnh xem trước chiếm ~40% chiều cao, luôn hiển thị khi cuộn; nút đổi
 *   kích thước Nhỏ/Vừa/Lớn và Sáng/Tối.
 * - 4 tab: Màu / Chữ / Hình khối / Nền. Tab Màu: bảng màu, ô nhập HEX,
 *   màu gợi ý từ hình nền, công tắc "Theo màu hệ thống".
 * - Cảnh báo tương phản (W-01) thành dải màu dưới ảnh xem trước + nút
 *   "Sửa nhanh" để engine đề xuất màu chữ đạt chuẩn.
 * - Khi chỉnh riêng cho một widget: tiêu đề ghi "Chỉ áp cho widget này",
 *   token bị ghi đè có dấu chấm bên cạnh.
 * - Mỗi lần đổi giá trị: gọi engine.apply(command) qua UniFFI, nhận lại
 *   theme đã gộp để vẽ lại xem trước (mục tiêu ≤ 100 ms, NFR-01).
 * - Lưu: Rust ghi file theme an toàn (tempfile + rename); Kotlin ghi
 *   liên kết appWidgetId -> themeId vào DataStore; trả RESULT_OK cho
 *   launcher khi mở từ màn hình cấu hình (UC-01).
 *
 * Giai đoạn 1 chỉ có khung Compose + luồng trả kết quả cấu hình.
 * Giai đoạn 3: làm đủ 4 tab + xem trước live + undo/redo + W-01/W-02.
 */
class ThemeEditorActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Mở từ màn hình cấu hình thì mặc định hủy (BR-02: widget vẫn dùng
        // theme Mặc định, không hủy widget — xem onBackPressed / nút Bỏ qua).
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, result)

        setContent {
            MaterialTheme {
                EditorScreen(
                    appWidgetId = appWidgetId,
                    onApply = {
                        // TODO Giai đoạn 3: lưu theme qua Rust, ghi DataStore,
                        // cập nhật widget, trả RESULT_OK.
                        val done = Intent().putExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            appWidgetId,
                        )
                        setResult(Activity.RESULT_OK, done)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorScreen(appWidgetId: Int, onApply: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Chỉnh sửa theme",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Khung Giai đoạn 1 — 4 tab Màu/Chữ/Hình khối/Nền và xem trước " +
                "live sẽ làm ở Giai đoạn 3. Widget: $appWidgetId",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onApply) {
            Text("Áp dụng")
        }
    }
}
