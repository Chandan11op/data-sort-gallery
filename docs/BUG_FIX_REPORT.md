# Bug Fix Report — Data Sort Gallery

## 1. Duplicate Deletion Crash Fix
- **Fix**: Implemented stable composite primary keys in `MediaEntity` (`mediaType + id`) to eliminate ID collisions between images, videos, and audio.
- **Fix**: Rebuilt media deletion workflow supporting API-compliant trash/delete requests and safe exception handling.

## 2. Duplicate Detection Engine Upgrade
- **Fix**: Replaced size/resolution grouping with 4-level robust duplicate and similarity detection (SHA-256 exact matching, perceptual hashing, and video analysis).

## 3. Navigation Mappings Fix
- **Fix**: Updated `MainScreen.kt` to route Explore categories (Screenshots, Downloads, Large Files, Recycle Bin) to dedicated filtered views.
