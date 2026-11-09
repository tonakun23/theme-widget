//! Màu sắc: đọc mã màu (BR-06), tính tương phản WCAG (BR-08),
//! sinh biến thể tối từ biến thể sáng (BR-16).
use crate::model::{BgKind, Colors, Variant};

/// Kiểm tra mã màu đúng định dạng #RGB, #RRGGBB hoặc #RRGGBBAA (BR-06).
pub fn is_valid_color(s: &str) -> bool {
    parse_color(s).is_some()
}

/// Đọc mã màu thành (R, G, B, A). Trả `None` khi sai định dạng.
pub fn parse_color(s: &str) -> Option<(u8, u8, u8, u8)> {
    let hex = s.strip_prefix('#')?;
    let bytes = hex.as_bytes();
    let all_hex = bytes.iter().all(|b| b.is_ascii_hexdigit());
    if !all_hex {
        return None;
    }
    let expand = |c: u8| -> u8 {
        let v = (c as char).to_digit(16).unwrap() as u8;
        (v << 4) | v
    };
    let pair = |i: usize| -> Option<u8> { u8::from_str_radix(&hex[i..i + 2], 16).ok() };
    match hex.len() {
        3 => Some((expand(bytes[0]), expand(bytes[1]), expand(bytes[2]), 255)),
        6 => Some((pair(0)?, pair(2)?, pair(4)?, 255)),
        8 => Some((pair(0)?, pair(2)?, pair(4)?, pair(6)?)),
        _ => None,
    }
}

pub fn to_hex(r: u8, g: u8, b: u8) -> String {
    format!("#{r:02X}{g:02X}{b:02X}")
}

fn linear(c: f32) -> f32 {
    if c <= 0.03928 {
        c / 12.92
    } else {
        ((c + 0.055) / 1.055).powf(2.4)
    }
}

/// Độ sáng tương đối WCAG của một màu (0.0–1.0).
pub fn relative_luminance(hex: &str) -> Option<f32> {
    let (r, g, b, _) = parse_color(hex)?;
    let (r, g, b) = (r as f32 / 255.0, g as f32 / 255.0, b as f32 / 255.0);
    Some(0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b))
}

/// Tỷ lệ tương phản WCAG giữa chữ và nền (1.0–21.0).
pub fn contrast_ratio(text_hex: &str, bg_hex: &str) -> Option<f32> {
    let l1 = relative_luminance(text_hex)?;
    let l2 = relative_luminance(bg_hex)?;
    let (hi, lo) = if l1 > l2 { (l1, l2) } else { (l2, l1) };
    Some((hi + 0.05) / (lo + 0.05))
}

/// Mức cảnh báo tương phản (BR-08): dưới 4.5:1 cảnh báo vàng,
/// dưới 3:1 cảnh báo đỏ. Vẫn cho lưu.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ContrastLevel {
    Ok,
    Yellow,
    Red,
}

pub fn contrast_level(ratio: f32) -> ContrastLevel {
    if ratio < 3.0 {
        ContrastLevel::Red
    } else if ratio < 4.5 {
        ContrastLevel::Yellow
    } else {
        ContrastLevel::Ok
    }
}

/// Làm tối một màu (trộn về đen theo hệ số 0.0–1.0).
fn darken(hex: &str, factor: f32) -> Option<String> {
    let (r, g, b, _) = parse_color(hex)?;
    Some(to_hex(
        (r as f32 * (1.0 - factor)) as u8,
        (g as f32 * (1.0 - factor)) as u8,
        (b as f32 * (1.0 - factor)) as u8,
    ))
}

/// Làm sáng một màu (trộn về trắng theo hệ số 0.0–1.0).
fn lighten(hex: &str, factor: f32) -> Option<String> {
    let (r, g, b, _) = parse_color(hex)?;
    Some(to_hex(
        (r as f32 + (255.0 - r as f32) * factor) as u8,
        (g as f32 + (255.0 - g as f32) * factor) as u8,
        (b as f32 + (255.0 - b as f32) * factor) as u8,
    ))
}

/// Sinh biến thể Tối từ biến thể Sáng (BR-16): nền tối lại mạnh,
/// chữ sáng lên, màu chủ đạo giữ sắc nhưng tối vừa phải.
pub fn generate_dark_variant(light: &Variant) -> Variant {
    let map = |c: &Option<String>, dark_fn: fn(&str, f32) -> Option<String>, f: f32| {
        c.as_ref().and_then(|hex| dark_fn(hex, f))
    };
    let mut dark = light.clone();
    dark.colors.background = map(&light.colors.background, darken, 0.88);
    dark.colors.surface = map(&light.colors.surface, darken, 0.86);
    dark.colors.border = map(&light.colors.border, darken, 0.82);
    dark.colors.primary = map(&light.colors.primary, darken, 0.25);
    dark.colors.accent = map(&light.colors.accent, darken, 0.15);
    // Chữ: nếu chữ sáng nhạt thì tối lại ít, chữ tối thì sáng lên mạnh.
    let text_dark = |hex: &str| -> Option<String> {
        let lum = relative_luminance(hex).unwrap_or(0.0);
        if lum > 0.5 {
            darken(hex, 0.85)
        } else {
            lighten(hex, 0.88)
        }
    };
    dark.colors.text_primary = light
        .colors
        .text_primary
        .as_ref()
        .and_then(|h| text_dark(h));
    dark.colors.text_secondary = light
        .colors
        .text_secondary
        .as_ref()
        .and_then(|h| text_dark(h));
    dark.background.color = map(&light.background.color, darken, 0.88);
    dark.background.kind = light.background.kind.or(Some(BgKind::Solid));
    // Ghi chú: `Colors` không có trường nào khác cần đổi; giữ nguyên
    // typography/shape từ bản sáng.
    let _ = &dark.colors;
    dark
}

/// Kiểm tra tương phản của một biến thể đã gộp xong: trả về tỷ lệ
/// chữ chính/nền, hoặc `None` khi thiếu token màu.
pub fn variant_contrast(colors: &Colors) -> Option<f32> {
    let text = colors.text_primary.as_ref()?;
    let bg = colors.background.as_ref()?;
    contrast_ratio(text, bg)
}
