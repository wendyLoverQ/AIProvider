CREATE TABLE c_PromptTranslationCache (
  Id BIGINT NOT NULL AUTO_INCREMENT,
  SourceSha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  SourceLength INT NOT NULL,
  TargetLanguage VARCHAR(16) NOT NULL,
  Provider VARCHAR(64) NOT NULL,
  TranslatedText MEDIUMTEXT NOT NULL,
  HitCount BIGINT NOT NULL DEFAULT 0,
  LastHitAt DATETIME(6) NULL,
  CreatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (Id),
  UNIQUE KEY UK_PromptTranslationCache_Source (SourceSha256, SourceLength, TargetLanguage, Provider),
  KEY IX_PromptTranslationCache_LastHitAt (LastHitAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
