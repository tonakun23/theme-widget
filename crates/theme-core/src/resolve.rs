//! Gộp các lớp theme (BR-24) và kiểm tra hợp lệ (BR-06..BR-09).
//!
//! Thứ tự ưu tiên tăng dần:
//! Mặc định → Theme cha → Theme đã chọn → Biến thể sáng/tối
//! → Màu hệ thống → Ghi đè của widget.
use crate::color::{
    contrast_level, contrast_ratio, generate_dark_variant, is_valid_color, ContrastLevel,
};
use crate::error::ThemeError;
use crate::model::{
    default_theme, BgKind, Colors, ResolvedTheme, Theme, Variant, VariantMode,
    CURRENT_SCHEMA_VERSION, DEFAULT_THEME_ID, MAX_INHERITANCE_DEPTH,
};
use std::collections::{HashMap, HashSet};

/// Một cảnh báo kiểm tra (không chặn lưu, chỉ hiển thị — BR-08, W-01).
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct Warning {
    pub code: &'static str,
    pub level: ContrastLevel,
    pub message: String,
}

/// Kiểm tra một theme: màu đúng định dạng (BR-06), schema được hỗ trợ.
/// Trả về danh sách cảnh báo (tương phản thấp...); lỗi thì `Err`.
pub fn validate_theme(theme: &Theme) -> Result<Vec<Warning>, ThemeError> {
    if theme.schema_version > CURRENT_SCHEMA_VERSION {
        return Err(ThemeError::SchemaVersionTooNew(theme.schema_version));
    }
    if theme.name.trim().is_empty() || theme.name.chars().count() > 40 {
        return Err(ThemeError::InvalidName);
    }
    let mut warnings = Vec::new();
    for (label, variant) in [("light", &theme.light)]
        .into_iter()
        .chain(theme.dark.as_ref().map(|d| ("dark", d)))
    {
        validate_variant_colors(label, variant, &mut warnings)?;
    }
    Ok(warnings)
}

fn validate_variant_colors(
    label: &str,
    v: &Variant,
    warnings: &mut Vec<Warning>,
) -> Result<(), ThemeError> {
    let colors = [
        ("background", &v.colors.background),
        ("surface", &v.colors.surface),
        ("primary", &v.colors.primary),
        ("accent", &v.colors.accent),
        ("text.primary", &v.colors.text_primary),
        ("text.secondary", &v.colors.text_secondary),
        ("border", &v.colors.border),
    ];
    for (name, c) in colors {
        if let Some(hex) = c {
            if !is_valid_color(hex) {
                return Err(ThemeError::InvalidColor(format!("{label}.{name} = {hex}")));
            }
        }
    }
    if let Some(bg) = &v.background.color {
        if !is_valid_color(bg) {
            return Err(ThemeError::InvalidColor(format!(
                "{label}.background.color = {bg}"
            )));
        }
    }
    // Cảnh báo tương phản chữ chính / nền (BR-08, W-01).
    if let (Some(text), Some(bg)) = (&v.colors.text_primary, &v.colors.background) {
        if let Some(ratio) = contrast_ratio(text, bg) {
            let level = contrast_level(ratio);
            if level != ContrastLevel::Ok {
                let (code, msg) = if level == ContrastLevel::Red {
                    (
                        "W-01-red",
                        format!(
                        "Biến thể {label}: tương phản chữ/nền chỉ {ratio:.1}:1 — chữ có thể khó đọc"
                    ),
                    )
                } else {
                    (
                        "W-01-yellow",
                        format!(
                            "Biến thể {label}: tương phản chữ/nền {ratio:.1}:1, dưới chuẩn 4.5:1"
                        ),
                    )
                };
                warnings.push(Warning {
                    code,
                    level,
                    message: msg,
                });
            }
        }
    }
    Ok(())
}

/// Đi theo chuỗi `extends` từ theme lên gốc. Trả về danh sách theme
/// từ gốc xuống lá (đã gồm chính theme đó). Kiểm tra vòng lặp và
/// độ sâu tối đa 3 cấp (BR-09).
pub fn inheritance_chain<'a>(
    registry: &'a HashMap<String, Theme>,
    theme: &'a Theme,
) -> Result<Vec<&'a Theme>, ThemeError> {
    let mut chain: Vec<&Theme> = vec![theme];
    let mut seen: HashSet<&str> = HashSet::from([theme.id.as_str()]);
    let mut current = theme;
    while let Some(parent_id) = &current.extends {
        if !seen.insert(parent_id.as_str()) {
            return Err(ThemeError::InheritanceCycle(parent_id.clone()));
        }
        let parent = registry
            .get(parent_id)
            .ok_or_else(|| ThemeError::ParentNotFound(parent_id.clone()))?;
        chain.push(parent);
        current = parent;
        // Số cạnh extends = chain.len() - 1; tối đa 3 (BR-09).
        if chain.len() - 1 > MAX_INHERITANCE_DEPTH {
            return Err(ThemeError::InheritanceTooDeep);
        }
    }
    chain.reverse(); // gốc trước, lá sau
    Ok(chain)
}

/// Chọn biến thể theo chế độ: thiếu biến thể tối thì tự sinh từ sáng (BR-16).
fn variant_for_mode(theme: &Theme, mode: VariantMode) -> Variant {
    match mode {
        VariantMode::Light => theme.light.clone(),
        VariantMode::Dark => theme
            .dark
            .clone()
            .unwrap_or_else(|| generate_dark_variant(&theme.light)),
    }
}

/// Gộp theme thành [`ResolvedTheme`] theo BR-24.
///
/// * `registry` — mọi theme đã biết (gồm cả theme "Mặc định").
/// * `mode` — Sáng / Tối / Theo hệ thống (khi đó caller truyền mode đã tính).
/// * `system` — bảng màu hệ thống Material You (Android 12+); chỉ áp cho
///   token có cờ `follow_system` (BR-17).
/// * `overrides` — ghi đè riêng của một widget instance (BR-12), ưu tiên cao nhất.
pub fn resolve_theme(
    registry: &HashMap<String, Theme>,
    theme_id: &str,
    mode: VariantMode,
    system: Option<&Colors>,
    overrides: Option<&Variant>,
) -> Result<ResolvedTheme, ThemeError> {
    let theme = registry
        .get(theme_id)
        .ok_or_else(|| ThemeError::ThemeNotFound(theme_id.to_string()))?;
    validate_theme(theme)?;

    // Lớp nền: theme Mặc định (BR-24). Nếu chính theme đang gộp là
    // Mặc định thì không gộp trùng.
    let mut merged = if theme_id == DEFAULT_THEME_ID {
        variant_for_mode(theme, mode)
    } else {
        let def = registry
            .get(DEFAULT_THEME_ID)
            .cloned()
            .unwrap_or_else(default_theme);
        variant_for_mode(&def, mode)
    };

    // Theme cha → theme đã chọn (gốc trước, lá sau).
    for ancestor in inheritance_chain(registry, theme)? {
        merged = merged.merge(&variant_for_mode(ancestor, mode));
    }

    // Màu hệ thống (BR-17): chỉ khi theme bật follow_system.
    if merged.colors.follow_system {
        if let Some(sys) = system {
            let c = &mut merged.colors;
            if sys.background.is_some() {
                c.background = sys.background.clone();
            }
            if sys.surface.is_some() {
                c.surface = sys.surface.clone();
            }
            if sys.primary.is_some() {
                c.primary = sys.primary.clone();
            }
            if sys.accent.is_some() {
                c.accent = sys.accent.clone();
            }
            if sys.text_primary.is_some() {
                c.text_primary = sys.text_primary.clone();
            }
            if sys.text_secondary.is_some() {
                c.text_secondary = sys.text_secondary.clone();
            }
            if sys.border.is_some() {
                c.border = sys.border.clone();
            }
        }
    }

    // Ghi đè widget — ưu tiên cao nhất (BR-12).
    if let Some(ov) = overrides {
        merged = merged.merge(ov);
    }

    merged.clamp();
    Ok(fill_defaults(theme, mode, merged))
}

/// Điền token còn thiếu từ theme Mặc định để có theme đầy đủ.
fn fill_defaults(theme: &Theme, mode: VariantMode, merged: Variant) -> ResolvedTheme {
    let def = variant_for_mode(&default_theme(), mode);
    let c = merged.colors;
    let dc = def.colors;
    let t = merged.typography;
    let dt = def.typography;
    let s = merged.shape;
    let ds = def.shape;
    let b = merged.background;
    let db = def.background;
    ResolvedTheme {
        theme_id: theme.id.clone(),
        theme_name: theme.name.clone(),
        mode,
        background: c
            .background
            .or(dc.background)
            .unwrap_or_else(|| "#FFFFFF".into()),
        surface: c.surface.or(dc.surface).unwrap_or_else(|| "#F2F2F7".into()),
        primary: c.primary.or(dc.primary).unwrap_or_else(|| "#3366FF".into()),
        accent: c.accent.or(dc.accent).unwrap_or_else(|| "#FF8A00".into()),
        text_primary: c
            .text_primary
            .or(dc.text_primary)
            .unwrap_or_else(|| "#1A1A1A".into()),
        text_secondary: c
            .text_secondary
            .or(dc.text_secondary)
            .unwrap_or_else(|| "#6B7280".into()),
        border: c.border.or(dc.border).unwrap_or_else(|| "#E5E7EB".into()),
        title_size: t.title_size.or(dt.title_size).unwrap_or(20.0),
        body_size: t.body_size.or(dt.body_size).unwrap_or(14.0),
        caption_size: t.caption_size.or(dt.caption_size).unwrap_or(12.0),
        title_bold: t.title_bold.or(dt.title_bold).unwrap_or(true),
        body_bold: t.body_bold.or(dt.body_bold).unwrap_or(false),
        radius: s.radius.or(ds.radius).unwrap_or(16.0),
        padding: s.padding.or(ds.padding).unwrap_or(12.0),
        border_width: s.border_width.or(ds.border_width).unwrap_or(0.0),
        bg_kind: b.kind.or(db.kind).unwrap_or(BgKind::Solid),
        bg_color: b.color.or(db.color).unwrap_or_else(|| "#FFFFFF".into()),
        opacity: b.opacity.or(db.opacity).unwrap_or(100.0),
    }
}

/// Đọc một theme từ chuỗi JSON (kiểm tra schema_version, BR-21).
pub fn theme_from_json(json: &str) -> Result<Theme, ThemeError> {
    let theme: Theme =
        serde_json::from_str(json).map_err(|e| ThemeError::InvalidThemeFile(e.to_string()))?;
    if theme.schema_version > CURRENT_SCHEMA_VERSION {
        return Err(ThemeError::SchemaVersionTooNew(theme.schema_version));
    }
    Ok(theme)
}

/// Đọc mọi theme JSON trong một thư mục (thư viện `themes/`).
pub fn load_themes_from_dir(dir: &std::path::Path) -> Result<Vec<Theme>, ThemeError> {
    let mut themes = Vec::new();
    let entries = std::fs::read_dir(dir).map_err(ThemeError::from)?;
    for entry in entries {
        let entry = entry.map_err(ThemeError::from)?;
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) == Some("json") {
            let text = std::fs::read_to_string(&path)?;
            let theme = theme_from_json(&text)
                .map_err(|e| ThemeError::InvalidThemeFile(format!("{}: {e}", path.display())))?;
            themes.push(theme);
        }
    }
    themes.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(themes)
}
