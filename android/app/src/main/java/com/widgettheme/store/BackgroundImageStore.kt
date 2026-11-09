package com.widgettheme.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Ảnh nền riêng từng widget (Giai đoạn 3, Bước 1; Tony chốt 22:11 09/11:
 * ảnh nền chọn lúc thêm widget, không gắn theo theme).
 *
 * - Mỗi widget một file: filesDir/widget_backgrounds/<appWidgetId>.jpg.
 * - Ảnh gốc được thu nhỏ còn tối đa 640px cạnh dài, nén JPEG q80, để
 *   vừa giới hạn giao dịch Binder của RemoteViews (~1 MB): decode lại
 *   bằng RGB_565 (2 byte/điểm ảnh) nên bitmap vuông 640px chỉ ~800 KB,
 *   widget thường là hình chữ nhật ngang nên còn nhỏ hơn nhiều.
 * - Không có ảnh thì không có file — WidgetThemeStore.removeWidget
 *   (BR-18) xóa file này cùng lúc với liên kết theme.
 * - Lỗi bất kỳ (đọc URI, decode, ghi file) thì trả false/null, widget
 *   rơi về nền màu — không bao giờ crash (E-04).
 */
object BackgroundImageStore {

    private const val MAX_DIMENSION = 640
    private const val JPEG_QUALITY = 80

    private fun dir(context: Context): File =
        File(context.filesDir, "widget_backgrounds").also { it.mkdirs() }

    private fun file(context: Context, appWidgetId: Int): File =
        File(dir(context), "$appWidgetId.jpg")

    fun hasImage(context: Context, appWidgetId: Int): Boolean =
        file(context, appWidgetId).exists()

    /**
     * Lưu ảnh từ [uri] (ảnh người dùng chọn) thành file của widget.
     * Trả về true nếu lưu được.
     */
    fun save(context: Context, appWidgetId: Int, uri: Uri): Boolean = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return false
        var sample = 1
        while (maxOf(w, h) / sample > MAX_DIMENSION) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return false
        // Thu nhỏ chính xác về <= 640px nếu inSampleSize còn thừa.
        val scaled = scaleDown(decoded, MAX_DIMENSION)
        FileOutputStream(file(context, appWidgetId)).use { out ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return false
        }
        if (scaled !== decoded) decoded.recycle()
        true
    }.getOrDefault(false)

    /** Đọc bitmap đã lưu để vẽ lên widget (RGB_565, đã thu nhỏ lúc lưu). */
    fun load(context: Context, appWidgetId: Int): Bitmap? = runCatching {
        val f = file(context, appWidgetId)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(f.absolutePath, opts)
    }.getOrNull()

    fun delete(context: Context, appWidgetId: Int) {
        runCatching { file(context, appWidgetId).delete() }
    }

    private fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val (w, h) = src.width to src.height
        val longest = maxOf(w, h)
        if (longest <= maxDim) return src
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }
}
