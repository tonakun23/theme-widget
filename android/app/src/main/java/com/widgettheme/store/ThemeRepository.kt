package com.widgettheme.store

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Kho theme JSON (Giai đoạn 3, Bước 3).
 *
 * - Theme có sẵn: đọc từ assets/themes/<id>.json (chỉ đọc, BR-04).
 * - Theme người dùng tạo/nhân bản: đọc/ghi ở filesDir/themes/<id>.json,
 *   filesDir được ưu tiên khi đọc (bản người dùng thắng bản có sẵn).
 * - Ghi an toàn: ghi file tạm rồi rename (không ghi đè trực tiếp).
 */
object ThemeRepository {

    private const val DIR = "themes"

    /** Đọc JSON theme: filesDir trước, assets sau; null khi không có. */
    fun loadThemeJson(context: Context, themeId: String): String? {
        val userFile = File(File(context.filesDir, DIR), "$themeId.json")
        if (userFile.exists()) {
            runCatching { return userFile.readText() }
        }
        return runCatching {
            context.assets.open("themes/$themeId.json").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    /** Ghi theme người dùng (temp + rename). Trả về false khi ghi lỗi. */
    fun saveUserTheme(context: Context, themeId: String, json: String): Boolean = runCatching {
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        val tmp = File(dir, "$themeId.json.tmp")
        val dst = File(dir, "$themeId.json")
        tmp.writeText(json)
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            return false
        }
        true
    }.getOrDefault(false)

    /** Theme có sẵn trong app thì kind = "built_in" (chỉ đọc, BR-04). */
    fun isBuiltIn(themeJson: String): Boolean = runCatching {
        JSONObject(themeJson).optString("kind", "") == "built_in"
    }.getOrDefault(false)

    fun themeName(themeJson: String): String = runCatching {
        JSONObject(themeJson).optString("name", "Theme")
    }.getOrDefault("Theme")

    fun themeId(themeJson: String): String = runCatching {
        JSONObject(themeJson).optString("id", "default")
    }.getOrDefault("default")

    /**
     * Nhân bản theme có sẵn (BR-04): id mới duy nhất, tên "<tên> (bản của tôi)",
     * kind thành "custom". Trả về (id mới, JSON mới).
     */
    fun cloneBuiltIn(context: Context, themeJson: String): Pair<String, String> {
        val obj = JSONObject(themeJson)
        val baseId = obj.optString("id", "theme")
        val baseName = obj.optString("name", "Theme")
        var newId = "${baseId}_copy"
        var n = 2
        while (File(File(context.filesDir, DIR), "$newId.json").exists()) {
            newId = "${baseId}_copy$n"
            n++
        }
        obj.put("id", newId)
        obj.put("name", "$baseName (bản của tôi)")
        obj.put("kind", "custom")
        return newId to obj.toString()
    }
}
