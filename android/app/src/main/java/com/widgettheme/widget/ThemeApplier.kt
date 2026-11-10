package com.widgettheme.widget

import android.graphics.Color
import android.widget.RemoteViews
import com.widgettheme.R

/**
 * ThemeApplier — đổi theme đã gộp từ Rust thành lệnh RemoteViews (cách A, BA §5).
 *
 * Rust trả về ResolvedTheme (JSON) với mọi token đã có giá trị cụ thể;
 * Kotlin chỉ việc áp dụng, không tự xử lý gộp theme.
 *
 * GIỚI HẠN RemoteViews (ghi rõ theo BA):
 * - Không có font riêng (font riêng chỉ vẽ được bằng ảnh — theme-render, Giai đoạn 4).
 * - Gradient bị hạn chế; bo góc / tô màu động đầy đủ cần Android 12 (API 31).
 *   Máy cũ hơn dùng drawable XML có sẵn theo bậc bo góc 0 / 8 / 16 / 24 / 32 dp,
 *   hoặc ảnh nền do Rust vẽ (Giai đoạn 4).
 * - Mỗi widget cập nhật trong ~10 giây (BroadcastReceiver); việc nặng
 *   (vẽ ảnh nền) phải đẩy sang WorkManager — xem WidgetUpdater.
 */
object ThemeApplier {

    /** Theme đã gộp từ Rust — khớp 1-1 với `ResolvedTheme` trong theme-core. */
    data class ResolvedTheme(
        val themeId: String,
        val themeName: String,
        val background: String,
        val surface: String,
        val primary: String,
        val accent: String,
        val textPrimary: String,
        val textSecondary: String,
        val border: String,
        val titleSize: Float, // sp
        val bodySize: Float, // sp
        val captionSize: Float, // sp
        val titleBold: Boolean,
        val bodyBold: Boolean,
        val radius: Float, // dp
        val padding: Float, // dp
        val borderWidth: Float, // dp
        val opacity: Float, // 0-100 %
        val bgKind: String = "solid", // solid | gradient | image (Rust BgKind)
    )

    private fun parse(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        // Widget không bao giờ trống/vỡ: màu lỗi thì về Mặc định (E-04).
        Color.parseColor("#FFFFFF")
    }

    /**
     * Áp theme lên RemoteViews của một widget. Trả về RemoteViews đã tô màu.
     * [backgroundBitmap]: ảnh nền riêng của widget (Giai đoạn 3) — có thì
     * vẽ ảnh (centerCrop trong layout) và nền màu thành trong suốt; không
     * có thì tô màu nền như trước và ẩn ImageView.
     */
    fun apply(
        layoutRes: Int,
        theme: ResolvedTheme,
        packageName: String,
        backgroundBitmap: android.graphics.Bitmap? = null,
    ): RemoteViews {
        val views = RemoteViews(packageName, layoutRes)
        val bg = parse(theme.background)
        val text = parse(theme.textPrimary)
        val secondary = parse(theme.textSecondary)
        val primary = parse(theme.primary)

        // Nền: RemoteViews không tô màu nền động trực tiếp cho mọi view,
        // nên dùng setInt với "setBackgroundColor" trên root (API 31+ mới
        // bo góc động được; máy cũ dùng drawable theo bậc — TODO Giai đoạn 4).
        if (backgroundBitmap != null) {
            views.setImageViewBitmap(R.id.widget_background, backgroundBitmap)
            views.setViewVisibility(R.id.widget_background, android.view.View.VISIBLE)
            views.setInt(R.id.widget_root, "setBackgroundColor", 0)
        } else {
            views.setViewVisibility(R.id.widget_background, android.view.View.GONE)
            views.setInt(R.id.widget_root, "setBackgroundColor", bg)
        }
        views.setTextColor(R.id.widget_title, text)
        views.setTextColor(R.id.widget_body, secondary)
        views.setTextColor(R.id.widget_accent, primary)
        views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, theme.titleSize)
        views.setTextViewTextSize(R.id.widget_body, android.util.TypedValue.COMPLEX_UNIT_SP, theme.bodySize)
        // Độ mờ nền: RemoteViews không có setAlpha cho background;
        // opacity < 100% thì trộn màu nền với màu đen hệ thống ở lớp Rust
        // khi vẽ ảnh nền (Giai đoạn 4). Ở V1, opacity chỉ áp cho ảnh nền.
        return views
    }
}
