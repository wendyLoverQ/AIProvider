CREATE TABLE c_PlatformAccounts (
  Id BIGINT NOT NULL AUTO_INCREMENT,
  Platform VARCHAR(32) NOT NULL,
  AccountKind VARCHAR(24) NOT NULL,
  DisplayName VARCHAR(100) NOT NULL,
  AccountHandle VARCHAR(200) NULL,
  AdapterType VARCHAR(64) NOT NULL,
  PublicConfigJson JSON NULL,
  Enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ConnectionStatus VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED',
  CredentialHint VARCHAR(100) NULL,
  LastValidatedAt DATETIME(6) NULL,
  LastConnectedAt DATETIME(6) NULL,
  LastErrorCode VARCHAR(80) NULL,
  LastErrorMessage VARCHAR(1000) NULL,
  LegacySourceType VARCHAR(48) NULL,
  LegacySourceId BIGINT NULL,
  ArchivedAt DATETIME(6) NULL,
  CreatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (Id),
  UNIQUE KEY UX_PlatformAccounts_Legacy (LegacySourceType, LegacySourceId),
  KEY IX_PlatformAccounts_PlatformEnabled (Platform, Enabled, ArchivedAt),
  KEY IX_PlatformAccounts_KindEnabled (AccountKind, Enabled, ArchivedAt),
  KEY IX_PlatformAccounts_StatusUpdated (ConnectionStatus, UpdatedAt)
);

CREATE TABLE c_PlatformAccountSecrets (
  Id BIGINT NOT NULL AUTO_INCREMENT,
  AccountId BIGINT NOT NULL,
  SecretType VARCHAR(32) NOT NULL,
  EncryptedValue LONGTEXT NOT NULL,
  SecretHint VARCHAR(100) NULL,
  SecretVersion INT NOT NULL DEFAULT 1,
  LastValidatedAt DATETIME(6) NULL,
  CreatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (Id),
  UNIQUE KEY UX_PlatformAccountSecrets_AccountType (AccountId, SecretType),
  CONSTRAINT FK_PlatformAccountSecrets_Account FOREIGN KEY (AccountId) REFERENCES c_PlatformAccounts(Id)
);

ALTER TABLE c_TwitterAccounts ADD COLUMN PlatformAccountId BIGINT NULL AFTER Id;
ALTER TABLE c_TwitterAccounts ADD KEY IX_TwitterAccounts_PlatformAccount (PlatformAccountId);
ALTER TABLE c_TwitterAccounts ADD CONSTRAINT FK_TwitterAccounts_PlatformAccount FOREIGN KEY (PlatformAccountId) REFERENCES c_PlatformAccounts(Id);

ALTER TABLE c_ContentCollectionAccounts ADD COLUMN PlatformAccountId BIGINT NULL AFTER Id;
ALTER TABLE c_ContentCollectionAccounts ADD KEY IX_ContentCollectionAccounts_PlatformAccount (PlatformAccountId);
ALTER TABLE c_ContentCollectionAccounts ADD CONSTRAINT FK_ContentCollectionAccounts_PlatformAccount FOREIGN KEY (PlatformAccountId) REFERENCES c_PlatformAccounts(Id);

ALTER TABLE c_ContentAccounts ADD COLUMN PlatformAccountId BIGINT NULL AFTER Id;
ALTER TABLE c_ContentAccounts ADD KEY IX_ContentAccounts_PlatformAccount (PlatformAccountId);
ALTER TABLE c_ContentAccounts ADD CONSTRAINT FK_ContentAccounts_PlatformAccount FOREIGN KEY (PlatformAccountId) REFERENCES c_PlatformAccounts(Id);

ALTER TABLE c_ContentOperationSettings ADD COLUMN PlatformAccountId BIGINT NULL AFTER Id;
ALTER TABLE c_ContentOperationSettings ADD KEY IX_ContentOperationSettings_PlatformAccount (PlatformAccountId);
ALTER TABLE c_ContentOperationSettings ADD CONSTRAINT FK_ContentOperationSettings_PlatformAccount FOREIGN KEY (PlatformAccountId) REFERENCES c_PlatformAccounts(Id);
