//! theme-core — Lõi Rust của app Theme Widget (Giai đoạn 1).
//!
//! Chứa toàn bộ logic theme, kiểm thử được trên máy tính, không cần Android:
//! - Mô hình theme theo design token ([`model`])
//! - Màu sắc, tương phản WCAG, sinh biến thể tối ([`color`])
//! - Kiểm tra hợp lệ + gộp các lớp theme theo BR-24 ([`resolve`])
//!
//! Giao diện Kotlin chỉ hiển thị kết quả; mọi quy tắc BR đều kiểm tra ở đây.
pub mod color;
pub mod error;
pub mod model;
pub mod render;
pub mod resolve;

pub use color::{
    contrast_level, contrast_ratio, generate_dark_variant, is_valid_color, parse_color,
    ContrastLevel,
};
pub use error::ThemeError;
pub use model::{
    default_theme, Background, BgKind, Colors, ResolvedTheme, Shape, Theme, ThemeKind, Typography,
    Variant, VariantMode, CURRENT_SCHEMA_VERSION, DEFAULT_THEME_ID, MAX_INHERITANCE_DEPTH,
};
pub use render::render_resolved_background;
pub use resolve::{
    inheritance_chain, load_themes_from_dir, resolve_theme, theme_from_json, validate_theme,
    Warning,
};

#[cfg(test)]
mod tests;
