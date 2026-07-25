package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.model.MarketDataSnapshot;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.KlineInterval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Validates and loads a complete, immutable historical candle range. */
public final class MarketDataSnapshotService {
    private final MarketDatasetRepository datasetRepository;
    private final MarketCandleRepository candleRepository;
    private final int maxSnapshotCandles;

    public MarketDataSnapshotService(MarketDatasetRepository datasetRepository,
                                     MarketCandleRepository candleRepository,
                                     int maxSnapshotCandles) {
        this.datasetRepository = datasetRepository;
        this.candleRepository = candleRepository;
        this.maxSnapshotCandles = maxSnapshotCandles;
    }

    public MarketDataSnapshot load(long datasetId, Instant startOpenTimeInclusive,
                                   Instant endOpenTimeExclusive) {
        if (datasetId <= 0 || startOpenTimeInclusive == null || endOpenTimeExclusive == null
                || !startOpenTimeInclusive.isBefore(endOpenTimeExclusive)) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_REQUEST_INVALID, "Invalid snapshot request");
        }
        MarketDataset dataset = datasetRepository.findById(datasetId);
        if (dataset == null) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATASET_NOT_FOUND,
                    "datasetId=" + datasetId + " not found");
        }
        validateDataset(dataset);
        KlineInterval interval = dataset.getInterval();
        if (!interval.alignOpenTime(startOpenTimeInclusive).equals(startOpenTimeInclusive)
                || !interval.alignOpenTime(endOpenTimeExclusive).equals(endOpenTimeExclusive)) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_RANGE_NOT_ALIGNED,
                    "datasetId=" + datasetId + " range is not aligned");
        }
        long start = startOpenTimeInclusive.toEpochMilli();
        long end = endOpenTimeExclusive.toEpochMilli();
        long duration = interval.durationMillis();
        long lastRequired;
        try {
            lastRequired = Math.subtractExact(end, duration);
        } catch (ArithmeticException ex) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_RANGE_NOT_COVERED,
                    "datasetId=" + datasetId + " range overflows interval boundary");
            return null;
        }
        if (start < dataset.getEarliestOpenTime().toEpochMilli()
                || lastRequired > dataset.getLatestOpenTime().toEpochMilli()) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_RANGE_NOT_COVERED,
                    "datasetId=" + datasetId + " range is not covered");
        }
        long expected;
        try {
            expected = Math.subtractExact(end, start) / duration;
        } catch (ArithmeticException ex) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_TOO_LARGE,
                    "datasetId=" + datasetId + " range is too large");
            return null;
        }
        if (expected <= 0 || expected > maxSnapshotCandles || expected > Integer.MAX_VALUE - 1) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_TOO_LARGE,
                    "datasetId=" + datasetId + " expectedCount=" + expected);
        }
        long actual = candleRepository.countRangeExclusive(datasetId, start, end);
        if (actual != expected) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_COUNT_MISMATCH,
                    "datasetId=" + datasetId + " expectedCount=" + expected + " actualCount=" + actual
                            + " start=" + start + " end=" + end);
        }
        List<HistoricalCandle> candles = candleRepository.findRangeAscending(datasetId, start, end,
                (int) expected + 1);
        if (candles == null) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATA_INVALID,
                    "datasetId=" + datasetId + " repository returned null");
        }
        if (candles.size() > expected) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_QUERY_OVERFLOW,
                    "datasetId=" + datasetId + " expectedCount=" + expected + " actualCount=" + candles.size());
        }
        if (candles.size() < expected) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_COUNT_MISMATCH,
                    "datasetId=" + datasetId + " expectedCount=" + expected + " actualCount=" + candles.size());
        }
        validateCandles(dataset, candles, startOpenTimeInclusive, duration, endOpenTimeExclusive);
        return new MarketDataSnapshot(datasetId, dataset.getProvider(), dataset.getMarketType(),
                dataset.getDataType(), dataset.getSymbol(), interval, startOpenTimeInclusive,
                endOpenTimeExclusive, expected, candles.size(), dataset.getLastValidatedAt(),
                dataset.getLastSyncTaskId(), candles);
    }

    private void validateDataset(MarketDataset d) {
        if (d.getDataType() != MarketDataType.CANDLE || d.getProvider() == null || d.getMarketType() == null
                || d.getSymbol() == null || d.getSymbol().trim().isEmpty() || d.getInterval() == null
                || !d.getInterval().isFixedDuration()) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATASET_INVALID, "datasetId=" + d.getId() + " invalid");
        }
        if (d.getStatus() != MarketDatasetStatus.CONTIGUOUS || d.getGapCount() != 0
                || d.getGapSegmentCount() != 0 || d.getEarliestOpenTime() == null
                || d.getLatestOpenTime() == null || d.getCandleCount() <= 0 || d.getLastValidatedAt() == null) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATASET_NOT_READY, "datasetId=" + d.getId() + " not ready");
        }
    }

    private void validateCandles(MarketDataset d, List<HistoricalCandle> candles, Instant start, long duration,
                                 Instant end) {
        Instant expectedOpen = start;
        for (int i = 0; i < candles.size(); i++) {
            HistoricalCandle c = candles.get(i);
            if (c == null || c.getDatasetId() != d.getId() || c.getProvider() != d.getProvider()
                    || c.getMarketType() != d.getMarketType() || !d.getSymbol().equals(c.getSymbol())
                    || c.getInterval() != d.getInterval() || !expectedOpen.equals(c.getOpenTime())
                    || c.getCloseTime() == null || !c.getCloseTime().equals(expectedOpen.plusMillis(duration - 1))
                    || c.getOpenPrice() == null || c.getHighPrice() == null || c.getLowPrice() == null
                    || c.getClosePrice() == null || c.getVolume() == null || c.getQuoteVolume() == null
                    || c.getOpenPrice().compareTo(BigDecimal.ZERO) <= 0
                    || c.getHighPrice().compareTo(BigDecimal.ZERO) <= 0 || c.getLowPrice().compareTo(BigDecimal.ZERO) <= 0
                    || c.getClosePrice().compareTo(BigDecimal.ZERO) <= 0 || c.getVolume().compareTo(BigDecimal.ZERO) < 0
                    || c.getQuoteVolume().compareTo(BigDecimal.ZERO) < 0 || c.getTradeCount() < 0
                    || c.getHighPrice().compareTo(c.getOpenPrice()) < 0
                    || c.getHighPrice().compareTo(c.getClosePrice()) < 0
                    || c.getLowPrice().compareTo(c.getOpenPrice()) > 0
                    || c.getLowPrice().compareTo(c.getClosePrice()) > 0
                    || c.getHighPrice().compareTo(c.getLowPrice()) < 0) {
                fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATA_INVALID,
                        "datasetId=" + d.getId() + " invalid candle index=" + i);
            }
            try {
                expectedOpen = expectedOpen.plusMillis(duration);
            } catch (ArithmeticException ex) {
                fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATA_INVALID, "candle time overflow");
            }
        }
        if (!expectedOpen.equals(end)) {
            fail(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATA_INVALID, "candle range does not end at request boundary");
        }
    }

    private static void fail(MarketDataSnapshotException.ErrorCode code, String message) {
        throw new MarketDataSnapshotException(code, message);
    }
}
