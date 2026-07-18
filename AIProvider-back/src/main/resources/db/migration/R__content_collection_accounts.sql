CREATE TABLE IF NOT EXISTS c_ContentCollectionAccounts (
  Id BIGINT NOT NULL AUTO_INCREMENT,
  Platform VARCHAR(30) NOT NULL,
  DisplayName VARCHAR(100) NOT NULL,
  AdapterType VARCHAR(40) NOT NULL,
  CredentialEncrypted TEXT NOT NULL,
  CredentialHint VARCHAR(20) NULL,
  Enabled BOOLEAN NOT NULL DEFAULT TRUE,
  LegacySourceId BIGINT NULL,
  CreatedAt DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UpdatedAt DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (Id),
  UNIQUE KEY UX_ContentCollectionAccounts_LegacySource (LegacySourceId),
  KEY IX_ContentCollectionAccounts_PlatformEnabled (Platform, Enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS c_ContentSourceCollectionAccounts (
  SourceId BIGINT NOT NULL,
  CollectionAccountId BIGINT NOT NULL,
  CreatedAt DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (SourceId),
  KEY IX_ContentSourceCollectionAccounts_Account (CollectionAccountId),
  CONSTRAINT FK_ContentSourceCollectionAccounts_Source FOREIGN KEY (SourceId) REFERENCES c_ContentSources(Id) ON DELETE CASCADE,
  CONSTRAINT FK_ContentSourceCollectionAccounts_Account FOREIGN KEY (CollectionAccountId) REFERENCES c_ContentCollectionAccounts(Id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO c_ContentCollectionAccounts(Platform,DisplayName,AdapterType,CredentialEncrypted,CredentialHint,Enabled,LegacySourceId)
SELECT Platform,CONCAT(Name,' 采集账号'),AdapterType,CredentialEncrypted,CredentialHint,TRUE,Id
FROM c_ContentSources
WHERE CredentialEncrypted IS NOT NULL;

INSERT IGNORE INTO c_ContentSourceCollectionAccounts(SourceId,CollectionAccountId)
SELECT s.Id,a.Id
FROM c_ContentSources s
JOIN c_ContentCollectionAccounts a ON a.LegacySourceId=s.Id
WHERE s.CredentialEncrypted IS NOT NULL;

UPDATE c_ContentPublications
SET ErrorMessage='旧版本只保存了 Playwright 错误外壳，原始失败原因未保留；重新执行后将记录具体失败阶段和完整原因。'
WHERE Status='FAILED' AND ErrorMessage='小红书网页发布失败：Error {';
