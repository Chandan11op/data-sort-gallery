# Startup Crash Report — Data Sort Gallery

## 1. Crash Overview
- **Symptom**: App launched, displayed initialization error, and exited immediately to home screen.
- **Root Cause**: Room database schema version mismatch. Entity primary key was changed from `id: Long` to `uniqueId: String`, but database version remained `1` without any defined migration, causing Room to fail validation upon opening the local SQLite database.

## 2. Resolution
- Incremented Room database version to `2`.
- Implemented `MIGRATION_1_2` to safely reconstruct the `media` table, generating stable `uniqueId` values (`IMAGE_`, `VIDEO_`, `AUDIO_` prefixes) and preserving user metadata (favorites, trash state, etc.).
- Removed destructive migration fallback.
