# Android Permission Audit — Data Sort Gallery

## 1. Permission Matrix by Android Version
- **Android 8–9 (API 26–28)**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`
- **Android 10 (API 29)**: Scoped storage with legacy support flag.
- **Android 11–12 (API 30–32)**: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`.
- **Android 13+ (API 33+)**: Granular permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, and `READ_MEDIA_VISUAL_USER_SELECTED` for partial access).

## 2. Enforcement
- Feature-specific permission checks replace blanket ANY checks.
