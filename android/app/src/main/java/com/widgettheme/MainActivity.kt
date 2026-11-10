package com.widgettheme

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.widgettheme.editor.ThemeEditorActivity
import com.widgettheme.store.ThemeInfo
import com.widgettheme.store.ThemePackage
import com.widgettheme.store.ThemeRepository
import com.widgettheme.store.WidgetThemeStore
import com.widgettheme.widget.BatteryWidgetProvider
import com.widgettheme.widget.CalendarWidgetProvider
import com.widgettheme.widget.ClockWidgetProvider
import com.widgettheme.widget.NoteWidgetProvider
import com.widgettheme.widget.ThemeEngine
import com.widgettheme.widget.WidgetUpdater
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * MainActivity — Trang chính SC-02 (Giai đoạn 3, Bước 4).
 *
 * - "Widget của tôi" (SC-06): widget đã thêm ra màn hình chính, theme
 *   đang dùng, nút Chỉnh sửa / Đổi theme.
 * - "Thêm widget": ghim widget mới ra màn hình chính; hệ thống mở màn
 *   hình cấu hình (ThemeEditorActivity) để chọn theme + ảnh (UC-01).
 * - "Thư viện theme" (SC-03): theme có sẵn + theme người dùng, bấm để
 *   mở trình chỉnh sửa (theme có sẵn sẽ được nhân bản khi lưu, BR-04).
 */
class MainActivity : ComponentActivity() {

    private var myWidgets by mutableStateOf<List<MyWidget>>(emptyList())
    private var themes by mutableStateOf<List<ThemeInfo>>(emptyList())
    private var themePickerForWidget by mutableStateOf<Int?>(null)

    /** Chọn file gói theme (.zip) để nhập (Giai đoạn 5, Bước 2). */
    private val importThemeLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importTheme(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text("Theme Widget", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // -- Widget của tôi (SC-06) --
                    SectionTitle("Widget của tôi")
                    if (myWidgets.isEmpty()) {
                        Text("Chưa có widget nào. Thêm widget bên dưới.", fontSize = 14.sp, color = Color.Gray)
                    } else {
                        myWidgets.forEach { w ->
                            WidgetRow(
                                widget = w,
                                onEdit = { openEditor(w.appWidgetId, null) },
                                onChangeTheme = { themePickerForWidget = w.appWidgetId },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // -- Thêm widget --
                    SectionTitle("Thêm widget")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetType.entries.forEach { type ->
                            Button(onClick = { pinWidget(type) }) { Text(type.label) }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // -- Thư viện theme (SC-03) --
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle("Thư viện theme")
                        TextButton(onClick = { importThemeLauncher.launch("*/*") }) {
                            Text("Nhập theme")
                        }
                    }
                    themes.forEach { info ->
                        ThemeRow(
                            info,
                            onClick = { openEditor(AppWidgetManager.INVALID_APPWIDGET_ID, info.id) },
                            onExport = { exportTheme(info) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Hộp chọn theme cho một widget (Đổi theme).
                val pickerId = themePickerForWidget
                if (pickerId != null) {
                    AlertDialog(
                        onDismissRequest = { themePickerForWidget = null },
                        title = { Text("Chọn theme") },
                        text = {
                            Column {
                                themes.forEach { info ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            applyThemeToWidget(pickerId, info.id)
                                            themePickerForWidget = null
                                        }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ThemeSwatches(info)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(info.name, fontSize = 15.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { themePickerForWidget = null }) { Text("Đóng") }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val manager = AppWidgetManager.getInstance(this@MainActivity)
            val list = mutableListOf<MyWidget>()
            WidgetType.entries.forEach { type ->
                val ids = manager.getAppWidgetIds(ComponentName(this@MainActivity, type.provider))
                ids.forEach { id ->
                    val themeId = WidgetThemeStore.getThemeId(this@MainActivity, id)
                    val override = WidgetThemeStore.getOverrideJson(this@MainActivity, id)
                    val json = ThemeRepository.loadThemeJson(this@MainActivity, themeId)
                    val name = (if (override != null) ThemeRepository.themeName(override) else json?.let { ThemeRepository.themeName(it) }) ?: themeId
                    list.add(MyWidget(id, type.label, name, override != null))
                }
            }
            myWidgets = list
            themes = ThemeRepository.listThemes(this@MainActivity)
        }
    }

    /** Ghim widget mới ra màn hình chính; hệ thống sẽ mở màn hình cấu hình (UC-01). */
    private fun pinWidget(type: WidgetType) {
        val manager = AppWidgetManager.getInstance(this)
        val ok = manager.requestPinAppWidget(ComponentName(this, type.provider), null, null)
        if (!ok) {
            Toast.makeText(this, "Thiết bị không hỗ trợ ghim widget, hãy thêm từ màn hình chính.", Toast.LENGTH_LONG).show()
        }
    }

    /** Xuất theme thành gói .zip rồi mở share sheet (Giai đoạn 5). */
    private fun exportTheme(info: ThemeInfo) {
        if (!ThemePackage.shareTheme(this@MainActivity, info.json)) {
            Toast.makeText(this@MainActivity, "Không xuất được theme", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Nhập gói theme từ file .zip (Giai đoạn 5, Bước 2).
     *
     * - Đọc `theme.json` trong gói, từ chối file lạ (sai manifest).
     * - Kiểm tra theme qua lõi Rust: `validateThemeJson` trả `null`
     *   thì gói không hợp lệ, không lưu gì cả.
     * - Theme nhập vào luôn là theme người dùng (`kind` = "custom"):
     *   không bao giờ ghi đè theme có sẵn. Trùng id thì đổi id mới
     *   (`<id>_nhap`, `<id>_nhap2`, ...) và thêm "(đã nhập)" vào tên,
     *   theo đúng cách nhân bản theme có sẵn (BR-04).
     */
    private fun importTheme(uri: android.net.Uri) {
        val themeJson = ThemePackage.readThemeJsonFromPackage(this, uri)
        if (themeJson == null) {
            Toast.makeText(this, "Không đọc được gói theme — cần file .zip do app này xuất ra", Toast.LENGTH_LONG).show()
            return
        }
        if (ThemeEngine.validateThemeJson(themeJson) == null) {
            Toast.makeText(this, "Gói theme không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        val obj = runCatching { JSONObject(themeJson) }.getOrNull()
        val baseId = obj?.optString("id", "")?.takeIf { it.isNotBlank() }
        if (obj == null || baseId == null) {
            Toast.makeText(this, "Gói theme thiếu id", Toast.LENGTH_SHORT).show()
            return
        }
        var newId = baseId
        var name = obj.optString("name", "Theme")
        if (ThemeRepository.loadThemeJson(this, newId) != null) {
            var n = 1
            do {
                newId = if (n == 1) "${baseId}_nhap" else "${baseId}_nhap$n"
                n++
            } while (ThemeRepository.loadThemeJson(this, newId) != null)
            name = "$name (đã nhập)"
        }
        obj.put("id", newId)
        obj.put("name", name)
        obj.put("kind", "custom")
        if (!ThemeRepository.saveUserTheme(this, newId, obj.toString())) {
            Toast.makeText(this, "Không lưu được theme đã nhập", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Đã nhập theme \"$name\"", Toast.LENGTH_SHORT).show()
        loadData()
    }

    private fun openEditor(appWidgetId: Int, themeId: String?) {
        val intent = Intent(this, ThemeEditorActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            themeId?.let { putExtra(ThemeEditorActivity.EXTRA_THEME_ID, it) }
        }
        startActivity(intent)
    }

    /** Đổi theme cho widget: ghi liên kết mới, xóa ghi đè riêng (nếu có), cập nhật widget. */
    private fun applyThemeToWidget(appWidgetId: Int, themeId: String) {
        lifecycleScope.launch {
            WidgetThemeStore.setThemeId(this@MainActivity, appWidgetId, themeId)
            WidgetThemeStore.setOverrideJson(this@MainActivity, appWidgetId, null)
            WidgetUpdater.updateWidgetByProvider(
                this@MainActivity,
                AppWidgetManager.getInstance(this@MainActivity),
                appWidgetId,
            )
            loadData()
        }
    }
}

data class MyWidget(
    val appWidgetId: Int,
    val typeLabel: String,
    val themeName: String,
    val hasOverride: Boolean,
)

enum class WidgetType(val label: String, val provider: Class<*>) {
    CLOCK("Đồng hồ", ClockWidgetProvider::class.java),
    CALENDAR("Lịch", CalendarWidgetProvider::class.java),
    NOTE("Ghi chú", NoteWidgetProvider::class.java),
    BATTERY("Pin", BatteryWidgetProvider::class.java),
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun WidgetRow(widget: MyWidget, onEdit: () -> Unit, onChangeTheme: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFF2F2F7), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(widget.typeLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                widget.themeName + if (widget.hasOverride) " (riêng)" else "",
                fontSize = 13.sp,
                color = Color.Gray,
            )
        }
        TextButton(onClick = onChangeTheme) { Text("Đổi theme") }
        TextButton(onClick = onEdit) { Text("Chỉnh sửa") }
    }
}

@Composable
private fun ThemeRow(info: ThemeInfo, onClick: () -> Unit, onExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFF2F2F7), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatches(info)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(info.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(if (info.kind == "built_in") "Có sẵn" else "Của tôi", fontSize = 13.sp, color = Color.Gray)
        }
        TextButton(onClick = onExport) { Text("Chia sẻ") }
    }
}

/** 3 chấm màu đại diện: nền / chính / nhấn (gộp từ biến thể Sáng qua Rust). */
@Composable
private fun ThemeSwatches(info: ThemeInfo) {
    val resolved = ThemeEngine.resolve(info.json, false)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(resolved?.background, resolved?.primary, resolved?.accent).forEach { hex ->
            Box(
                modifier = Modifier.size(20.dp)
                    .background(parseHexOrGray(hex), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(10.dp)),
            )
        }
    }
}

private fun parseHexOrGray(hex: String?): Color = try {
    val h = (hex ?: "").trimStart('#')
    when (h.length) {
        3 -> Color("${h[0]}${h[0]}".toInt(16), "${h[1]}${h[1]}".toInt(16), "${h[2]}${h[2]}".toInt(16))
        6 -> Color(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
        else -> Color.Gray
    }
} catch (e: Exception) {
    Color.Gray
}
