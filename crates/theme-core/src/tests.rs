//! Unit test cho theme-core: gộp theme, kẹp giá trị, màu sai,
//! vòng lặp extends, undo không nằm ở đây (xem theme-editor),
//! sinh biến thể tối, tương phản.
use super::*;
use crate::color::contrast_level;
use crate::model::{BgKind, Colors, Variant};
use std::collections::HashMap;

fn registry_with(themes: Vec<Theme>) -> HashMap<String, Theme> {
    let mut reg = HashMap::new();
    reg.insert(DEFAULT_THEME_ID.to_string(), default_theme());
    for t in themes {
        reg.insert(t.id.clone(), t);
    }
    reg
}

fn theme_with_light(id: &str, bg: &str, primary: &str) -> Theme {
    let mut t = Theme {
        id: id.to_string(),
        name: id.to_string(),
        kind: ThemeKind::Custom,
        ..Theme::default()
    };
    t.light.colors.background = Some(bg.to_string());
    t.light.colors.primary = Some(primary.to_string());
    t
}

#[test]
fn resolve_fills_all_tokens_from_default() {
    // Theme chỉ đặt 1 token -> gộp xong vẫn đủ token (lấy từ Mặc định).
    let reg = registry_with(vec![theme_with_light("t1", "#FFF8E1", "#3366FF")]);
    let r = resolve_theme(&reg, "t1", VariantMode::Light, None, None).unwrap();
    assert_eq!(r.background, "#FFF8E1");
    assert_eq!(r.primary, "#3366FF");
    assert_eq!(r.surface, "#F2F2F7"); // từ Mặc định
    assert_eq!(r.title_size, 20.0);
    assert_eq!(r.radius, 16.0);
    assert_eq!(r.opacity, 100.0);
    assert_eq!(r.bg_kind, BgKind::Solid);
}

#[test]
fn later_layer_wins() {
    // Lớp sau thắng lớp trước (BR-24): theme con thắng theme cha.
    let mut parent = theme_with_light("parent", "#111111", "#0000FF");
    parent.light.colors.text_primary = Some("#EEEEEE".to_string());
    let mut child = theme_with_light("child", "#222222", "#00FF00");
    child.extends = Some("parent".to_string());
    let reg = registry_with(vec![parent, child]);
    let r = resolve_theme(&reg, "child", VariantMode::Light, None, None).unwrap();
    assert_eq!(r.background, "#222222"); // con thắng cha
    assert_eq!(r.primary, "#00FF00");
    assert_eq!(r.text_primary, "#EEEEEE"); // con không đặt -> lấy từ cha
}

#[test]
fn overrides_win_over_everything() {
    let reg = registry_with(vec![theme_with_light("t1", "#FFF8E1", "#3366FF")]);
    let mut ov = Variant::default();
    ov.colors.primary = Some("#FF0000".to_string());
    let r = resolve_theme(&reg, "t1", VariantMode::Light, None, Some(&ov)).unwrap();
    assert_eq!(r.primary, "#FF0000");
}

#[test]
fn system_colors_apply_only_when_follow_system() {
    let mut t = theme_with_light("t1", "#FFF8E1", "#3366FF");
    t.light.colors.follow_system = true;
    let reg = registry_with(vec![t]);
    let sys = Colors {
        primary: Some("#00BCD4".to_string()),
        background: Some("#E0F7FA".to_string()),
        ..Default::default()
    };
    let r = resolve_theme(&reg, "t1", VariantMode::Light, Some(&sys), None).unwrap();
    assert_eq!(r.primary, "#00BCD4");
    assert_eq!(r.background, "#E0F7FA");

    // Không bật follow_system -> màu hệ thống bị bỏ qua.
    let t2 = theme_with_light("t2", "#FFF8E1", "#3366FF");
    let reg2 = registry_with(vec![t2]);
    let r2 = resolve_theme(&reg2, "t2", VariantMode::Light, Some(&sys), None).unwrap();
    assert_eq!(r2.primary, "#3366FF");
}

#[test]
fn clamp_out_of_range_values() {
    let mut v = Variant::default();
    v.shape.radius = Some(99.0);
    v.shape.padding = Some(-5.0);
    v.shape.border_width = Some(10.0);
    v.typography.title_size = Some(100.0);
    v.background.opacity = Some(150.0);
    assert_eq!(v.clamp(), 5);
    assert_eq!(v.shape.radius, Some(32.0));
    assert_eq!(v.shape.padding, Some(0.0));
    assert_eq!(v.shape.border_width, Some(4.0));
    assert_eq!(v.typography.title_size, Some(40.0));
    assert_eq!(v.background.opacity, Some(100.0));
}

#[test]
fn invalid_color_is_rejected() {
    for bad in ["#GGGGGG", "3366FF", "#12", "#12345", "red", ""] {
        assert!(!is_valid_color(bad), "{bad} phải bị từ chối");
    }
    for good in ["#3366FF", "#36F", "#3366FFAA", "#fff"] {
        assert!(is_valid_color(good), "{good} phải được chấp nhận");
    }
    let mut t = theme_with_light("t1", "not-a-color", "#3366FF");
    t.light.colors.background = Some("not-a-color".to_string());
    assert!(matches!(
        validate_theme(&t),
        Err(ThemeError::InvalidColor(_))
    ));
}

#[test]
fn inheritance_cycle_is_rejected() {
    let mut a = theme_with_light("a", "#111111", "#0000FF");
    a.extends = Some("b".to_string());
    let mut b = theme_with_light("b", "#222222", "#00FF00");
    b.extends = Some("a".to_string());
    let reg = registry_with(vec![a, b]);
    let err = resolve_theme(&reg, "a", VariantMode::Light, None, None).unwrap_err();
    assert!(matches!(err, ThemeError::InheritanceCycle(_)));
}

#[test]
fn inheritance_too_deep_is_rejected() {
    // Chuỗi 5 theme: t4 -> t3 -> t2 -> t1 -> default? Không: extends
    // t0->t1->t2->t3->t4 là 4 cạnh, vượt 3 cấp.
    let mut themes = Vec::new();
    for i in 0..5 {
        let mut t = theme_with_light(&format!("t{i}"), "#111111", "#0000FF");
        if i > 0 {
            t.extends = Some(format!("t{}", i - 1));
        }
        themes.push(t);
    }
    let reg = registry_with(themes);
    let err = resolve_theme(&reg, "t4", VariantMode::Light, None, None).unwrap_err();
    assert!(matches!(err, ThemeError::InheritanceTooDeep));
}

#[test]
fn missing_parent_is_rejected() {
    let mut t = theme_with_light("t1", "#111111", "#0000FF");
    t.extends = Some("ghost".to_string());
    let reg = registry_with(vec![t]);
    let err = resolve_theme(&reg, "t1", VariantMode::Light, None, None).unwrap_err();
    assert!(matches!(err, ThemeError::ParentNotFound(_)));
}

#[test]
fn dark_variant_is_generated_when_missing() {
    // Theme chỉ có biến thể sáng -> chế độ tối vẫn gộp được (BR-16).
    let mut t1 = theme_with_light("t1", "#FFFFFF", "#3366FF");
    t1.light.colors.text_primary = Some("#1A1A1A".to_string());
    let reg = registry_with(vec![t1]);
    let r = resolve_theme(&reg, "t1", VariantMode::Dark, None, None).unwrap();
    assert_eq!(r.mode, VariantMode::Dark);
    // Nền tối phải tối hơn nền sáng (độ sáng < 0.2).
    let lum = crate::color::relative_luminance(&r.background).unwrap();
    assert!(lum < 0.2, "nền tối phải tối, got lum {lum}");
    // Chữ tối phải sáng (đọc được trên nền tối).
    let text_lum = crate::color::relative_luminance(&r.text_primary).unwrap();
    assert!(text_lum > 0.5, "chữ tối phải sáng, got lum {text_lum}");
}

#[test]
fn contrast_levels_match_br08() {
    // Đen trên trắng ~21:1 -> Ok.
    let ratio = contrast_ratio("#000000", "#FFFFFF").unwrap();
    assert!(ratio > 20.0);
    assert_eq!(contrast_level(ratio), ContrastLevel::Ok);
    // #777 trên trắng ~4.5:1 biên -> Yellow hoặc Ok tùy làm tròn.
    let ratio2 = contrast_ratio("#777777", "#FFFFFF").unwrap();
    assert!(ratio2 < 4.5, "ratio2={ratio2}");
    assert_eq!(contrast_level(ratio2), ContrastLevel::Yellow);
    // #AAAAAA trên trắng < 3:1 -> Red.
    let ratio3 = contrast_ratio("#AAAAAA", "#FFFFFF").unwrap();
    assert!(ratio3 < 3.0, "ratio3={ratio3}");
    assert_eq!(contrast_level(ratio3), ContrastLevel::Red);
}

#[test]
fn low_contrast_theme_warns_but_still_resolves() {
    // BR-08: cảnh báo nhưng vẫn cho lưu / vẫn gộp được.
    let mut t = theme_with_light("t1", "#FFFFFF", "#3366FF");
    t.light.colors.text_primary = Some("#CCCCCC".to_string());
    let warnings = validate_theme(&t).unwrap();
    assert!(!warnings.is_empty());
    assert!(warnings.iter().any(|w| w.code.starts_with("W-01")));
    let reg = registry_with(vec![t]);
    assert!(resolve_theme(&reg, "t1", VariantMode::Light, None, None).is_ok());
}

#[test]
fn theme_json_roundtrip() {
    let t = default_theme();
    let json = serde_json::to_string(&t).unwrap();
    let back = theme_from_json(&json).unwrap();
    assert_eq!(back.id, t.id);
    assert_eq!(back.light.colors.primary, t.light.colors.primary);
}

#[test]
fn schema_version_too_new_is_rejected() {
    let mut t = default_theme();
    t.schema_version = 99;
    assert!(matches!(
        validate_theme(&t),
        Err(ThemeError::SchemaVersionTooNew(99))
    ));
}
