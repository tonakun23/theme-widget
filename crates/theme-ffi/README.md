# theme-ffi — Cầu nối Rust ↔ Kotlin

API public khai báo bằng UniFFI (`#[uniffi::export]`):

- `validate_color(color: String) -> bool`
- `resolve_theme_json(theme_json: String, dark: bool) -> Result<String, FfiError>`
- `validate_theme_json(theme_json: String) -> Result<String, FfiError>`

Ngoài ra có C ABI thô (`theme_validate_color`, `theme_resolve_json`,
`theme_free_string`) cho trường hợp dùng JNI thủ công — mọi hàm đều bọc
`catch_unwind`, **không để panic qua FFI**.

## TODO — Giai đoạn 2

1. `cargo ndk` build `.so` cho arm64-v8a / armeabi-v7a / x86_64.
2. Chạy `uniffi-bindgen` sinh code Kotlin từ `.so` vừa build.
3. Thêm JNA vào `android/app/build.gradle.kts` (đã khai sẵn dependency).
4. Đưa `ThemeEngine` thành `uniffi::Object` giữ registry dùng chung (Arc)
   thay vì truyền JSON qua lại mỗi lần gọi; truyền bảng màu hệ thống
   Material You từ Kotlin xuống (hiện `resolve_theme_json` truyền `None`).
