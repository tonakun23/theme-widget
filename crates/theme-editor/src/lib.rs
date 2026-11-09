//! theme-editor — Logic chỉnh sửa theme (Giai đoạn 1).
//!
//! - Mỗi thao tác sửa là một [`Command`] (SetToken...), ghi vào lịch sử
//!   để undo/redo (tối đa 50 bước/phiên, BR-10).
//! - [`EditSession`] giữ bản nháp + tự lưu nháp (BR-13, giữ 7 ngày).
//! - [`ThemeStore`] quản lý kho theme: nhân bản theme có sẵn (BR-04),
//!   tên 1–40 ký tự không trùng (BR-05), tối đa 200 theme người dùng (BR-23).
pub mod session;
pub mod store;

pub use session::{Command, EditSession, TokenValue};
pub use store::{validate_theme_name, ThemeStore};

#[cfg(test)]
mod tests;
