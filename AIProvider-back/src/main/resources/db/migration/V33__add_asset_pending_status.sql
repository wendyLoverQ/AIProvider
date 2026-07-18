ALTER TABLE c_GeneratedAssets
  ADD COLUMN Status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER AssetType,
  ADD INDEX IX_GeneratedAssets_Platform_Status_CreatedAt (Platform, Status, CreatedAt DESC);