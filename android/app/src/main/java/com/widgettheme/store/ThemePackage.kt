package com.widgettheme.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024

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

    /**
     * Đọc `theme.json` từ một gói .zip (Giai đoạn 5, Bước 2 — nhập theme).
     * Chỉ nhận gói do app này xuất ra: `manifest.json` phải có đúng
     * `format` + `version`, và phải có mục `theme.json`. File lạ, thiếu
     * manifest, sai định dạng hoặc quá lớn (> 2 MB/mục) thì trả `null` —
     * phía gọi báo lỗi, không lưu gì cả. Chỉ đọc chuỗi JSON trong bộ nhớ,
     * không giải nén file ra đĩa nên không sợ đường dẫn lạ trong zip.
     */
    fun readThemeJsonFromPackage(context: Context, uri: Uri): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            var manifestOk = false
            var themeJson: String? = null
            ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val bytes = readEntryCapped(zip, MAX_ENTRY_BYTES)
                        if (bytes != null) {
                            when (entry.name) {
                                MANIFEST_ENTRY -> {
                                    val manifest = runCatching {
                                        JSONObject(String(bytes, Charsets.UTF_8))
                                    }.getOrNull()
                                    manifestOk = manifest != null &&
                                        manifest.optString("format", "") == FORMAT &&
                                        manifest.optInt("version", -1) == PACKAGE_VERSION
                                }
                                THEME_ENTRY -> themeJson =
                                    String(bytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (manifestOk) themeJson else null
        } catch (e: Exception) {
            null
        }
    }

    /** Đọc một mục zip, trả `null` khi mục vượt giới hạn (từ chối sớm). */
    private fun readEntryCapped(zip: ZipInputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
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
