//! Mô hình dữ liệu theme theo design token.
//!
//! Token chuẩn (xem BA §8.3):
//! - Màu: `background`, `surface`, `primary`, `accent`, `text.primary`,
//!   `text.secondary`, `border`
//! - Chữ: `title`, `body`, `caption` (cỡ + độ đậm)
//! - Hình khối: `radius`, `padding`, `border.width`
//! - Nền: loại (màu / gradient / ảnh), màu hoặc ảnh, độ mờ
//!
//! Mọi trường token trong [`Variant`] đều là `Option`: `None` nghĩa là
//! "lớp này không đặt token đó, lấy từ lớp dưới khi gộp" (BR-24).
//! [`ResolvedTheme`] là kết quả cuối cùng sau khi gộp: mọi token đều có giá trị.
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

/// Phiên bản định dạng file theme mà app này hỗ trợ (BR-21, BR-22).
pub const CURRENT_SCHEMA_VERSION: u32 = 1;
/// Mã theme "Mặc định" — không xóa, không sửa được (BR-02, BR-03).
pub const DEFAULT_THEME_ID: &str = "default";
/// Số cấp kế thừa tối đa (BR-09).
pub const MAX_INHERITANCE_DEPTH: usize = 3;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum ThemeKind {
    /// Theme đi kèm app, chỉ đọc (BR-04).
    BuiltIn,
    /// Theme do người dùng tạo hoặc nhân bản.
    Custom,
    /// Theme nhập từ gói file (BR-22).
    Imported,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum BgKind {
    Solid,
    Gradient,
    Image,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum VariantMode {
    Light,
    Dark,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Colors {
    /// palette.background
    pub background: Option<String>,
    /// palette.surface
    pub surface: Option<String>,
    /// palette.primary
    pub primary: Option<String>,
    /// palette.accent
    pub accent: Option<String>,
    /// text.primary
    pub text_primary: Option<String>,
    /// text.secondary
    pub text_secondary: Option<String>,
    /// border
    pub border: Option<String>,
    /// Cờ "theo màu hệ thống" (BR-17): chỉ token được đánh dấu mới lấy
    /// màu hệ thống khi gộp.
    pub follow_system: bool,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Typography {
    /// Cỡ chữ tiêu đề, sp (10–40, BR-07).
    pub title_size: Option<f32>,
    /// Cỡ chữ nội dung, sp.
    pub body_size: Option<f32>,
    /// Cỡ chữ chú thích, sp.
    pub caption_size: Option<f32>,
    pub title_bold: Option<bool>,
    pub body_bold: Option<bool>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Shape {
    /// Bo góc, dp (0–32, BR-07).
    pub radius: Option<f32>,
    /// Padding, dp (0–24, BR-07).
    pub padding: Option<f32>,
    /// Độ dày viền, dp (0–4, BR-07).
    pub border_width: Option<f32>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Background {
    /// Loại nền: màu trơn / gradient / ảnh (gradient, ảnh ở V2).
    pub kind: Option<BgKind>,
    /// Màu nền khi kind = Solid.
    pub color: Option<String>,
    /// Độ mờ nền, % (0–100, BR-07).
    pub opacity: Option<f32>,
}

/// Một biến thể (Sáng hoặc Tối) của theme. Mọi token đều tùy chọn:
/// thiếu token thì lấy từ lớp dưới khi gộp (BA §8.1).
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Variant {
    pub colors: Colors,
    pub typography: Typography,
    pub shape: Shape,
    pub background: Background,
}

impl Variant {
    /// Gộp `other` lên trên `self`: token nào `other` có giá trị thì thắng.
    pub fn merge(&self, other: &Variant) -> Variant {
        Variant {
            colors: Colors {
                background: other
                    .colors
                    .background
                    .clone()
                    .or(self.colors.background.clone()),
                surface: other.colors.surface.clone().or(self.colors.surface.clone()),
                primary: other.colors.primary.clone().or(self.colors.primary.clone()),
                accent: other.colors.accent.clone().or(self.colors.accent.clone()),
                text_primary: other
                    .colors
                    .text_primary
                    .clone()
                    .or(self.colors.text_primary.clone()),
                text_secondary: other
                    .colors
                    .text_secondary
                    .clone()
                    .or(self.colors.text_secondary.clone()),
                border: other.colors.border.clone().or(self.colors.border.clone()),
                follow_system: other.colors.follow_system || self.colors.follow_system,
            },
            typography: Typography {
                title_size: other.typography.title_size.or(self.typography.title_size),
                body_size: other.typography.body_size.or(self.typography.body_size),
                caption_size: other
                    .typography
                    .caption_size
                    .or(self.typography.caption_size),
                title_bold: other.typography.title_bold.or(self.typography.title_bold),
                body_bold: other.typography.body_bold.or(self.typography.body_bold),
            },
            shape: Shape {
                radius: other.shape.radius.or(self.shape.radius),
                padding: other.shape.padding.or(self.shape.padding),
                border_width: other.shape.border_width.or(self.shape.border_width),
            },
            background: Background {
                kind: other.background.kind.or(self.background.kind),
                color: other
                    .background
                    .color
                    .clone()
                    .or(self.background.color.clone()),
                opacity: other.background.opacity.or(self.background.opacity),
            },
        }
    }

    /// Kẹp mọi giá trị số về khoảng cho phép (BR-07). Trả về số token đã kẹp.
    pub fn clamp(&mut self) -> usize {
        let mut clamped = 0;
        let mut clamp = |v: &mut Option<f32>, min: f32, max: f32| {
            if let Some(x) = v {
                let c = x.clamp(min, max);
                if (c - *x).abs() > f32::EPSILON {
                    *x = c;
                    clamped += 1;
                }
            }
        };
        clamp(&mut self.shape.radius, 0.0, 32.0);
        clamp(&mut self.shape.padding, 0.0, 24.0);
        clamp(&mut self.shape.border_width, 0.0, 4.0);
        clamp(&mut self.typography.title_size, 10.0, 40.0);
        clamp(&mut self.typography.body_size, 10.0, 40.0);
        clamp(&mut self.typography.caption_size, 10.0, 40.0);
        clamp(&mut self.background.opacity, 0.0, 100.0);
        clamped
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
#[serde(default)]
pub struct Theme {
    pub id: String,
    pub name: String,
    pub kind: ThemeKind,
    /// Theme cha (extends), tối đa 3 cấp, cấm vòng lặp (BR-09).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub extends: Option<String>,
    pub schema_version: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub author: Option<String>,
    /// Số lần sửa, tăng mỗi lần lưu (UC-02 bước 6).
    pub version: u32,
    /// Biến thể Sáng — luôn có.
    pub light: Variant,
    /// Biến thể Tối — thiếu thì engine tự sinh từ Sáng (BR-16).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub dark: Option<Variant>,
}

impl Default for Theme {
    fn default() -> Self {
        Theme {
            id: DEFAULT_THEME_ID.to_string(),
            name: "Mặc định".to_string(),
            kind: ThemeKind::BuiltIn,
            extends: None,
            schema_version: CURRENT_SCHEMA_VERSION,
            author: None,
            version: 1,
            light: Variant::default(),
            dark: None,
        }
    }
}

/// Theme cuối cùng sau khi gộp (BR-24): mọi token đều có giá trị cụ thể.
/// Đây là thứ Kotlin nhận từ Rust và đổi thành lệnh RemoteViews/Glance.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct ResolvedTheme {
    pub theme_id: String,
    pub theme_name: String,
    pub mode: VariantMode,
    pub background: String,
    pub surface: String,
    pub primary: String,
    pub accent: String,
    pub text_primary: String,
    pub text_secondary: String,
    pub border: String,
    pub title_size: f32,
    pub body_size: f32,
    pub caption_size: f32,
    pub title_bold: bool,
    pub body_bold: bool,
    pub radius: f32,
    pub padding: f32,
    pub border_width: f32,
    pub bg_kind: BgKind,
    pub bg_color: String,
    /// Độ mờ nền, %.
    pub opacity: f32,
}

/// Theme "Mặc định" đầy đủ giá trị (BR-02, BR-03): lớp nền của mọi phép gộp.
pub fn default_theme() -> Theme {
    let mut t = Theme::default();
    t.light = Variant {
        colors: Colors {
            background: Some("#FFFFFF".to_string()),
            surface: Some("#F2F2F7".to_string()),
            primary: Some("#3366FF".to_string()),
            accent: Some("#FF8A00".to_string()),
            text_primary: Some("#1A1A1A".to_string()),
            text_secondary: Some("#6B7280".to_string()),
            border: Some("#E5E7EB".to_string()),
            follow_system: false,
        },
        typography: Typography {
            title_size: Some(20.0),
            body_size: Some(14.0),
            caption_size: Some(12.0),
            title_bold: Some(true),
            body_bold: Some(false),
        },
        shape: Shape {
            radius: Some(16.0),
            padding: Some(12.0),
            border_width: Some(1.0),
        },
        background: Background {
            kind: Some(BgKind::Solid),
            color: Some("#FFFFFF".to_string()),
            opacity: Some(100.0),
        },
    };
    t.dark = Some(Variant {
        colors: Colors {
            background: Some("#121212".to_string()),
            surface: Some("#1E1E1E".to_string()),
            primary: Some("#7AA2FF".to_string()),
            accent: Some("#FFB25E".to_string()),
            text_primary: Some("#F5F5F5".to_string()),
            text_secondary: Some("#A1A1AA".to_string()),
            border: Some("#2E2E2E".to_string()),
            follow_system: false,
        },
        typography: t.light.typography.clone(),
        shape: t.light.shape.clone(),
        background: Background {
            kind: Some(BgKind::Solid),
            color: Some("#121212".to_string()),
            opacity: Some(100.0),
        },
    });
    t
}
