CREATE TABLE c_RemoteCodexConversations (
  Id CHAR(36) NOT NULL,
  CodexThreadId VARCHAR(100) NULL,
  Title VARCHAR(255) NOT NULL,
  Status VARCHAR(20) NOT NULL,
  ErrorMessage TEXT NULL,
  CreatedAt DATETIME(3) NOT NULL,
  UpdatedAt DATETIME(3) NOT NULL,
  PRIMARY KEY (Id),
  KEY IX_RemoteCodexConversations_UpdatedAt (UpdatedAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE c_RemoteCodexMessages (
  Id BIGINT NOT NULL AUTO_INCREMENT,
  ConversationId CHAR(36) NOT NULL,
  Role VARCHAR(20) NOT NULL,
  Content LONGTEXT NOT NULL,
  CreatedAt DATETIME(3) NOT NULL,
  PRIMARY KEY (Id),
  KEY IX_RemoteCodexMessages_Conversation (ConversationId, Id),
  CONSTRAINT FK_RemoteCodexMessages_Conversation FOREIGN KEY (ConversationId)
    REFERENCES c_RemoteCodexConversations (Id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
