# Media Deletion Architecture — Data Sort Gallery

## 1. Overview
Data Sort Gallery adheres to Android Scoped Storage guidelines for Android 11+ (API 30+).
- **Trash Operations**: Uses `MediaStore.createTrashRequest` to request user consent via system dialogs.
- **Permanent Deletion**: Uses `MediaStore.createDeleteRequest`.
- **Database Synchronization**: Room database updates reflect actual storage deletion status via safe operation journals.
