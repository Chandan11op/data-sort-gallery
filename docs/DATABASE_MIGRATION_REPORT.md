# Database Migration Report — Data Sort Gallery

## 1. Migration Specification: Version 1 to Version 2
- **Objective**: Transition primary key from `id: Long` to `uniqueId: String` to prevent ID collisions between images, videos, and audio.
- **Migration Logic (`MIGRATION_1_2`)**:
  1. Creates temporary table `media_new` with `uniqueId` as primary key.
  2. Copies existing records from `media` to `media_new`, calculating `uniqueId` as `mediaType` prefix + `id`.
  3. Drops old `media` table.
  4. Renames `media_new` to `media`.
- **Result**: Zero data loss, robust schema upgrade, and preserved user state.
