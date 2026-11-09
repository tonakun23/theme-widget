//! Unit test cho theme-editor.
use super::*;
use theme_core::{default_theme, Theme, ThemeKind, VariantMode};

fn sample_theme() -> Theme {
    let mut t = default_theme();
    t.id = "pastel".to_string();
    t.name = "Pastel Ngọt".to_string();
    t
}

fn set_color(path: &str, value: &str) -> Command {
    Command::SetToken {
        mode: VariantMode::Light,
        path: path.to_string(),
        value: TokenValue::Color(value.to_string()),
    }
}

fn set_number(path: &str, value: f32) -> Command {
    Command::SetToken {
        mode: VariantMode::Light,
        path: path.to_string(),
        value: TokenValue::Number(value),
    }
}

#[test]
fn apply_undo_redo_roundtrip() {
    let mut s = EditSession::new(sample_theme());
    let orig = s.draft.light.colors.primary.clone();
    s.apply(set_color("palette.primary", "#FF0000")).unwrap();
    assert_eq!(s.draft.light.colors.primary.as_deref(), Some("#FF0000"));
    assert!(s.is_dirty());
    assert!(s.undo());
    assert_eq!(s.draft.light.colors.primary, orig);
    assert!(!s.is_dirty());
    assert!(s.redo());
    assert_eq!(s.draft.light.colors.primary.as_deref(), Some("#FF0000"));
}

#[test]
fn undo_stops_at_empty_history() {
    let mut s = EditSession::new(sample_theme());
    assert!(!s.undo());
    assert!(!s.redo());
}

#[test]
fn undo_is_capped_at_50_steps() {
    let mut s = EditSession::new(sample_theme());
    for i in 0..60 {
        // Đổi bo góc 60 lần, mỗi lần một giá trị khác nhau.
        s.apply(set_number("shape.radius", (i % 32) as f32))
            .unwrap();
    }
    assert_eq!(s.undo_len(), 50);
    // Undo hết 50 bước: về giá trị trước lần sửa thứ 11 (i=9 -> 9.0),
    // vì mỗi lần undo khôi phục giá trị TRƯỚC khi sửa đó.
    for _ in 0..50 {
        assert!(s.undo());
    }
    assert!(!s.undo());
    assert_eq!(s.draft.light.shape.radius, Some(9.0));
}

#[test]
fn invalid_color_is_rejected_and_keeps_old_value() {
    let mut s = EditSession::new(sample_theme());
    let before = s.draft.light.colors.primary.clone();
    let err = s
        .apply(set_color("palette.primary", "not-a-color"))
        .unwrap_err();
    assert!(matches!(err, theme_core::ThemeError::InvalidColor(_)));
    assert_eq!(s.draft.light.colors.primary, before);
    assert_eq!(s.undo_len(), 0);
}

#[test]
fn numbers_are_clamped_on_apply() {
    let mut s = EditSession::new(sample_theme());
    s.apply(set_number("shape.radius", 99.0)).unwrap();
    assert_eq!(s.draft.light.shape.radius, Some(32.0));
    s.apply(set_number("typography.title.size", 5.0)).unwrap();
    assert_eq!(s.draft.light.typography.title_size, Some(10.0));
}

#[test]
fn unknown_token_is_rejected() {
    let mut s = EditSession::new(sample_theme());
    let err = s
        .apply(Command::SetToken {
            mode: VariantMode::Light,
            path: "palette.nope".to_string(),
            value: TokenValue::Color("#FFFFFF".to_string()),
        })
        .unwrap_err();
    assert!(matches!(err, theme_core::ThemeError::InvalidValue(_)));
}

#[test]
fn save_bumps_version_and_clears_history() {
    let mut s = EditSession::new(sample_theme());
    s.apply(set_color("palette.primary", "#FF0000")).unwrap();
    let saved = s.save();
    assert_eq!(saved.version, 2);
    assert!(!s.is_dirty());
    assert_eq!(s.undo_len(), 0);
}

#[test]
fn reset_to_original_restores_draft() {
    let mut s = EditSession::new(sample_theme());
    s.apply(set_color("palette.primary", "#FF0000")).unwrap();
    s.reset_to_original();
    assert_eq!(
        s.draft.light.colors.primary,
        sample_theme().light.colors.primary
    );
    assert!(!s.is_dirty());
}

#[test]
fn dark_mode_edit_creates_dark_variant() {
    let mut s = EditSession::new(sample_theme());
    s.mode = VariantMode::Dark;
    s.apply(Command::SetToken {
        mode: VariantMode::Dark,
        path: "palette.primary".to_string(),
        value: TokenValue::Color("#00FF00".to_string()),
    })
    .unwrap();
    assert_eq!(
        s.draft.dark.as_ref().unwrap().colors.primary.as_deref(),
        Some("#00FF00")
    );
    // Biến thể sáng không bị ảnh hưởng.
    assert_eq!(
        s.draft.light.colors.primary,
        sample_theme().light.colors.primary
    );
}

#[test]
fn draft_save_and_load_roundtrip() {
    let dir = std::env::temp_dir().join("widget-theme-test-draft");
    let _ = std::fs::remove_dir_all(&dir);
    let path = dir.join("draft.json");
    let mut s = EditSession::new(sample_theme()).with_draft_path(path.clone());
    s.apply(set_color("palette.primary", "#FF0000")).unwrap();
    // apply() đã autosave.
    let loaded = EditSession::load_draft(&path).unwrap();
    assert_eq!(loaded.light.colors.primary.as_deref(), Some("#FF0000"));
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn clone_builtin_names_copy_correctly() {
    let mut store = ThemeStore::new(vec![sample_theme()]);
    let c1 = store.clone_builtin("pastel").unwrap();
    assert_eq!(c1.name, "Pastel Ngọt (bản của tôi)");
    assert_eq!(c1.kind, ThemeKind::Custom);
    assert_ne!(c1.id, "pastel");
    let c2 = store.clone_builtin("pastel").unwrap();
    assert_eq!(c2.name, "Pastel Ngọt (bản của tôi) (2)");
}

#[test]
fn rename_validates_length_and_duplicates() {
    let mut store = ThemeStore::new(vec![sample_theme()]);
    let c = store.clone_builtin("pastel").unwrap();
    assert!(store.rename(&c.id, "").is_err());
    assert!(store.rename(&c.id, &"x".repeat(41)).is_err());
    assert!(store.rename(&c.id, "Tên mới").is_ok());
    let c2 = store.clone_builtin("pastel").unwrap();
    // Trùng tên (không phân biệt hoa/thường).
    assert!(store.rename(&c2.id, "tên mới").is_err());
    // Theme có sẵn không đổi tên được.
    assert!(store.rename("pastel", "Khác").is_err());
}

#[test]
fn builtin_cannot_be_deleted() {
    let mut store = ThemeStore::new(vec![sample_theme()]);
    assert!(store.delete("pastel").is_err());
    let c = store.clone_builtin("pastel").unwrap();
    assert!(store.delete(&c.id).is_ok());
}

#[test]
fn max_200_user_themes() {
    let mut store = ThemeStore::new(vec![sample_theme()]);
    for i in 0..200 {
        let mut t = sample_theme();
        t.id = format!("u{i}");
        t.name = format!("Theme {i}");
        t.kind = ThemeKind::Custom;
        store.add(t).unwrap();
    }
    let mut extra = sample_theme();
    extra.id = "u200".to_string();
    extra.name = "Theme 200".to_string();
    extra.kind = ThemeKind::Custom;
    assert!(matches!(
        store.add(extra),
        Err(theme_core::ThemeError::TooManyThemes)
    ));
}
