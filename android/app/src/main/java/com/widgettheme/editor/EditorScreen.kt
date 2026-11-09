package com.widgettheme.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgettheme.widget.ThemeApplier
import com.widgettheme.widget.ThemeEngine
import kotlin.math.pow

/**
 * Màn hình chỉnh sửa SC-05 (Giai đoạn 3, Bước 3).
 *
 * - Xem trước cố định phía trên (~40% chiều cao), đổi theo S/M/L + Sáng/Tối.
 * - 4 tab: Màu / Chữ / Hình khối / Nền. Sửa token nào thì gộp lại qua Rust
 *   ngay để xem trước luôn đúng theme cuối.
 * - Dải cảnh báo tương phản (W-01) + nút Sửa nhanh.
 * - Chỉnh theo biến thể đang xem (Sáng/Tối); đổi biến thể không làm mất
 *   màu chưa lưu (khác lỗi prototype SC-05: mỗi biến thể sửa trực tiếp
 *   trên JSON của chính nó).
 */
@Composable
fun EditorScreen(
    state: EditorState,
    themeName: String,
    backgroundBitmap: Bitmap?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var tick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var size by remember { mutableStateOf(PreviewSize.MEDIUM) }

    fun touched() {
        tick++
    }

    val resolved = state.resolved
    val warnings = state.warnings

    // tick được đọc ở key() để mỗi lần sửa đều vẽ lại toàn bộ màn hình.
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        key(tick) {
        // -- Tiêu đề + Hoàn tác/Làm lại --
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(themeName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (state.isPerWidget) {
                    Text("Chỉ áp cho widget này", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Row {
                TextButton(onClick = { if (state.undo()) touched() }, enabled = state.canUndo) {
                    Text("Hoàn tác")
                }
                TextButton(onClick = { if (state.redo()) touched() }, enabled = state.canRedo) {
                    Text("Làm lại")
                }
            }
        }

        // -- Xem trước --
        WidgetPreview(resolved = resolved, bitmap = backgroundBitmap, size = size)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                PreviewSize.entries.forEach { s ->
                    TextButton(onClick = { size = s }) {
                        Text(
                            s.label,
                            fontWeight = if (size == s) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.dark) "Tối" else "Sáng", fontSize = 14.sp)
                Switch(
                    checked = state.dark,
                    onCheckedChange = { state.setDarkMode(it); touched() },
                )
            }
        }

        // -- Cảnh báo tương phản + Sửa nhanh --
        if (warnings.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(warnings.first().message, modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = {
                    val bg = resolved?.background ?: "#FFFFFF"
                    state.setToken(currentVariant(state), "colors", "text_primary", bestTextColor(bg))
                    touched()
                }) {
                    Text("Sửa nhanh")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // -- 4 tab --
        TabRow(selectedTabIndex = tab) {
            listOf("Màu", "Chữ", "Hình khối", "Nền").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp),
        ) {
            when (tab) {
                0 -> ColorTab(state, onEdit = ::touched)
                1 -> TextTab(state, onEdit = ::touched)
                2 -> ShapeTab(state, onEdit = ::touched)
                3 -> BackgroundTab(state, backgroundBitmap, onPickImage, onRemoveImage, onEdit = ::touched)
            }
        }

        // -- Nút dưới --
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("Bỏ qua") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave) { Text(if (state.isPerWidget) "Áp dụng" else "Lưu") }
        }
        }
    }
}

enum class PreviewSize(val label: String, val heightDp: Int) {
    SMALL("Nhỏ", 120),
    MEDIUM("Vừa", 180),
    LARGE("Lớn", 240),
}

@Composable
private fun WidgetPreview(
    resolved: ThemeApplier.ResolvedTheme?,
    bitmap: Bitmap?,
    size: PreviewSize,
) {
    if (resolved == null) {
        Box(
            modifier = Modifier.fillMaxWidth().height(size.heightDp.dp)
                .background(Color.LightGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Theme lỗi, không xem trước được")
        }
        return
    }
    val bg = parseHex(resolved.background)
    val shape = RoundedCornerShape(resolved.radius.dp)
    Box(
        modifier = Modifier.fillMaxWidth().height(size.heightDp.dp)
            .clip(shape)
            .background(bg)
            .border(resolved.borderWidth.dp, parseHex(resolved.border), shape)
            .padding(resolved.padding.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop,
            )
        }
        Column {
            Text(
                "09:41",
                color = parseHex(resolved.textPrimary),
                fontSize = resolved.titleSize.sp,
                fontWeight = if (resolved.titleBold) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                "Thứ Hai, 9 tháng 11",
                color = parseHex(resolved.textSecondary),
                fontSize = resolved.bodySize.sp,
                fontWeight = if (resolved.bodyBold) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                "Đồng hồ",
                color = parseHex(resolved.accent),
                fontSize = resolved.captionSize.sp,
            )
        }
    }
}

private fun currentVariant(state: EditorState): String = if (state.dark) "dark" else "light"

@Composable
private fun ColorTab(state: EditorState, onEdit: () -> Unit) {
    val variant = currentVariant(state)
    val resolved = state.resolved
    val tokens = listOf(
        Triple("Nền", "background", resolved?.background),
        Triple("Mặt", "surface", resolved?.surface),
        Triple("Chính", "primary", resolved?.primary),
        Triple("Nhấn", "accent", resolved?.accent),
        Triple("Chữ chính", "text_primary", resolved?.textPrimary),
        Triple("Chữ phụ", "text_secondary", resolved?.textSecondary),
        Triple("Viền", "border", resolved?.border),
    )
    tokens.forEach { (label, key, value) ->
        if (value != null) {
            ColorRow(label, value) { hex ->
                state.setToken(variant, "colors", key, hex)
                onEdit()
            }
        }
    }
    // Theo màu hệ thống: đọc từ JSON gốc (không nằm trong ResolvedTheme).
    val followSystem = runCatching {
        org.json.JSONObject(state.currentJson)
            .optJSONObject(variant)?.optJSONObject("colors")
            ?.optBoolean("follow_system", false) ?: false
    }.getOrDefault(false)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Theo màu hệ thống")
        Switch(
            checked = followSystem,
            onCheckedChange = { checked ->
                state.setToken(variant, "colors", "follow_system", checked)
                onEdit()
            },
        )
    }
}

@Composable
private fun ColorRow(label: String, value: String, onChange: (String) -> Unit) {
    var hex by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp)
                .background(parseHex(value), RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.width(84.dp), fontSize = 14.sp)
        TextField(
            value = hex,
            onValueChange = {
                hex = it
                if (ThemeEngine.validateColor(it)) onChange(it)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TextTab(state: EditorState, onEdit: () -> Unit) {
    val variant = currentVariant(state)
    val resolved = state.resolved ?: return
    SliderRow("Cỡ tiêu đề", resolved.titleSize, 12f..32f) {
        state.setToken(variant, "typography", "title_size", it.toInt()); onEdit()
    }
    SliderRow("Cỡ nội dung", resolved.bodySize, 10f..24f) {
        state.setToken(variant, "typography", "body_size", it.toInt()); onEdit()
    }
    SliderRow("Cỡ chú thích", resolved.captionSize, 8f..20f) {
        state.setToken(variant, "typography", "caption_size", it.toInt()); onEdit()
    }
    SwitchRow("Tiêu đề đậm", resolved.titleBold) {
        state.setToken(variant, "typography", "title_bold", it); onEdit()
    }
    SwitchRow("Nội dung đậm", resolved.bodyBold) {
        state.setToken(variant, "typography", "body_bold", it); onEdit()
    }
}

@Composable
private fun ShapeTab(state: EditorState, onEdit: () -> Unit) {
    val variant = currentVariant(state)
    val resolved = state.resolved ?: return
    SliderRow("Bo góc", resolved.radius, 0f..32f) {
        state.setToken(variant, "shape", "radius", it.toInt()); onEdit()
    }
    SliderRow("Đệm trong", resolved.padding, 0f..24f) {
        state.setToken(variant, "shape", "padding", it.toInt()); onEdit()
    }
    SliderRow("Độ dày viền", resolved.borderWidth, 0f..4f) {
        state.setToken(variant, "shape", "border_width", it.toInt()); onEdit()
    }
}

@Composable
private fun BackgroundTab(
    state: EditorState,
    bitmap: Bitmap?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onEdit: () -> Unit,
) {
    val variant = currentVariant(state)
    val resolved = state.resolved ?: return
    ColorRow("Màu nền", resolved.background) { hex ->
        state.setToken(variant, "background", "color", hex)
        onEdit()
    }
    SliderRow("Độ mờ nền", resolved.opacity, 0f..100f) {
        state.setToken(variant, "background", "opacity", it.toInt()); onEdit()
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text("Ảnh nền riêng cho widget này", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(8.dp))
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRemoveImage) { Text("Xóa ảnh") }
    } else {
        Button(onClick = onPickImage) { Text("Chọn ảnh nền") }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 14.sp)
            Text(value.toInt().toString(), fontSize = 14.sp, color = Color.Gray)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Đọc mã màu #RGB/#RRGGBB/#RRGGBBAA; lỗi thì xám. */
private fun parseHex(hex: String): Color = try {
    val h = hex.trimStart('#')
    when (h.length) {
        3 -> {
            val r = "${h[0]}${h[0]}".toInt(16)
            val g = "${h[1]}${h[1]}".toInt(16)
            val b = "${h[2]}${h[2]}".toInt(16)
            Color(r, g, b)
        }
        6 -> Color(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
        8 -> Color(
            h.substring(2, 4).toInt(16),
            h.substring(4, 6).toInt(16),
            h.substring(6, 8).toInt(16),
            h.substring(0, 2).toInt(16),
        )
        else -> Color.Gray
    }
} catch (e: Exception) {
    Color.Gray
}

/** Chọn đen/trắng, màu nào tương phản với nền tốt hơn thì lấy (cho nút Sửa nhanh). */
private fun bestTextColor(bgHex: String): String {
    fun luminance(hex: String): Double {
        val c = parseHex(hex)
        fun f(v: Float): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * f(c.red) + 0.7152 * f(c.green) + 0.0722 * f(c.blue)
    }
    fun contrast(a: Double, b: Double): Double {
        val (hi, lo) = if (a > b) a to b else b to a
        return (hi + 0.05) / (lo + 0.05)
    }
    val bgL = luminance(bgHex)
    val dark = contrast(bgL, luminance("#1A1A1A"))
    val light = contrast(bgL, luminance("#FFFFFF"))
    return if (dark >= light) "#1A1A1A" else "#FFFFFF"
}
