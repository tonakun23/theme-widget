# android/ — Khung Kotlin (Giai đoạn 1)

Khung thật nhưng **chưa từng biên dịch**: máy làm Giai đoạn 1 không có
JDK / Android SDK nên không chạy được `./gradlew`. Xem `README.md` gốc
mục "APK chưa build được trên máy này".

## Có gì

- `AndroidManifest.xml` — MainActivity + ThemeEditorActivity (SC-05) +
  4 receiver widget, không xin quyền Internet (NFR-09).
- `res/xml/*_widget_info.xml` — appwidget-provider cho đồng hồ, lịch,
  ghi chú, pin; đều trỏ `android:configure` về ThemeEditorActivity (UC-01).
- `widget/ThemeApplier.kt` — token → lệnh RemoteViews, ghi rõ giới hạn
  RemoteViews (không font riêng, gradient hạn chế, bo góc động cần API 31).
- `widget/WidgetProvider.kt` + `WidgetUpdater.kt` — provider + cập nhật
  widget, fallback theme Mặc định khi lỗi (E-04/E-06).
- `editor/ThemeEditorActivity.kt` — khung Compose/Material 3 cho SC-05
  (4 tab + xem trước live + W-01/W-02 làm ở Giai đoạn 3).
- `store/WidgetThemeStore.kt` — DataStore chỉ lưu `appWidgetId → themeId`
  (+ ghi đè widget dạng JSON, BR-12); nội dung theme do Rust ghi file.
- `app/build.gradle.kts` — Glance, Compose, DataStore, JNA, WorkManager.

## TODO — Giai đoạn 2

Cài JDK 17 + Android SDK + NDK rồi làm theo 6 bước trong `README.md` gốc.
Sau đó kiểm chứng: thêm widget thật trên máy → chọn theme → widget đổi màu.
