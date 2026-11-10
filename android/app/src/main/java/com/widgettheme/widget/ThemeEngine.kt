package com.widgettheme.widget

import org.json.JSONObject
import uniffi.theme_ffi.FfiException
import uniffi.theme_ffi.renderBackgroundPng
import uniffi.theme_ffi.resolveThemeJson
import uniffi.theme_ffi.resolveThemeJsonWithSystem
import uniffi.theme_ffi.validateColor as ffiValidateColor
import uniffi.theme_ffi.validateThemeJson as ffiValidateThemeJson

/**
 * ThemeEngine — cầu nối Kotlin <-> Rust (theme-ffi qua UniFFI, Giai đoạn 2).
 *
 * Kotlin chỉ truyền JSON theme + cờ Sáng/Tối sang Rust; mọi phép gộp
 * (BR-24), kế thừa, sinh biến thể tối (BR-16) và kiểm tra tương phản
 * đều chạy trong theme-core. Lỗi FFI nào cũng trả `null` / `false` để
 * phía gọi rơi về theme Mặc định — widget không bao giờ trống/vỡ (E-04).
 *
 * Thư viện `.so` do cargo-ndk build vào `src/main/jniLibs/<abi>/`;
 * code UniFFI sinh ra tự nạp `libtheme_ffi.so` qua JNA.
 */
object ThemeEngine {

    /** Gộp theme JSON thành [ThemeApplier.ResolvedTheme]. Lỗi thì `null`. */
    fun resolve(themeJson: String, dark: Boolean): ThemeApplier.ResolvedTheme? {
        val resolvedJson = try {
            resolveThemeJson(themeJson, dark)
        } catch (e: FfiException) {
            return null
        } catch (e: Exception) {
            return null
        }
        return parseResolved(resolvedJson)
    }

    /**
     * Gộp theme kèm bảng màu hệ thống Material You (Giai đoạn 4, BR-17).
     * `systemJson` null (máy < Android 12) thì gộp thường; lỗi khi gộp
     * kèm hệ thống thì rơi về gộp thường, rồi mới `null`.
     */
    fun resolveWithSystem(
        themeJson: String,
        dark: Boolean,
        systemJson: String?,
    ): ThemeApplier.ResolvedTheme? {
        if (systemJson == null) return resolve(themeJson, dark)
        val resolvedJson = try {
            resolveThemeJsonWithSystem(themeJson, dark, systemJson)
        } catch (e: Exception) {
            return resolve(themeJson, dark)
        }
        return parseResolved(resolvedJson) ?: resolve(themeJson, dark)
    }

    /**
     * Render nền gradient của theme thành PNG (Giai đoạn 4, theme-render).
     * Trả `null` khi nền không phải gradient hoặc render lỗi — phía gọi
     * dùng đường tô màu / ảnh nền riêng như trước.
     */
    fun renderBackground(
        themeJson: String,
        dark: Boolean,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Int,
    ): ByteArray? = try {
        renderBackgroundPng(themeJson, dark, widthPx.toUInt(), heightPx.toUInt(), radiusPx.toUInt())
    } catch (e: Exception) {
        null
    }

    /** Kiểm tra một mã màu (#RGB / #RRGGBB / #RRGGBBAA). */
    fun validateColor(color: String): Boolean = try {
        ffiValidateColor(color)
    } catch (e: Exception) {
        false
    }

    /** Kiểm tra theme JSON, trả danh sách cảnh báo dạng JSON. Lỗi thì `null`. */
    fun validateThemeJson(themeJson: String): String? = try {
        ffiValidateThemeJson(themeJson)
    } catch (e: FfiException) {
        null
    } catch (e: Exception) {
        null
    }

    /** JSON của Rust dùng snake_case (serde mặc định của ResolvedTheme). */
    private fun parseResolved(json: String): ThemeApplier.ResolvedTheme? = try {
        val o = JSONObject(json)
        ThemeApplier.ResolvedTheme(
            themeId = o.optString("theme_id", "default"),
            themeName = o.optString("theme_name", "Mặc định"),
            background = o.optString("background", "#FFFFFF"),
            surface = o.optString("surface", "#F2F2F7"),
            primary = o.optString("primary", "#3366FF"),
            accent = o.optString("accent", "#FF8A00"),
            textPrimary = o.optString("text_primary", "#1A1A1A"),
            textSecondary = o.optString("text_secondary", "#6B7280"),
            border = o.optString("border", "#E5E7EB"),
            titleSize = o.optDouble("title_size", 20.0).toFloat(),
            bodySize = o.optDouble("body_size", 14.0).toFloat(),
            captionSize = o.optDouble("caption_size", 12.0).toFloat(),
            titleBold = o.optBoolean("title_bold", true),
            bodyBold = o.optBoolean("body_bold", false),
            radius = o.optDouble("radius", 16.0).toFloat(),
            padding = o.optDouble("padding", 12.0).toFloat(),
            borderWidth = o.optDouble("border_width", 1.0).toFloat(),
            opacity = o.optDouble("opacity", 100.0).toFloat(),
            bgKind = o.optString("bg_kind", "solid"),
        )
    } catch (e: Exception) {
        null
    }
}
