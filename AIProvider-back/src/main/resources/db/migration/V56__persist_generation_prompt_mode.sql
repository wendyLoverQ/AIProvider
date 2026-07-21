ALTER TABLE c_ComfyUiTasks
  ADD COLUMN PromptMode VARCHAR(16) NOT NULL DEFAULT 'tags' AFTER PromptSchemeName;

ALTER TABLE c_LocalGeneratedImages
  ADD COLUMN PromptSchemeName VARCHAR(255) NULL AFTER WorkflowName,
  ADD COLUMN PromptMode VARCHAR(16) NOT NULL DEFAULT 'tags' AFTER PromptSchemeName;

ALTER TABLE c_GeneratedAssets
  ADD COLUMN PromptSchemeName VARCHAR(255) NULL AFTER WorkflowId,
  ADD COLUMN PromptMode VARCHAR(16) NOT NULL DEFAULT 'tags' AFTER PromptSchemeName;

UPDATE c_ComfyUiTasks t
SET t.PromptMode = COALESCE((
  SELECT s.PromptMode
  FROM c_ComfyParameterSchemes s
  WHERE CONVERT(s.Name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = CONVERT(t.PromptSchemeName USING utf8mb4) COLLATE utf8mb4_0900_ai_ci
  ORDER BY s.UpdatedAt DESC
  LIMIT 1
), 'tags');

ALTER TABLE c_ComfyUiTasks ADD CONSTRAINT CK_ComfyUiTasks_PromptMode CHECK (PromptMode IN ('tags','prose'));
ALTER TABLE c_LocalGeneratedImages ADD CONSTRAINT CK_LocalGeneratedImages_PromptMode CHECK (PromptMode IN ('tags','prose'));
ALTER TABLE c_GeneratedAssets ADD CONSTRAINT CK_GeneratedAssets_PromptMode CHECK (PromptMode IN ('tags','prose'));
