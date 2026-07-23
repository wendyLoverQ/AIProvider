DROP INDEX IX_AsrTranscriptionRecords_CharacterStatus ON c_AsrTranscriptionRecords;
ALTER TABLE c_AsrTranscriptionRecords
  DROP COLUMN CharacterId,
  DROP COLUMN CharacterNameSnapshot;
