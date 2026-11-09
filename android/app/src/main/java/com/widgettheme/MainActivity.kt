package com.widgettheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MainActivity — Trang chủ với 3 tab dưới cùng (BA §9):
 * Thư viện (SC-02) | Widget của tôi (SC-06) | Cài đặt (SC-08).
 *
 * Khung Giai đoạn 1. Giai đoạn 2-3: thêm Navigation Compose + danh sách
 * theme thật (đọc từ theme-core qua FFI) + SC-03 chi tiết theme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text(
                    text = "Theme Widget — khung Giai đoạn 1",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
