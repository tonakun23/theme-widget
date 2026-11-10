package com.widgettheme.widget

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * Đọc bảng màu hệ thống Material You (Giai đoạn 4, BR-17).
 *
 * Android 12+ (API 31) cung cấp các màu `system_*` theo hình nền máy;
 * máy cũ hơn không có thì trả `null` và Rust dùng màu theme như thường.
 * JSON trả về khớp struct `Colors` của Rust (thiếu trường nào thì
 * trường đó giữ nguyên màu theme).
 */
object SystemColors {

    /** Đọc bảng màu hệ thống cho biến thể Sáng/Tối; null khi không có. */
    fun read(context: Context, dark: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val res = context.resources
        fun color(name: String): String? {
            val id = res.getIdentifier(name, "color", "android")
            if (id == 0) return null
            return try {
                val c = res.getColor(id, context.theme)
                "#%06X".format(0xFFFFFF and c)
            } catch (e: Exception) {
                null
            }
        }
        // Tonal palette Material You: Sáng dùng tone đậm (600) trên nền
        // nhạt (50), Tối dùng tone nhạt (200) trên nền đậm (900).
        val names = if (!dark) {
            listOf(
                "system_neutral1_50",
                "system_neutral1_100",
                "system_accent1_600",
                "system_accent2_600",
                "system_neutral1_900",
                "system_neutral2_700",
                "system_neutral1_300",
            )
        } else {
            listOf(
                "system_neutral1_900",
                "system_neutral1_800",
                "system_accent1_200",
                "system_accent2_200",
                "system_neutral1_100",
                "system_neutral2_300",
                "system_neutral1_700",
            )
        }
        val keys = listOf(
            "background",
            "surface",
            "primary",
            "accent",
            "text_primary",
            "text_secondary",
            "border",
        )
        val obj = JSONObject()
        var found = false
        keys.zip(names).forEach { (key, name) ->
            color(name)?.let {
                obj.put(key, it)
                found = true
            }
        }
        return if (found) obj.toString() else null
    }
}
