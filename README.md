# Theme Widget — App Android (Rust lõi + Kotlin vỏ)

App cho phép người dùng Android chọn, chỉnh sửa và áp dụng theme cho widget
màn hình chính mà không cần biết thiết kế hay lập trình.
Tài liệu nghiệp vụ (BA): 09/11/2026, @Phan Quang.

## Mục tiêu V1 (MVP, hoàn toàn ngoại tuyến)

1. Áp một theme cho widget trong **dưới 30 giây** kể từ khi thêm widget.
2. Chỉnh được màu, chữ, bo góc, nền, độ trong suốt và **xem trước ngay** (≤ 100 ms).
3. Một theme dùng chung cho nhiều widget; mỗi widget vẫn chỉnh riêng được (ghi đè).
4. Theme tự thích ứng chế độ sáng/tối và màu hệ thống (Material You, Android 12+).
5. Chia sẻ và nhập theme dưới dạng gói file — **V2**.

Phạm vi V1: widget đồng hồ, lịch, ghi chú, pin; 8–12 theme có sẵn
(repo này có **10 theme** trong `themes/`); chỉnh màu/chữ/bo góc/padding/
độ trong suốt; nền màu trơn (gradient, ảnh ở V2); undo/redo, xem trước trực
tiếp; sáng/tối tự động; Material You; chỉnh riêng từng widget.
Không có ở V1: font riêng, xuất/nhập gói theme, tài khoản, đồng bộ đám mây.

## Cấu trúc

```text
widget-theme/
├── Cargo.toml                  # workspace (theme-core, theme-editor, theme-cli, theme-ffi)
├── crates/
│   ├── theme-core/             # mô hình theme, kiểm tra, gộp theme (BR-01..BR-24)
│   │   └── src/bin/gen_schema.rs  # sinh schema/theme.schema.json (schemars)
│   ├── theme-editor/           # Command, undo/redo 50 bước, phiên chỉnh sửa, kho theme
│   ├── theme-cli/              # CLI: validate, resolve, list-themes (F-24)
│   └── theme-ffi/              # cầu nối Rust <-> Kotlin (UniFFI + C ABI, catch_unwind)
├── themes/                     # 10 theme có sẵn dạng JSON, đúng schema
├── schema/theme.schema.json    # do schemars sinh ra, commit kèm
└── android/                    # KHUNG Kotlin thật, chưa build (xem dưới)
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── java/com/widgettheme/{MainActivity,editor,widget,store}/
        └── res/{xml,layout,values}/   # appwidget-provider cho 4 loại widget
```

### Quy tắc nghiệp vụ đã cài trong lõi Rust

- **BR-06**: màu `#RGB` / `#RRGGBB` / `#RRGGBBAA`, sai thì từ chối (E-01).
- **BR-07**: bo góc 0–32 dp, padding 0–24 dp, viền 0–4 dp, cỡ chữ 10–40 sp,
  độ mờ nền 0–100%; ngoài khoảng thì kẹp về giới hạn gần nhất.
- **BR-08**: tương phản chữ chính/nền < 4.5:1 cảnh báo vàng, < 3:1 cảnh báo đỏ
  (W-01) — vẫn cho lưu.
- **BR-09**: kế thừa theme cha tối đa 3 cấp, cấm vòng lặp.
- **BR-24** (thứ tự gộp, ưu tiên tăng dần): Mặc định → Theme cha → Theme đã
  chọn → Biến thể sáng/tối → Màu hệ thống → Ghi đè widget.
- **BR-16**: thiếu biến thể tối thì engine tự sinh từ biến thể sáng.
- **BR-04**: theme có sẵn chỉ đọc; bấm sửa thì tự nhân bản thành
  `"<Tên> (bản của tôi)"`. **BR-05**: tên 1–40 ký tự, không trùng
  (không phân biệt hoa/thường). **BR-10**: undo/redo tối đa 50 bước/phiên.
  **BR-13**: bản nháp tự lưu sau mỗi thay đổi, giữ 7 ngày.
  **BR-23**: tối đa 200 theme người dùng.

## Build / test (trên máy này — Linux, đã kiểm chứng 09/11/2026)

Cần: Rust stable (cài bằng rustup, xem dưới). Không cần Android SDK/JDK
cho phần Rust.

```sh
# Cài Rust (đã làm trên máy này: rustc/cargo 1.99.1)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable --profile minimal
rustup component add rustfmt clippy

# Test + kiểm tra định dạng + lint
cargo test                  # 28 test, xem kết quả bên dưới
cargo fmt --check
cargo clippy --all-targets

# Sinh lại JSON Schema sau khi đổi struct Theme
cargo run -p theme-core --bin gen_schema   # ghi schema/theme.schema.json

# CLI cho nhà thiết kế theme
cargo run -p theme-cli -- validate themes/pastel-ngot.json
cargo run -p theme-cli -- resolve themes/ca-phe-sua.json --dark
cargo run -p theme-cli -- list-themes themes/
```

### Kết quả thật (09/11/2026, máy này)

| Lệnh | Kết quả |
|---|---|
| `cargo test` | **28 passed, 0 failed** — theme-core 14/14, theme-editor 14/14 (theme-cli, theme-ffi 0 test) |
| `cargo fmt --check` | OK (sau `cargo fmt`) |
| `cargo clippy --all-targets` | **0 warning, 0 error** |
| `theme-cli validate` × 10 theme | cả 10 đều `OK` |
| `cargo build --release -p theme-ffi` | OK — ra `libtheme_ffi.so` cho **host x86_64 Linux** (chưa phải .so Android) |
| `cargo ndk ... build --release -p theme-ffi` (09/11, Giai đoạn 2) | ✅ OK — ra `libtheme_ffi.so` cho **arm64-v8a, armeabi-v7a, x86_64** (795 / 550 / 771 KB) |
| Binding UniFFI + nối Kotlin (09/11, Giai đoạn 2) | ✅ OK — `theme_ffi.kt` sinh ra + `ThemeEngine.kt`/`WidgetUpdater` gọi Rust thật; **đã kiểm chứng bằng biên dịch Kotlin trên CI** (run 42634197034) |

Hai lỗi thật đã bắt được nhờ test (đã sửa, test vẫn xanh):

1. `parse_color` đọc kênh màu sai vị trí (`#3366FF` thành `(51, 54, 102)`)
   → tương phản WCAG tính sai. Sửa chỉ số cặp hex bước 2.
2. `clone_builtin` sinh id bằng mili-giây → 2 lần nhân bản trong cùng 1 ms
   trùng id, đổi tên kiểm tra trùng bị lọt. Sửa thành id tăng dần
   `custom-<id gốc>-<số thứ tự>`.

## Giai đoạn 2 — tiến độ thật (09/11/2026, máy này)

| Bước | Kết quả |
|---|---|
| JDK 17 + Android SDK | ✅ Xong — OpenJDK 17.0.20.1; SDK: cmdline-tools, platform-tools 37.0.2, platform android-34, build-tools 34.0.0, NDK r26d; `adb` chạy được |
| `.so` Android (cargo-ndk) | ✅ Xong — `libtheme_ffi.so` cho arm64-v8a (795 KB), armeabi-v7a (550 KB), x86_64 (771 KB) vào `android/app/src/main/jniLibs/` (file `.so` không commit, build lại được bằng lệnh dưới) |
| Binding Kotlin (UniFFI) | ✅ Xong — `android/app/src/main/java/uniffi/theme_ffi/theme_ffi.kt` (1.210 dòng): `resolveThemeJson`, `validateColor`, `validateThemeJson` |
| Nối Kotlin với engine thật | ✅ Xong — `ThemeEngine.kt` gọi Rust qua UniFFI; `WidgetUpdater` đọc themeId từ DataStore, load JSON từ `assets/themes/`, gộp bằng Rust, lỗi thì về Mặc định; `onDeleted` xóa liên kết (BR-18) |
| APK đầu tiên (Gradle) | ✅ **Xong — build bằng GitHub Actions** (run 42634197034, commit `475860e`): `app-debug.apk` 16.431.059 byte, chứa `libtheme_ffi.so` cho cả 3 kiến trúc; Gradle trên máy này vẫn kẹt (xem dưới) |

### ✅ APK build bằng GitHub Actions — ⛔ Gradle trên máy này vẫn kẹt

Workflow `.github/workflows/build-apk.yml` (chạy tay bằng `workflow_dispatch`)
đã chạy xanh lần đầu ở run 42634197034 sau 3 lần sửa: (1) `setup-android`
mặc định cài gói `tools` đã bị Google bỏ → chỉ cài `platform-tools`;
(2) `tools/uniffi-gen` thiếu `[workspace]` nên cargo từ chối chạy trong
workspace; (3) thiếu icon launcher `mipmap/ic_launcher` nên AAPT fail.
APK debug tự ký bằng debug keystore, cài trực tiếp được.

Gradle trên máy này vẫn không build được: daemon cần client Java nối
qua TCP trên localhost, mà **mọi kết nối TCP do tiến trình Java mở đều
bị chặn** — đã kiểm chứng bằng chương trình Java tối giản. Tony đã chuyển
`other_tcp` sang **Ask** và duyệt ở giây 58, nhưng Gradle chỉ chờ ~60
giây nên vẫn trễ (daemon không nhận được kết nối nào rồi tự dừng).
Đường bấm thẻ từng cổng coi như không đi được; build APK cứ dùng CI.

Lưu ý khác khi build lại trên máy này: sdkmanager của Java không qua
được proxy egress (cần bật Basic auth cho HTTPS tunneling) và `/tmp`
chỉ có 512 MB — các gói SDK đã tải tay bằng curl vào
`~/android-sdk-downloads/` rồi giải nén vào `~/android-sdk/`.
Gradle cũng đã hạ heap xuống 768 MB trong `android/gradle.properties`
vì máy ít RAM trống (daemon 2 GB bị kill khi bắt đầu build).

Để build APK (Giai đoạn 2, trên máy Tony hoặc CI):

1. Cài JDK 17 + Android SDK (API 26–34, NDK cho cargo-ndk).
2. `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`
3. `cargo install cargo-ndk`
4. `cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o android/app/src/main/jniLibs build --release -p theme-ffi`
5. Sinh binding Kotlin — lưu ý: `uniffi_bindgen` 0.29.5 **không có binary
   CLI** (`cargo install uniffi_bindgen` báo "has no binaries"), nên dùng
   chương trình sinh nhỏ trong repo:
   `cargo run --release --manifest-path tools/uniffi-gen/Cargo.toml -- target/aarch64-linux-android/release/libtheme_ffi.so android/app/src/main/java`
6. `cd android && ./gradlew assembleDebug`

## Lộ trình 5 giai đoạn

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 1 | Mô hình theme, kiểm tra, gộp theme, CLI. Chưa đụng Android. | ✅ Xong (repo này) |
| 2 | FFI, sinh binding Kotlin, 1 widget mẫu dùng 2–3 theme có sẵn; build `.so` Android + APK đầu tiên. | ✅ Xong — APK đầu tiên build bằng CI (run 42634197034, 09/11/2026) |
| 3 | Màn hình chỉnh sửa (SC-05), undo/redo, xem trước live, lưu riêng từng widget. | Đang làm (bắt đầu 09/11/2026, trực tiếp từng bước nhỏ) |
| 4 | Material You thật, chế độ sáng/tối, nền do Rust vẽ (tiny-skia). | Chưa làm |
| 5 | Gói theme, nhập/xuất, chia sẻ (BR-20..BR-22). | Chưa làm |

## Giai đoạn 3 — kế hoạch + tiến độ (bắt đầu 09/11/2026)

Màn hình chỉnh sửa thật + ảnh nền riêng từng widget (Tony chốt 22:11
09/11: ảnh nền chọn lúc thêm widget, không gắn theo theme). Làm trực
tiếp từng bước nhỏ, mỗi bước kiểm chứng bằng CI (Gradle local vẫn kẹt
quyền Java TCP — xem mục Giai đoạn 2).

| Bước | Nội dung | Trạng thái |
|---|---|---|
| 1 | Nền tảng ảnh nền riêng từng widget: lưu ảnh theo appWidgetId, vẽ bitmap lên widget (RemoteViews). | ✅ Xong — CI run 42645086578 xanh, APK 16.431.395 byte |
| 2 | Trạng thái editor Kotlin: undo/redo (snapshot JSON), nháp, dùng FFI resolve/validate có sẵn. | ✅ Xong — CI run 42646281937 xanh |
| 3 | SC-05 editor thật: preview live + 4 tab Màu/Chữ/Hình khối/Nền + cảnh báo tương phản + Sửa nhanh + lưu (BR-04 nhân bản, BR-12 ghi đè). | ✅ Xong — CI run 42652633786 xanh, APK 16.480.547 byte (lỗi import `key` ở run 42647873013 đã sửa ở main 1e65bde) |
| 4 | Trang chính SC-02/SC-03/SC-06 + luồng cấu hình UC-01 (chọn theme + ảnh lúc thêm widget). | Đang làm |
| 4 | Trang chính SC-02/SC-03/SC-06 + luồng cấu hình UC-01 (chọn theme + ảnh lúc thêm widget). | Chưa làm |
| 5 | APK mới qua CI + Tony test trên máy thật. | Chưa làm |

## Quyết định đã chốt (09/11/2026, Tony trả lời)

1. **Mô hình kiếm tiền: miễn phí kèm theme trả phí** (freemium). Theme
   trả phí hiển thị trạng thái Khóa trong thư viện; mua theme là V2,
   V1 chưa làm thanh toán thật — chỉ làm cấu trúc `tier: free|paid`
   trong mô hình theme.
2. **Widget V1: đúng 4 loại — đồng hồ, lịch, ghi chú, pin.** Không thêm
   loại khác ở V1.
3. **Trình chỉnh sửa Nâng cao ngay từ V1.** Không làm chế độ Cơ bản;
   editor mặc định đầy đủ 4 tab Màu / Chữ / Hình khối / Nền.
4. **Android tối thiểu: API 26 / Android 8.0.** Đây là mức thấp nhất mà
   Glance + các tính năng widget trong BA hỗ trợ (minSdk = 26).
   Material You chỉ có từ API 31 (Android 12+).
5. **Có thu thập thống kê.** Mặc định TẮT, người dùng bật trong Cài đặt
   (SC-08) mới thu; chỉ thu các sự kiện đã định trong BA
   (`widget_added`, `theme_applied`, `editor_opened`/`editor_closed`,
   `contrast_warning`, `theme_deleted`, `error_shown`), không dữ liệu
   cá nhân, không định danh. Quyền Internet chỉ bật khi người dùng đồng
   ý thống kê (NFR-09).
6. **Tên app, biểu tượng và bộ theme có sẵn do Muse thiết kế.**
   Tên app giữ là "Theme Widget"; 10 theme có sẵn giữ nguyên tên.
