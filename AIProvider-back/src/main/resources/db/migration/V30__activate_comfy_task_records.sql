ALTER TABLE c_ComfyUiTasks
  ADD COLUMN PromptId VARCHAR(100) NULL AFTER Id,
  ADD COLUMN WorkflowId VARCHAR(100) NULL AFTER WorkflowName,
  ADD COLUMN PromptSchemeName VARCHAR(255) NULL AFTER WorkflowId,
  ADD COLUMN InputSha256 CHAR(64) NULL AFTER InputFile,
  ADD COLUMN InputFileName VARCHAR(255) NULL AFTER InputSha256,
  ADD COLUMN ResultPathsJson JSON NULL AFTER OutputMime,
  ADD COLUMN CompletedAt DATETIME(3) NULL AFTER ErrorMessage,
  ADD UNIQUE KEY UK_ComfyUiTasks_PromptId (PromptId),
  ADD KEY IX_ComfyUiTasks_Duplicate (WorkflowId, InputSha256, Status);
