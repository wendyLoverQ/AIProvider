ALTER TABLE c_ComfyUiTasks
  ADD COLUMN MainModel VARCHAR(1000) NULL AFTER NegativePrompt;

ALTER TABLE c_GeneratedAssets
  ADD COLUMN MainModel VARCHAR(1000) NULL AFTER NegativePrompt;
