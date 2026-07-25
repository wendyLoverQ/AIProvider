package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDataGapRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import com.aiprovider.quant.market.model.KlineInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据集校验服务。
 *
 * 负责缺口检测、数据集统计信息和状态计算。
 * 校验采用按 openTime 升序流式遍历，不一次性加载全部数据。
 *
 * 数据集级状态只校验 earliestOpenTime ～ latestOpenTime 区间，
 * 不代表 Binance 上线以来所有数据都已下载。
 */
public class MarketDatasetValidationService {

    private static final Logger log = LoggerFactory.getLogger(MarketDatasetValidationService.class);
    private static final int STREAM_BATCH = 5000;

    private final MarketCandleRepository candleRepository;
    private final MarketDatasetRepository datasetRepository;
    private final MarketDataGapRepository gapRepository;
    private final SyncUnitOfWork unitOfWork;

    public MarketDatasetValidationService(MarketCandleRepository candleRepository,
                                          MarketDatasetRepository datasetRepository,
                                          MarketDataGapRepository gapRepository,
                                          SyncUnitOfWork unitOfWork) {
        this.candleRepository = candleRepository;
        this.datasetRepository = datasetRepository;
        this.gapRepository = gapRepository;
        this.unitOfWork = unitOfWork;
    }

    /**
     * 校验数据集：重新计算统计信息、缺口和状态。
     *
     * 删除旧 gap、写入新 gap、更新 dataset 统计信息和状态，全部在同一事务内完成。
     *
     * @param datasetId 数据集 ID
     * @param interval  K 线周期
     * @param taskId    执行校验的任务 ID
     * @return 校验结果
     */
    public ValidationResult validateDataset(long datasetId, KlineInterval interval, String taskId) {
        return unitOfWork.execute(() -> {
            long durationMs = interval.durationMillis();

            // 删除旧 gap
            gapRepository.deleteByDataset(datasetId);

            // 流式遍历 openTime，计算 gap
            List<MarketDataGap> gaps = new ArrayList<>();
            Long previousOpenTime = null;
            long cursor = 0;
            long candleCount = 0;
            Long earliest = null;
            Long latest = null;

            while (true) {
                List<Long> openTimes = candleRepository.streamOpenTimesAscending(datasetId, STREAM_BATCH, cursor);
                if (openTimes == null || openTimes.isEmpty()) {
                    break;
                }
                for (long openTime : openTimes) {
                    if (earliest == null) {
                        earliest = openTime;
                    }
                    latest = openTime;
                    candleCount++;

                    if (previousOpenTime != null) {
                        long expected = previousOpenTime + durationMs;
                        if (openTime > expected) {
                            long missingCount = (openTime - expected) / durationMs;
                            MarketDataGap gap = new MarketDataGap();
                            gap.setDatasetId(datasetId);
                            gap.setStartOpenTime(Instant.ofEpochMilli(expected));
                            gap.setEndOpenTimeExclusive(Instant.ofEpochMilli(openTime));
                            gap.setMissingCount(missingCount);
                            gap.setDetectedByTaskId(taskId);
                            gap.setDetectedAt(Instant.now());
                            gaps.add(gap);
                        }
                    }
                    previousOpenTime = openTime;
                    cursor = openTime;
                }
                // 推进游标到最后一个 openTime 之后
                cursor = cursor + 1;
            }

            // 批量写入 gap
            if (!gaps.isEmpty()) {
                gapRepository.insertBatch(gaps);
            }

            // 计算缺失 K 线总根数（gapCount = Σ gap.missingCount，不是区段数量）
            long totalMissingCount = 0;
            for (MarketDataGap gap : gaps) {
                totalMissingCount += gap.getMissingCount();
            }

            // 更新数据集统计信息
            MarketDataset dataset = datasetRepository.findById(datasetId);
            if (dataset == null) {
                throw new IllegalStateException("数据集不存在: " + datasetId);
            }
            dataset.setCandleCount(candleCount);
            dataset.setGapCount(totalMissingCount);
            dataset.setGapSegmentCount(gaps.size());
            dataset.setEarliestOpenTime(earliest != null ? Instant.ofEpochMilli(earliest) : null);
            dataset.setLatestOpenTime(latest != null ? Instant.ofEpochMilli(latest) : null);
            dataset.setLastValidatedAt(Instant.now());

            if (candleCount == 0) {
                dataset.setStatus(MarketDatasetStatus.EMPTY);
            } else if (gaps.isEmpty()) {
                dataset.setStatus(MarketDatasetStatus.CONTIGUOUS);
            } else {
                dataset.setStatus(MarketDatasetStatus.GAPPED);
            }

            // 计算区间内预期数量
            if (earliest != null && latest != null) {
                long expected = (latest - earliest) / durationMs + 1;
                dataset.setExpectedInsideRange(expected);
            } else {
                dataset.setExpectedInsideRange(0);
            }

            datasetRepository.updateStats(dataset);

            log.info("operation=validate-dataset datasetId={} candleCount={} gapCount={} gapSegments={} status={} taskId={}",
                    datasetId, candleCount, totalMissingCount, gaps.size(), dataset.getStatus(), taskId);

            ValidationResult result = new ValidationResult();
            result.earliestOpenTimeMs = earliest;
            result.latestOpenTimeMs = latest;
            result.candleCount = candleCount;
            result.gapCount = totalMissingCount;
            result.gapSegmentCount = gaps.size();
            result.gaps = gaps;
            result.status = dataset.getStatus();
            return result;
        });
    }

    /**
     * 校验结果。
     *
     * gapCount = 缺失 K 线根数（Σ gap.missingCount）
     * gapSegmentCount = 缺口区段数量（gaps.size()）
     * gaps = 缺口列表，供调用方计算任务级缺口
     */
    public static class ValidationResult {
        public Long earliestOpenTimeMs;
        public Long latestOpenTimeMs;
        public long candleCount;
        public long gapCount;
        public int gapSegmentCount;
        public List<MarketDataGap> gaps;
        public MarketDatasetStatus status;
    }
}
