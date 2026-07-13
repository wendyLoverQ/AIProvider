ALTER TABLE ComfyParameterSchemes
  DROP INDEX IX_ComfyParameterSchemes_WorkflowId,
  DROP COLUMN WorkflowId;
