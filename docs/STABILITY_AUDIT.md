# Stability Audit Report — Data Sort Gallery

## 1. Executive Summary
This document records the comprehensive stability audit, crash investigation, security review, and architectural enhancements performed on **Data Sort Gallery**.

## 2. Discovered Issues & Root Cause Analysis

### Issue 1: Application Exit / Crash on Duplicate Deletion
- **Severity**: CRITICAL (Fatal Exception / Exit to Home)
- **Affected Files**: `DuplicatesScreen.kt`, `GalleryViewModel.kt`, `MediaRepositoryImpl.kt`, `MediaScanner.kt`
- **Root Cause**: 
  1. **Scoped Storage Security Exception**: On Android 11+ (API 30+), deleting or moving media files in `MediaStore` without using `MediaStore.createTrashRequest` / `MediaStore.createDeleteRequest` (which returns an `IntentSender` for user authorization) results in an unhandled `SecurityException`.
  2. **Media Database ID Collisions**: `MediaScanner` assigned raw `MediaStore` IDs directly for both images (`id = id`) and videos (`id = id`), causing primary key collision (`UNIQUE constraint failed: media.id`) when an image and video shared the same numeric ID.
  3. **Inadequate Duplicate Detection**: Grouping candidates solely by file size and resolution caused false positives.

### Issue 2: Incorrect Navigation Screen Mappings
- **Severity**: MODERATE (UX Bug)
- **Affected Files**: `MainScreen.kt`
- **Root Cause**: Shortcuts like Screenshots, Downloads, and Large Files incorrectly navigated to `VideosScreen`.

### Issue 3: Destructive Database Migrations
- **Severity**: HIGH (Data Loss Risk)
- **Affected Files**: `DatabaseModule.kt`
- **Root Cause**: Used `.fallbackToDestructiveMigration()`, risking user index loss on schema updates.

---
*Audit conducted on branch `bugfix/full-stability-audit`.*
