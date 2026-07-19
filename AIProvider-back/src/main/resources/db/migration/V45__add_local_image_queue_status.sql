ALTER TABLE c_LocalGeneratedImages
  ADD COLUMN Status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER FileName,
  ADD INDEX IX_LocalGeneratedImages_Platform_Status_UpdatedAt (Platform, Status, UpdatedAt DESC);
