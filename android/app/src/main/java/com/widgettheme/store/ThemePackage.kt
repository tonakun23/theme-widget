package com.widgettheme.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gói theme nhập/xuất (Giai đoạn 5, BR-22).
 *
 * Định dạng gói: file .zip chứa 2 mục:
 * - `manifest.json`: `{"format":"theme-widget-package","version":1}` —
 *   để phía nhập nhận diện đúng gói theme, từ chối file lạ sớm.
 * - `theme.json`: theme JSON đầy đủ (đã kiểm tra qua Rust khi nhập).
 */
object ThemePackage {
    const val FORMAT = "theme-widget-package"
    const val PACKAGE_VERSION = 1
    const val THEME_ENTRY = "theme.json"
    const val MANIFEST_ENTRY = "manifest.json"

    /** Xuất theme thành file .zip trong cache, trả Uri chia sẻ được; lỗi thì null. */
    fun exportTheme(context: Context, themeJson: String): Uri? {
        return try {
            val id = themeId(themeJson)
            val dir = File(context.cacheDir, "theme-packages").apply { mkdirs() }
            val file = File(dir, "$id.zip")
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(
                    """{"format":"$FORMAT","version":$PACKAGE_VERSION}"""
                        .toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(THEME_ENTRY))
                zip.write(themeJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /** Mở share sheet với file gói theme; false khi xuất lỗi. */
    fun shareTheme(context: Context, themeJson: String): Boolean {
        val uri = exportTheme(context, themeJson) ?: return false
        val name = themeName(themeJson)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Theme Widget: $name")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ theme \"$name\""))
        return true
    }

    internal fun themeId(themeJson: String): String =
        runCatching { JSONObject(themeJson).optString("id", "theme") }
            .getOrNull().orEmpty().ifBlank { "theme" }

    internal fun themeName(themeJson: String): String =
        runCatching { JSONObject(themeJson).optString("name", "theme") }
            .getOrNull().orEmpty().ifBlank { "theme" }
}
