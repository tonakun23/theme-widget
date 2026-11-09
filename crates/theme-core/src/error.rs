//! Các loại lỗi của Theme Engine (được đưa sang Kotlin qua FFI ở Giai đoạn 2).
use thiserror::Error;

/// Mọi lỗi mà lõi Rust có thể trả về. Giao diện chỉ hiển thị thông báo
/// ngắn cho người dùng (E-01..E-08), không hiển thị mã kỹ thuật.
#[derive(Debug, Error, PartialEq, Eq)]
pub enum ThemeError {
    #[error("Mã màu không hợp lệ: {0}. Ví dụ: #3366FF")]
    InvalidColor(String),

    #[error("Tên theme trống hoặc dài quá 40 ký tự")]
    InvalidName,

    #[error("Tên theme đã tồn tại: {0}")]
    DuplicateName(String),

    #[error("Đã đạt tối đa 200 theme người dùng")]
    TooManyThemes,

    #[error("Vòng lặp kế thừa theme: {0}")]
    InheritanceCycle(String),

    #[error("Kế thừa theme cha vượt quá 3 cấp")]
    InheritanceTooDeep,

    #[error("Không tìm thấy theme cha: {0}")]
    ParentNotFound(String),

    #[error("Không tìm thấy theme: {0}")]
    ThemeNotFound(String),

    #[error("Giá trị ngoài khoảng cho phép: {0}")]
    InvalidValue(String),

    #[error("Phiên bản định dạng theme ({0}) mới hơn app hỗ trợ")]
    SchemaVersionTooNew(u32),

    #[error("File theme hỏng hoặc sai định dạng: {0}")]
    InvalidThemeFile(String),

    #[error("Bản nháp đã hết hạn (giữ tối đa 7 ngày)")]
    DraftExpired,

    #[error("Lỗi đọc/ghi file: {0}")]
    Io(String),

    #[error("Lỗi JSON: {0}")]
    Json(String),
}

impl From<std::io::Error> for ThemeError {
    fn from(e: std::io::Error) -> Self {
        ThemeError::Io(e.to_string())
    }
}

impl From<serde_json::Error> for ThemeError {
    fn from(e: serde_json::Error) -> Self {
        ThemeError::Json(e.to_string())
    }
}
