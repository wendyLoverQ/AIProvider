ALTER TABLE c_GeneratedAssets
    ADD COLUMN AssetType VARCHAR(32) NOT NULL DEFAULT 'image' AFTER Height,
    ADD COLUMN MimeType VARCHAR(100) NULL AFTER AssetType,
    ADD KEY IX_GeneratedAssets_Platform_AssetType_CreatedAt (Platform, AssetType, CreatedAt DESC);
