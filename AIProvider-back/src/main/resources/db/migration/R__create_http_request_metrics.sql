CREATE TABLE IF NOT EXISTS `c_HttpRequestMetrics` (
  `Id` BIGINT NOT NULL AUTO_INCREMENT,
  `Method` VARCHAR(10) NOT NULL,
  `Route` VARCHAR(255) NOT NULL,
  `StatusCode` INT NOT NULL,
  `DurationMs` BIGINT NOT NULL,
  `CreatedAt` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`Id`),
  KEY `IX_c_HttpRequestMetrics_CreatedAt` (`CreatedAt`),
  KEY `IX_c_HttpRequestMetrics_CreatedAt_StatusCode` (`CreatedAt`, `StatusCode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
