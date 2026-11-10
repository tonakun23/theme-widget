//! theme-ffi — Cầu nối Rust <-> Kotlin (Giai đoạn 1: khung).
//!
//! API public khai báo bằng UniFFI (`#[uniffi::export]`); Giai đoạn 2 sẽ:
//! 1. Chạy `uniffi-bindgen` để sinh code Kotlin từ thư viện `.so` này.
//! 2. Thêm JNA vào `build.gradle.kts` (bước bắt buộc, hay bị quên).
//! 3. Đưa `ThemeEngine` thành `uniffi::Object` giữ registry dùng chung
//!    (Arc) thay vì truyền JSON qua lại mỗi lần gọi.
//!
//! NGUYÊN TẮC BẮT BUỘC: không để panic đi qua ranh giới FFI. Mọi hàm
//! `extern "C"` đều bọc `catch_unwind` và trả mã lỗi; mọi hàm UniFFI
//! đều trả `Result<_, FfiError>`.
use std::collections::HashMap;
use std::ffi::{c_char, CStr, CString};
use theme_core::{
    default_theme, resolve_theme, theme_from_json, validate_theme, Colors, VariantMode,
    DEFAULT_THEME_ID,
};

/// Lỗi trả về phía Kotlin qua UniFFI.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiError {
    #[error("theme không hợp lệ: {0}")]
    InvalidTheme(String),
    #[error("lỗi nội bộ: {0}")]
    Internal(String),
}

impl From<theme_core::ThemeError> for FfiError {
    fn from(e: theme_core::ThemeError) -> Self {
        FfiError::InvalidTheme(e.to_string())
    }
}

/// Kiểm tra một mã màu (#RGB / #RRGGBB / #RRGGBBAA).
#[uniffi::export]
pub fn validate_color(color: String) -> bool {
    theme_core::is_valid_color(&color)
}

/// Gộp một theme JSON thành theme cuối cùng (BR-24) và trả JSON.
/// `dark = true` thì gộp biến thể Tối (tự sinh từ Sáng khi thiếu, BR-16).
/// Registry ở Giai đoạn 1 chỉ gồm theme Mặc định + chính theme truyền vào;
/// Giai đoạn 2 sẽ truyền cả kho theme (để hỗ trợ `extends`).
#[uniffi::export]
pub fn resolve_theme_json(theme_json: String, dark: bool) -> Result<String, FfiError> {
    resolve_inner(theme_json, dark, None)
}

/// Gộp theme kèm bảng màu hệ thống Material You (Giai đoạn 4, BR-17).
/// `system_json` là JSON của [`Colors`] (thiếu trường nào thì trường đó
/// giữ nguyên màu theme); chỉ áp khi theme bật `follow_system`.
#[uniffi::export]
pub fn resolve_theme_json_with_system(
    theme_json: String,
    dark: bool,
    system_json: String,
) -> Result<String, FfiError> {
    let system: Colors = serde_json::from_str(&system_json)
        .map_err(|e| FfiError::InvalidTheme(format!("system_json không hợp lệ: {e}")))?;
    resolve_inner(theme_json, dark, Some(system))
}

fn resolve_inner(
    theme_json: String,
    dark: bool,
    system: Option<Colors>,
) -> Result<String, FfiError> {
    let theme = theme_from_json(&theme_json)?;
    let mut registry: HashMap<String, theme_core::Theme> = HashMap::new();
    registry.insert(DEFAULT_THEME_ID.to_string(), default_theme());
    registry.insert(theme.id.clone(), theme.clone());
    let mode = if dark {
        VariantMode::Dark
    } else {
        VariantMode::Light
    };
    let resolved = resolve_theme(&registry, &theme.id, mode, system.as_ref(), None)?;
    serde_json::to_string(&resolved).map_err(|e| FfiError::Internal(e.to_string()))
}

/// Kiểm tra một theme JSON, trả về danh sách cảnh báo (tương phản...) dạng JSON.
/// Theme không hợp lệ thì trả `Err`.
#[uniffi::export]
pub fn validate_theme_json(theme_json: String) -> Result<String, FfiError> {
    let theme = theme_from_json(&theme_json)?;
    let warnings = validate_theme(&theme)?;
    serde_json::to_string(&warnings).map_err(|e| FfiError::Internal(e.to_string()))
}

uniffi::setup_scaffolding!();

// ---------------------------------------------------------------------------
// C ABI thô (dự phòng cho JNI thủ công): mọi hàm đều bọc catch_unwind.
// Chuỗi trả về phải được giải phóng bằng `theme_free_string`.
// ---------------------------------------------------------------------------

/// Trả 1 khi mã màu hợp lệ, 0 khi không hợp lệ hoặc con trỏ null.
///
/// # Safety
/// `ptr` phải trỏ tới một chuỗi C kết thúc bằng NUL còn hiệu lực,
/// hoặc là null (trả về false).
#[no_mangle]
pub unsafe extern "C" fn theme_validate_color(ptr: *const c_char) -> bool {
    std::panic::catch_unwind(|| {
        if ptr.is_null() {
            return false;
        }
        let s = unsafe { CStr::from_ptr(ptr) }.to_string_lossy();
        theme_core::is_valid_color(&s)
    })
    .unwrap_or(false)
}

/// Gộp theme JSON, trả chuỗi JSON đã gộp (null khi lỗi). Giải phóng bằng
/// `theme_free_string`. `dark`: 0 = Sáng, khác 0 = Tối.
///
/// # Safety
/// `ptr` phải trỏ tới một chuỗi C kết thúc bằng NUL còn hiệu lực,
/// hoặc là null (trả về null).
#[no_mangle]
pub unsafe extern "C" fn theme_resolve_json(ptr: *const c_char, dark: bool) -> *mut c_char {
    let result = std::panic::catch_unwind(|| {
        if ptr.is_null() {
            return None;
        }
        let json = unsafe { CStr::from_ptr(ptr) }
            .to_string_lossy()
            .into_owned();
        resolve_theme_json(json, dark).ok()
    })
    .unwrap_or(None);
    match result {
        Some(s) => CString::new(s)
            .map(|c| c.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

/// Giải phóng chuỗi do `theme_resolve_json` trả về.
///
/// # Safety
/// `ptr` phải là con trỏ do `theme_resolve_json` trả về và chưa được
/// giải phóng trước đó; null thì không làm gì.
#[no_mangle]
pub unsafe extern "C" fn theme_free_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        let _ = unsafe { CString::from_raw(ptr) };
    }
}
