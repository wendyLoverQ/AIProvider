-- V67: 历史行情任务来源模式与缺口区段数
-- 不修改 V66 已有列，只新增列。
-- q_market_sync_task 新增 SourceMode / CurrentSourceFile / PlannedFileCount / CompletedFileCount / GapSegmentCount
-- q_market_dataset 新增 GapSegmentCount

ALTER TABLE q_market_sync_task
    ADD COLUMN SourceMode          VARCHAR(32)  NULL AFTER Status,
    ADD COLUMN CurrentSourceFile   VARCHAR(255) NULL AFTER SourceMode,
    ADD COLUMN PlannedFileCount    INT          NULL AFTER CurrentSourceFile,
    ADD COLUMN CompletedFileCount  INT          NOT NULL DEFAULT 0 AFTER PlannedFileCount,
    ADD COLUMN GapSegmentCount     INT          NOT NULL DEFAULT 0 AFTER GapCount;

ALTER TABLE q_market_dataset
    ADD COLUMN GapSegmentCount INT NOT NULL DEFAULT 0 AFTER GapCount;
