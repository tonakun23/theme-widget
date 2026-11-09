//! Phiên chỉnh sửa: bản nháp + lịch sử Command + tự lưu nháp (BR-13).
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};
use theme_core::{
    generate_dark_variant, is_valid_color, BgKind, Theme, ThemeError, Variant, VariantMode,
};

/// Lịch sử undo/redo tối đa trong một phiên (BR-10).
pub const MAX_UNDO_STEPS: usize = 50;
/// Bản nháp giữ tối đa 7 ngày (BR-13).
pub const DRAFT_TTL_SECS: u64 = 7 * 24 * 60 * 60;

/// Giá trị của một token khi sửa.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TokenValue {
    Color(String),
    Number(f32),
    Bool(bool),
    Text(String),
}

/// Một thao tác sửa. Ví dụ: SetToken("palette.primary", Color("#3366FF")).
/// Đường dẫn token (xem [`token_kind`]):
/// màu: `palette.background|surface|primary|accent`, `text.primary`,
/// `text.secondary`, `border`, `background.color`;
/// số: `shape.radius|padding|border.width`, `typography.title|body|caption.size`,
/// `background.opacity`; bool: `typography.title|body.bold`;
/// chữ: `background.kind` = "solid" | "gradient" | "image".
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum Command {
    SetToken {
        mode: VariantMode,
        path: String,
        value: TokenValue,
    },
}

fn now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

fn token_kind(path: &str) -> &'static str {
    match path {
        "palette.background" | "palette.surface" | "palette.primary" | "palette.accent"
        | "text.primary" | "text.secondary" | "border" | "background.color" => "color",
        "shape.radius"
        | "shape.padding"
        | "shape.border.width"
        | "typography.title.size"
        | "typography.body.size"
        | "typography.caption.size"
        | "background.opacity" => "number",
        "typography.title.bold" | "typography.body.bold" => "bool",
        "background.kind" => "text",
        _ => "unknown",
    }
}

fn clamp_for(path: &str, v: f32) -> f32 {
    match path {
        "shape.radius" => v.clamp(0.0, 32.0),
        "shape.padding" => v.clamp(0.0, 24.0),
        "shape.border.width" => v.clamp(0.0, 4.0),
        "typography.title.size" | "typography.body.size" | "typography.caption.size" => {
            v.clamp(10.0, 40.0)
        }
        "background.opacity" => v.clamp(0.0, 100.0),
        _ => v,
    }
}

/// Đọc giá trị hiện tại của một token trong variant.
pub fn get_token(v: &Variant, path: &str) -> Option<TokenValue> {
    match path {
        "palette.background" => v.colors.background.clone().map(TokenValue::Color),
        "palette.surface" => v.colors.surface.clone().map(TokenValue::Color),
        "palette.primary" => v.colors.primary.clone().map(TokenValue::Color),
        "palette.accent" => v.colors.accent.clone().map(TokenValue::Color),
        "text.primary" => v.colors.text_primary.clone().map(TokenValue::Color),
        "text.secondary" => v.colors.text_secondary.clone().map(TokenValue::Color),
        "border" => v.colors.border.clone().map(TokenValue::Color),
        "background.color" => v.background.color.clone().map(TokenValue::Color),
        "shape.radius" => v.shape.radius.map(TokenValue::Number),
        "shape.padding" => v.shape.padding.map(TokenValue::Number),
        "shape.border.width" => v.shape.border_width.map(TokenValue::Number),
        "typography.title.size" => v.typography.title_size.map(TokenValue::Number),
        "typography.body.size" => v.typography.body_size.map(TokenValue::Number),
        "typography.caption.size" => v.typography.caption_size.map(TokenValue::Number),
        "background.opacity" => v.background.opacity.map(TokenValue::Number),
        "typography.title.bold" => v.typography.title_bold.map(TokenValue::Bool),
        "typography.body.bold" => v.typography.body_bold.map(TokenValue::Bool),
        "background.kind" => v.background.kind.map(|k| {
            TokenValue::Text(match k {
                BgKind::Solid => "solid".to_string(),
                BgKind::Gradient => "gradient".to_string(),
                BgKind::Image => "image".to_string(),
            })
        }),
        _ => None,
    }
}

/// Ghi một token vào variant. Màu sai định dạng thì từ chối (BR-06, E-01);
/// số ngoài khoảng thì kẹp về giới hạn (BR-07).
pub fn set_token(v: &mut Variant, path: &str, value: &TokenValue) -> Result<(), ThemeError> {
    match (path, value) {
        ("palette.background", TokenValue::Color(c)) => check_set(&mut v.colors.background, c),
        ("palette.surface", TokenValue::Color(c)) => check_set(&mut v.colors.surface, c),
        ("palette.primary", TokenValue::Color(c)) => check_set(&mut v.colors.primary, c),
        ("palette.accent", TokenValue::Color(c)) => check_set(&mut v.colors.accent, c),
        ("text.primary", TokenValue::Color(c)) => check_set(&mut v.colors.text_primary, c),
        ("text.secondary", TokenValue::Color(c)) => check_set(&mut v.colors.text_secondary, c),
        ("border", TokenValue::Color(c)) => check_set(&mut v.colors.border, c),
        ("background.color", TokenValue::Color(c)) => check_set(&mut v.background.color, c),
        ("shape.radius", TokenValue::Number(n)) => {
            v.shape.radius = Some(clamp_for(path, *n));
            Ok(())
        }
        ("shape.padding", TokenValue::Number(n)) => {
            v.shape.padding = Some(clamp_for(path, *n));
            Ok(())
        }
        ("shape.border.width", TokenValue::Number(n)) => {
            v.shape.border_width = Some(clamp_for(path, *n));
            Ok(())
        }
        ("typography.title.size", TokenValue::Number(n)) => {
            v.typography.title_size = Some(clamp_for(path, *n));
            Ok(())
        }
        ("typography.body.size", TokenValue::Number(n)) => {
            v.typography.body_size = Some(clamp_for(path, *n));
            Ok(())
        }
        ("typography.caption.size", TokenValue::Number(n)) => {
            v.typography.caption_size = Some(clamp_for(path, *n));
            Ok(())
        }
        ("background.opacity", TokenValue::Number(n)) => {
            v.background.opacity = Some(clamp_for(path, *n));
            Ok(())
        }
        ("typography.title.bold", TokenValue::Bool(b)) => {
            v.typography.title_bold = Some(*b);
            Ok(())
        }
        ("typography.body.bold", TokenValue::Bool(b)) => {
            v.typography.body_bold = Some(*b);
            Ok(())
        }
        ("background.kind", TokenValue::Text(t)) => {
            v.background.kind = Some(match t.as_str() {
                "gradient" => BgKind::Gradient,
                "image" => BgKind::Image,
                _ => BgKind::Solid,
            });
            Ok(())
        }
        _ => Err(ThemeError::InvalidValue(format!(
            "Token không hợp lệ: {path} = {value:?}"
        ))),
    }
}

fn check_set(slot: &mut Option<String>, c: &str) -> Result<(), ThemeError> {
    if !is_valid_color(c) {
        return Err(ThemeError::InvalidColor(c.to_string()));
    }
    *slot = Some(c.to_string());
    Ok(())
}

fn clear_token(v: &mut Variant, path: &str) {
    let none_color = TokenValue::Color(String::new());
    let _ = none_color;
    match path {
        "palette.background" => v.colors.background = None,
        "palette.surface" => v.colors.surface = None,
        "palette.primary" => v.colors.primary = None,
        "palette.accent" => v.colors.accent = None,
        "text.primary" => v.colors.text_primary = None,
        "text.secondary" => v.colors.text_secondary = None,
        "border" => v.colors.border = None,
        "background.color" => v.background.color = None,
        "shape.radius" => v.shape.radius = None,
        "shape.padding" => v.shape.padding = None,
        "shape.border.width" => v.shape.border_width = None,
        "typography.title.size" => v.typography.title_size = None,
        "typography.body.size" => v.typography.body_size = None,
        "typography.caption.size" => v.typography.caption_size = None,
        "background.opacity" => v.background.opacity = None,
        "typography.title.bold" => v.typography.title_bold = None,
        "typography.body.bold" => v.typography.body_bold = None,
        "background.kind" => v.background.kind = None,
        _ => {}
    }
}

#[derive(Debug, Clone)]
struct HistoryEntry {
    do_cmd: Command,
    /// Lệnh ngược: giá trị trước khi sửa (None = xóa token).
    undo_value: Option<TokenValue>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct DraftFile {
    theme: Theme,
    saved_at_unix: u64,
}

/// Một phiên chỉnh sửa (UC-02): bản nháp + lịch sử undo/redo.
#[derive(Debug)]
pub struct EditSession {
    pub original: Theme,
    pub draft: Theme,
    pub mode: VariantMode,
    undo_stack: Vec<HistoryEntry>,
    redo_stack: Vec<HistoryEntry>,
    pub created_at_unix: u64,
    draft_path: Option<PathBuf>,
}

impl EditSession {
    pub fn new(theme: Theme) -> Self {
        EditSession {
            original: theme.clone(),
            draft: theme,
            mode: VariantMode::Light,
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
            created_at_unix: now_unix(),
            draft_path: None,
        }
    }

    pub fn with_draft_path(mut self, path: impl Into<PathBuf>) -> Self {
        self.draft_path = Some(path.into());
        self
    }

    pub fn undo_len(&self) -> usize {
        self.undo_stack.len()
    }

    pub fn redo_len(&self) -> usize {
        self.redo_stack.len()
    }

    pub fn is_dirty(&self) -> bool {
        self.draft != self.original
    }

    fn variant_mut(&mut self, mode: VariantMode) -> &mut Variant {
        match mode {
            VariantMode::Light => &mut self.draft.light,
            VariantMode::Dark => {
                if self.draft.dark.is_none() {
                    let dark = generate_dark_variant(&self.draft.light);
                    self.draft.dark = Some(dark);
                }
                self.draft.dark.as_mut().unwrap()
            }
        }
    }

    fn variant_ref(&self, mode: VariantMode) -> Variant {
        match mode {
            VariantMode::Light => self.draft.light.clone(),
            VariantMode::Dark => self
                .draft
                .dark
                .clone()
                .unwrap_or_else(|| generate_dark_variant(&self.draft.light)),
        }
    }

    /// Áp một lệnh sửa: kiểm tra (BR-06/BR-07), ghi lịch sử, tự lưu nháp.
    pub fn apply(&mut self, cmd: Command) -> Result<(), ThemeError> {
        let Command::SetToken {
            mode,
            ref path,
            ref value,
        } = cmd;
        if token_kind(path) == "unknown" {
            return Err(ThemeError::InvalidValue(format!(
                "Token không tồn tại: {path}"
            )));
        }
        // Kiểm tra trước khi ghi: màu sai thì từ chối, không nhận (E-01).
        if let TokenValue::Color(c) = value {
            if token_kind(path) == "color" && !is_valid_color(c) {
                return Err(ThemeError::InvalidColor(c.clone()));
            }
        }
        let prev = get_token(&self.variant_ref(mode), path);
        // Kẹp số trước khi lưu vào lịch sử để undo/redo nhất quán.
        let effective_value = match value {
            TokenValue::Number(n) => TokenValue::Number(clamp_for(path, *n)),
            other => other.clone(),
        };
        let effective_cmd = Command::SetToken {
            mode,
            path: path.clone(),
            value: effective_value,
        };
        set_token(
            self.variant_mut(mode),
            path,
            &effective_cmd_value(&effective_cmd),
        )?;
        self.undo_stack.push(HistoryEntry {
            do_cmd: effective_cmd,
            undo_value: prev,
        });
        if self.undo_stack.len() > MAX_UNDO_STEPS {
            self.undo_stack.remove(0); // chỉ giữ 50 bước mới nhất (BR-10)
        }
        self.redo_stack.clear();
        self.autosave();
        Ok(())
    }

    pub fn undo(&mut self) -> bool {
        let Some(entry) = self.undo_stack.pop() else {
            return false;
        };
        let Command::SetToken { mode, ref path, .. } = entry.do_cmd;
        match &entry.undo_value {
            Some(v) => {
                let _ = set_token(self.variant_mut(mode), path, v);
            }
            None => clear_token(self.variant_mut(mode), path),
        }
        self.redo_stack.push(entry);
        self.autosave();
        true
    }

    pub fn redo(&mut self) -> bool {
        let Some(entry) = self.redo_stack.pop() else {
            return false;
        };
        let Command::SetToken {
            mode,
            ref path,
            ref value,
        } = entry.do_cmd;
        let _ = set_token(self.variant_mut(mode), path, value);
        self.undo_stack.push(entry);
        self.autosave();
        true
    }

    /// Khôi phục bản nháp về giá trị theme gốc (UC-02 bước 4b, vẫn undo được).
    pub fn reset_to_original(&mut self) {
        self.draft = self.original.clone();
        self.undo_stack.clear();
        self.redo_stack.clear();
        self.autosave();
    }

    /// Lưu theme: tăng số phiên bản (UC-02 bước 6), trả về theme đã lưu.
    pub fn save(&mut self) -> Theme {
        self.draft.version += 1;
        self.original = self.draft.clone();
        self.undo_stack.clear();
        self.redo_stack.clear();
        self.draft.clone()
    }

    /// Tự lưu bản nháp sau mỗi thay đổi (BR-13). Lỗi ghi nháp không chặn sửa.
    pub fn autosave(&self) {
        if let Some(path) = &self.draft_path {
            let _ = self.save_draft(path);
        }
    }

    pub fn save_draft(&self, path: &Path) -> Result<(), ThemeError> {
        let file = DraftFile {
            theme: self.draft.clone(),
            saved_at_unix: now_unix(),
        };
        let json = serde_json::to_string_pretty(&file)?;
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        // Ghi an toàn: ghi file tạm rồi đổi tên (NFR-07).
        let tmp = path.with_extension("tmp");
        std::fs::write(&tmp, json)?;
        std::fs::rename(&tmp, path)?;
        Ok(())
    }

    /// Khôi phục bản nháp khi mở lại app (BR-13). Quá 7 ngày thì từ chối.
    pub fn load_draft(path: &Path) -> Result<Theme, ThemeError> {
        let text = std::fs::read_to_string(path)?;
        let file: DraftFile = serde_json::from_str(&text)?;
        if now_unix().saturating_sub(file.saved_at_unix) > DRAFT_TTL_SECS {
            return Err(ThemeError::DraftExpired);
        }
        Ok(file.theme)
    }
}

fn effective_cmd_value(cmd: &Command) -> TokenValue {
    let Command::SetToken { value, .. } = cmd;
    value.clone()
}
