CREATE TABLE c_PromptOptionsV2 (
  Id VARCHAR(64) NOT NULL PRIMARY KEY,
  Category VARCHAR(40) NOT NULL,
  Name VARCHAR(100) NOT NULL,
  Prompt VARCHAR(500) NOT NULL,
  Type VARCHAR(16) NOT NULL,
  ReverseId VARCHAR(64) NULL,
  SortOrder INT NOT NULL DEFAULT 0,
  Enabled BOOLEAN NOT NULL DEFAULT TRUE,
  AllowMultiple BOOLEAN NOT NULL DEFAULT FALSE,
  CreatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY IX_PromptOptionsV2_Category (Category, Enabled, SortOrder),
  KEY IX_PromptOptionsV2_Type (Type, Enabled, Category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO c_PromptOptionsV2(Id, Category, Name, Prompt, Type, SortOrder, Enabled, AllowMultiple)
SELECT Id, Category, Name, PositivePrompt, 'positive', SortOrder, Enabled, AllowMultiple
FROM c_PromptOptions;

INSERT INTO c_PromptOptionsV2(Id, Category, Name, Prompt, Type, SortOrder, Enabled, AllowMultiple)
SELECT CONCAT('neg_', LEFT(SHA2(Id, 256), 32)), Category, CONCAT(Name, '（反向）'), NegativePrompt, 'negative', SortOrder, Enabled, FALSE
FROM c_PromptOptions
WHERE NegativePrompt IS NOT NULL AND TRIM(NegativePrompt) <> '';

UPDATE c_PromptOptionsV2 p
JOIN c_PromptOptions old ON old.Id=p.Id
SET p.ReverseId=CONCAT('neg_', LEFT(SHA2(old.Id, 256), 32))
WHERE p.Type='positive' AND old.NegativePrompt IS NOT NULL AND TRIM(old.NegativePrompt) <> '';

ALTER TABLE c_PromptOptionsV2
  ADD CONSTRAINT FK_PromptOptionsV2_Reverse FOREIGN KEY (ReverseId) REFERENCES c_PromptOptionsV2(Id) ON DELETE SET NULL;

DROP TABLE c_PromptOptions;
RENAME TABLE c_PromptOptionsV2 TO c_PromptOptions;
