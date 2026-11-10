//! Render nền theme thành ảnh PNG (Giai đoạn 4, theme-render).
//!
//! RemoteViews không vẽ được gradient trực tiếp, nên Rust vẽ nền thành
//! ảnh rồi Kotlin đặt làm bitmap cho ImageView của widget (cách đã dùng
//! cho ảnh nền riêng ở Giai đoạn 3).
//!
//! - Chỉ vẽ khi `bg_kind` là Gradient; màu trơn (Solid) Kotlin tô trực
//!   tiếp, ảnh (Image) là ảnh nền riêng của widget — cả hai đều trả `None`.
//! - Gradient: dọc, từ màu nền (trên) tới màu mặt (dưới), bo góc theo
//!   `radius` của theme, góc trong suốt; độ mờ `opacity` áp cho toàn ảnh.

use crate::{BgKind, ResolvedTheme};

/// Kích thước ảnh tối đa (px) để chặn theme độc hại đòi render ảnh khổng lồ.
const MAX_DIM: u32 = 1024;

/// Render nền của theme đã gộp thành PNG (RGBA).
/// Trả `None` khi nền không phải gradient.
pub fn render_resolved_background(
    resolved: &ResolvedTheme,
    width: u32,
    height: u32,
    radius_px: u32,
) -> Option<Vec<u8>> {
    if resolved.bg_kind != BgKind::Gradient {
        return None;
    }
    let width = width.clamp(1, MAX_DIM);
    let height = height.clamp(1, MAX_DIM);
    let radius = radius_px.min(width / 2).min(height / 2);
    let top = parse_rgb(&resolved.bg_color);
    let bottom = parse_rgb(&resolved.surface);
    let alpha = (resolved.opacity.clamp(0.0, 100.0) / 100.0 * 255.0).round() as u8;

    let mut img = image::RgbaImage::new(width, height);
    for y in 0..height {
        let t = if height > 1 {
            y as f32 / (height - 1) as f32
        } else {
            0.0
        };
        let (r, g, b) = lerp(top, bottom, t);
        for x in 0..width {
            // Bo góc có khử răng cưa: hệ số phủ từ khoảng cách tới tâm góc.
            let cover = corner_coverage(x, y, width, height, radius);
            let a = ((alpha as f32) * cover).round() as u8;
            img.put_pixel(x, y, image::Rgba([r, g, b, a]));
        }
    }
    let mut buf = Vec::new();
    let dyn_img = image::DynamicImage::ImageRgba8(img);
    dyn_img
        .write_to(&mut std::io::Cursor::new(&mut buf), image::ImageFormat::Png)
        .ok()?;
    Some(buf)
}

/// Đọc mã màu #RGB / #RRGGBB / #RRGGBBAA; lỗi thì trắng.
fn parse_rgb(hex: &str) -> (u8, u8, u8) {
    crate::parse_color(hex).map_or((255, 255, 255), |(r, g, b, _)| (r, g, b))
}

fn lerp(a: (u8, u8, u8), b: (u8, u8, u8), t: f32) -> (u8, u8, u8) {
    let f = |x: u8, y: u8| (x as f32 + (y as f32 - x as f32) * t).round() as u8;
    (f(a.0, b.0), f(a.1, b.1), f(a.2, b.2))
}

/// Hệ số phủ (0..1) của một điểm ảnh trong hình chữ nhật bo góc.
/// 1 = trong hẳn, 0 = ngoài hẳn, giữa là mép bo được khử răng cưa.
fn corner_coverage(x: u32, y: u32, w: u32, h: u32, r: u32) -> f32 {
    if r == 0 {
        return 1.0;
    }
    let (fx, fy) = (x as f32 + 0.5, y as f32 + 0.5);
    // Vùng giữa (không chạm góc) luôn phủ kín.
    if fx >= r as f32 && fx < (w - r) as f32 {
        return 1.0;
    }
    if fy >= r as f32 && fy < (h - r) as f32 {
        return 1.0;
    }
    // Góc: tâm bo ở (r, r) / (w-r, r) / (r, h-r) / (w-r, h-r).
    let cx = if fx < r as f32 { r as f32 } else { (w - r) as f32 };
    let cy = if fy < r as f32 { r as f32 } else { (h - r) as f32 };
    let dist = ((fx - cx).powi(2) + (fy - cy).powi(2)).sqrt();
    (r as f32 + 0.5 - dist).clamp(0.0, 1.0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::default_theme;

    fn gradient_theme() -> ResolvedTheme {
        let mut theme = default_theme();
        theme.light.background.kind = Some(BgKind::Gradient);
        theme.light.background.color = Some("#3366FF".to_string());
        theme.light.colors.surface = Some("#FFFFFF".to_string());
        let registry = std::collections::HashMap::from([
            (crate::DEFAULT_THEME_ID.to_string(), default_theme()),
            (theme.id.clone(), theme),
        ]);
        crate::resolve_theme(
            &registry,
            crate::DEFAULT_THEME_ID,
            crate::VariantMode::Light,
            None,
            None,
        )
        .unwrap()
    }

    #[test]
    fn solid_theme_renders_nothing() {
        let resolved = crate::resolve_theme(
            &std::collections::HashMap::from([(
                crate::DEFAULT_THEME_ID.to_string(),
                default_theme(),
            )]),
            crate::DEFAULT_THEME_ID,
            crate::VariantMode::Light,
            None,
            None,
        )
        .unwrap();
        assert_eq!(resolved.bg_kind, BgKind::Solid);
        assert!(render_resolved_background(&resolved, 100, 60, 16).is_none());
    }

    #[test]
    fn gradient_renders_png_with_right_size_and_colors() {
        let resolved = gradient_theme();
        assert_eq!(resolved.bg_kind, BgKind::Gradient);
        let png = render_resolved_background(&resolved, 100, 60, 0).unwrap();
        assert!(png.starts_with(&[0x89, b'P', b'N', b'G']));
        let img = image::load_from_memory(&png).unwrap().to_rgba8();
        assert_eq!(img.dimensions(), (100, 60));
        // Hàng trên ~ màu nền (#3366FF), hàng dưới ~ màu mặt (#FFFFFF).
        let top = img.get_pixel(50, 0).0;
        let bottom = img.get_pixel(50, 59).0;
        assert_eq!((top[0], top[1], top[2]), (0x33, 0x66, 0xFF));
        assert_eq!((bottom[0], bottom[1], bottom[2]), (0xFF, 0xFF, 0xFF));
        assert_eq!(top[3], 255);
    }

    #[test]
    fn rounded_corners_are_transparent() {
        let resolved = gradient_theme();
        let png = render_resolved_background(&resolved, 100, 60, 16).unwrap();
        let img = image::load_from_memory(&png).unwrap().to_rgba8();
        assert_eq!(img.get_pixel(0, 0).0[3], 0);
        assert_eq!(img.get_pixel(50, 30).0[3], 255);
    }
}
