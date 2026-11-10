package com.widgettheme.editor

import android.content.Context
import com.widgettheme.widget.ThemeApplier
import com.widgettheme.widget.ThemeEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Trạng thái trình chỉnh sửa SC-05 (Giai đoạn 3, Bước 2).
 *
 * Cách làm: Kotlin giữ theme dưới dạng JSON + chồng snapshot để undo/redo,
 * còn mọi phép gộp (BR-24), sinh biến thể tối (BR-16) và kiểm tra tương
 * phản (W-01) đều chạy trong Rust qua FFI có sẵn (resolve/validate).
 * Lõi Rust theme-editor (Command/undo) vẫn dùng cho CLI; ở app, undo
 * bằng snapshot JSON là đủ và không cần thêm hàm FFI mới.
 *
 * - Undo/redo tối đa 50 bước (BR-10).
 * - Nháp tự lưu vào filesDir, hết hạn sau 7 ngày (BR-13).
 * - Mỗi lần sửa đều gộp lại qua Rust để xem trước luôn đúng theme cuối.
 */
data class EditorWarning(val code: String, val message: String)

class EditorState(
    initialJson: String,
    var dark: Boolean,
    val appWidgetId: Int,
    /** true = chỉnh riêng cho widget này (BR-12), tiêu đề hiện "Chỉ áp cho widget này". */
    val isPerWidget: Boolean,
    /** Đọc bảng màu hệ thống Material You (BR-17); mặc định không có. */
    private val systemColors: (Boolean) -> String? = { null },
) {
    var currentJson: String = initialJson
        private set

    /** Theme đã gộp qua Rust cho biến thể đang xem; null khi JSON lỗi. */
    var resolved: ThemeApplier.ResolvedTheme? = null
        private set

    /** Cảnh báo từ Rust (tương phản W-01...); rỗng khi theme đạt chuẩn. */
    var warnings: List<EditorWarning> = emptyList()
        private set

    private val baselineJson: String = initialJson
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    val hasUnsavedChanges: Boolean get() = currentJson != baselineJson
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        refresh()
    }

    /** Đổi một token: variant "light"/"dark", section "colors"/"typography"/"shape"/"background". */
    fun setToken(variant: String, section: String, key: String, value: Any) {
        edit { root ->
            val variantObj = root.optJSONObject(variant) ?: JSONObject().also { root.put(variant, it) }
            val sectionObj = variantObj.optJSONObject(section) ?: JSONObject().also { variantObj.put(section, it) }
            when (value) {
                is String -> sectionObj.put(key, value)
                is Number -> sectionObj.put(key, value.toDouble())
                is Boolean -> sectionObj.put(key, value)
                else -> sectionObj.put(key, value.toString())
            }
        }
    }

    /** Đổi JSON tùy ý (dùng cho thao tác gộp nhiều token). */
    fun edit(block: (JSONObject) -> Unit) {
        val next = try {
            JSONObject(currentJson).also(block).toString()
        } catch (e: Exception) {
            return
        }
        if (next == currentJson) return
        undoStack.addLast(currentJson)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        currentJson = next
        refresh()
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(currentJson)
        currentJson = prev
        refresh()
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(currentJson)
        currentJson = next
        refresh()
        return true
    }

    /** Đổi biến thể Sáng/Tối đang xem (chỉ đổi bản xem, không đổi dữ liệu). */
    fun setDarkMode(isDark: Boolean) {
        if (dark == isDark) return
        dark = isDark
        refresh()
    }

    // -- Nháp (BR-13): tự lưu, hết hạn sau 7 ngày. --

    fun saveDraft(context: Context) {
        runCatching {
            val dir = File(context.filesDir, DRAFT_DIR).also { it.mkdirs() }
            val obj = JSONObject().apply {
                put("saved_at", System.currentTimeMillis())
                put("json", currentJson)
            }
            File(dir, "$appWidgetId.json").writeText(obj.toString())
        }
    }

    fun clearDraft(context: Context) {
        runCatching { File(File(context.filesDir, DRAFT_DIR), "$appWidgetId.json").delete() }
    }

    // -- Nội bộ --

    private fun refresh() {
        // Xem trước cũng áp màu hệ thống khi theme bật follow_system (BR-17).
        val systemJson = runCatching { systemColors(dark) }.getOrNull()
        resolved = ThemeEngine.resolveWithSystem(currentJson, dark, systemJson)
        warnings = parseWarnings(ThemeEngine.validateThemeJson(currentJson))
    }

    private fun parseWarnings(json: String?): List<EditorWarning> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.optJSONObject(i) ?: return emptyList()
                EditorWarning(
                    code = o.optString("code", ""),
                    message = o.optString("message", ""),
                )
            }.filter { it.message.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_UNDO = 50
        const val DRAFT_DIR = "editor_drafts"
        const val DRAFT_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Đọc nháp còn hạn của widget; null khi không có hoặc đã hết hạn. */
        fun loadDraft(context: Context, appWidgetId: Int): String? = runCatching {
            val f = File(File(context.filesDir, DRAFT_DIR), "$appWidgetId.json")
            if (!f.exists()) return null
            val obj = JSONObject(f.readText())
            val savedAt = obj.optLong("saved_at", 0)
            if (System.currentTimeMillis() - savedAt > DRAFT_TTL_MS) {
                f.delete()
                return null
            }
            obj.optString("json", "").ifBlank { null }
        }.getOrNull()
    }
}
