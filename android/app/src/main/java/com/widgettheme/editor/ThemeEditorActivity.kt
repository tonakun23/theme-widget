package com.widgettheme.editor

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.widgettheme.store.BackgroundImageStore
import com.widgettheme.store.ThemeRepository
import com.widgettheme.store.WidgetThemeStore
import com.widgettheme.widget.WidgetUpdater
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * ThemeEditorActivity — Trình chỉnh sửa theme SC-05 (Giai đoạn 3).
 *
 * - Ảnh xem trước ~40% chiều cao, luôn hiển thị khi cuộn; đổi kích thước
 *   Nhỏ/Vừa/Lớn và Sáng/Tối (xem [EditorScreen]).
 * - 4 tab: Màu / Chữ / Hình khối / Nền; cảnh báo tương phản (W-01) + nút
 *   "Sửa nhanh"; undo/redo 50 bước; nháp tự lưu, hết hạn 7 ngày (BR-13).
 * - Mở từ màn hình cấu hình (UC-01, có appWidgetId) = chỉnh riêng cho
 *   widget đó: lưu thành ghi đè riêng (BR-12), tiêu đề "Chỉ áp cho widget
 *   này". Mở từ thư viện (không có widget) = sửa theme: theme có sẵn được
 *   nhân bản (BR-04), theme người dùng ghi đè trực tiếp.
 * - Hủy (BR-02): widget vẫn dùng theme Mặc định, trả RESULT_CANCELED.
 * - Lưu: ghi file theme an toàn (temp + rename), ghi liên kết
 *   appWidgetId -> themeId vào DataStore, cập nhật widget, RESULT_OK.
 */
class ThemeEditorActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var editorState by mutableStateOf<EditorState?>(null)
    private var themeName by mutableStateOf("Theme")
    private var backgroundBitmap: Bitmap? by mutableStateOf(null)
    private var themes by mutableStateOf<List<com.widgettheme.store.ThemeInfo>>(emptyList())

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult
        if (BackgroundImageStore.save(this, appWidgetId, uri)) {
            backgroundBitmap = BackgroundImageStore.load(this, appWidgetId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Mở từ màn hình cấu hình thì mặc định hủy (BR-02).
        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelResult)

        val initialJson = loadInitialJson()
        themeName = ThemeRepository.themeName(initialJson)
        val isPerWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        editorState = EditorState(
            initialJson = initialJson,
            dark = false,
            appWidgetId = appWidgetId,
            isPerWidget = isPerWidget,
        )
        themes = ThemeRepository.listThemes(this)
        if (isPerWidget) {
            backgroundBitmap = BackgroundImageStore.load(this, appWidgetId)
        }

        setContent {
            MaterialTheme {
                val state = editorState ?: return@MaterialTheme
                EditorScreen(
                    state = state,
                    themeName = themeName,
                    themes = themes,
                    backgroundBitmap = backgroundBitmap,
                    onSelectTheme = { info ->
                        // Đổi theme trong lúc cấu hình (UC-01): nạp JSON mới,
                        // giữ nguyên biến thể Sáng/Tối + ảnh nền riêng của widget.
                        editorState = EditorState(
                            initialJson = info.json,
                            dark = state.dark,
                            appWidgetId = appWidgetId,
                            isPerWidget = isPerWidget,
                        )
                        themeName = info.name
                    },
                    onPickImage = { pickImage.launch("image/*") },
                    onRemoveImage = {
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            BackgroundImageStore.delete(this, appWidgetId)
                            backgroundBitmap = null
                        }
                    },
                    onSave = { saveAndFinish() },
                    onCancel = { finish() },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Tự lưu nháp khi rời màn hình (BR-13).
        editorState?.let { if (it.hasUnsavedChanges) it.saveDraft(this) }
    }

    /** Nháp còn hạn thắng trước, rồi theme của widget (hoặc theme_id được mở), rồi theme Mặc định. */
    private fun loadInitialJson(): String {
        EditorState.loadDraft(this, appWidgetId)?.let { return it }
        val themeId = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runBlocking { WidgetThemeStore.getThemeId(this@ThemeEditorActivity, appWidgetId) }
        } else {
            intent.getStringExtra(EXTRA_THEME_ID) ?: "default"
        }
        return ThemeRepository.loadThemeJson(this, themeId)
            ?: ThemeRepository.loadThemeJson(this, "default")
            ?: FALLBACK_THEME_JSON
    }

    private fun saveAndFinish() {
        val state = editorState ?: return
        lifecycleScope.launch {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Chỉnh riêng cho widget: lưu toàn bộ JSON đã sửa làm ghi đè (BR-12).
                WidgetThemeStore.setOverrideJson(
                    this@ThemeEditorActivity,
                    appWidgetId,
                    state.currentJson,
                )
                WidgetUpdater.updateWidgetByProvider(
                    this@ThemeEditorActivity,
                    AppWidgetManager.getInstance(this@ThemeEditorActivity),
                    appWidgetId,
                )
            } else {
                // Sửa trong thư viện: theme có sẵn thì nhân bản (BR-04).
                if (ThemeRepository.isBuiltIn(state.currentJson)) {
                    val (newId, newJson) = ThemeRepository.cloneBuiltIn(
                        this@ThemeEditorActivity,
                        state.currentJson,
                    )
                    ThemeRepository.saveUserTheme(this@ThemeEditorActivity, newId, newJson)
                } else {
                    ThemeRepository.saveUserTheme(
                        this@ThemeEditorActivity,
                        ThemeRepository.themeId(state.currentJson),
                        state.currentJson,
                    )
                }
            }
            state.clearDraft(this@ThemeEditorActivity)
            val done = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, done)
            finish()
        }
    }

    companion object {
        /** Mở editor trong thư viện với theme này (không có widget). */
        const val EXTRA_THEME_ID = "theme_id"
        /** Dự phòng khi assets thiếu theme Mặc định (không nên xảy ra). */
        const val FALLBACK_THEME_JSON = """{"id":"default","kind":"built_in","name":"Mặc định","schema_version":1,"version":1,"light":{"background":{"color":"#FFFFFF","kind":"solid","opacity":100},"colors":{"accent":"#FF8A00","background":"#FFFFFF","border":"#E5E7EB","follow_system":false,"primary":"#3366FF","surface":"#F2F2F7","text_primary":"#1A1A1A","text_secondary":"#6B7280"},"shape":{"border_width":1,"padding":12,"radius":16},"typography":{"body_bold":false,"body_size":14,"caption_size":12,"title_bold":true,"title_size":20}},"dark":{"background":{"color":"#121212","kind":"solid","opacity":100},"colors":{"accent":"#FFB25E","background":"#121212","border":"#2E2E2E","follow_system":false,"primary":"#7AA2FF","surface":"#1E1E1E","text_primary":"#F5F5F5","text_secondary":"#A1A1AA"},"shape":{"border_width":1,"padding":12,"radius":16},"typography":{"body_bold":false,"body_size":14,"caption_size":12,"title_bold":true,"title_size":20}}}"""
    }
}
