package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataSnapshotServiceTest {
    private static final long DATASET_ID = 7L;
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final long STEP = 60_000L;

    @Mock private MarketDatasetRepository datasets;
    @Mock private MarketCandleRepository candles;
    private MarketDataSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new MarketDataSnapshotService(datasets, candles, 1000);
    }

    @Test
    void loadsContinuousExclusiveRangeAndUsesExactlyThreeRepositoryCalls() {
        MarketDataset dataset = readyDataset();
        List<HistoricalCandle> rows = candles(3);
        when(datasets.findById(DATASET_ID)).thenReturn(dataset);
        when(candles.countRangeExclusive(DATASET_ID, START.toEpochMilli(), START.toEpochMilli() + STEP * 3))
                .thenReturn(3L);
        when(candles.findRangeAscending(DATASET_ID, START.toEpochMilli(), START.toEpochMilli() + STEP * 3, 4))
                .thenReturn(rows);

        com.aiprovider.quant.market.history.model.MarketDataSnapshot snapshot =
                service.load(DATASET_ID, START, START.plusMillis(STEP * 3));

        assertEquals(3, snapshot.getExpectedCandleCount());
        assertEquals(rows.size(), snapshot.getCandles().size());
        assertEquals(rows.get(0).getClosePrice(), snapshot.getCandles().get(0).getClosePrice());
        rows.get(0).setClosePrice(new BigDecimal("999"));
        assertEquals(new BigDecimal("105"), snapshot.getCandles().get(0).getClosePrice());
        List<HistoricalCandle> exposed = snapshot.getCandles();
        exposed.get(0).setClosePrice(new BigDecimal("888"));
        assertEquals(new BigDecimal("105"), snapshot.getCandles().get(0).getClosePrice());
        assertThrows(UnsupportedOperationException.class, () -> exposed.add(rows.get(0)));
        verify(datasets).findById(DATASET_ID);
        verify(candles).countRangeExclusive(DATASET_ID, START.toEpochMilli(), START.toEpochMilli() + STEP * 3);
        verify(candles).findRangeAscending(DATASET_ID, START.toEpochMilli(), START.toEpochMilli() + STEP * 3, 4);
        verifyNoMoreInteractions(datasets, candles);
    }

    @Test
    void rejectsMissingDatasetAndInvalidRequestWithoutCandleQueries() {
        when(datasets.findById(DATASET_ID)).thenReturn(null);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATASET_NOT_FOUND,
                () -> service.load(DATASET_ID, START, START.plusMillis(STEP)));
        verifyNoInteractions(candles);

        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_REQUEST_INVALID,
                () -> service.load(0, START, START.plusMillis(STEP)));
        verifyNoInteractions(candles);
    }

    @Test
    void rejectsNotReadyDatasetBeforeReadingCandles() {
        for (MarketDatasetStatus status : new MarketDatasetStatus[]{MarketDatasetStatus.EMPTY,
                MarketDatasetStatus.GAPPED, MarketDatasetStatus.PARTIAL, MarketDatasetStatus.ERROR}) {
            reset(datasets, candles);
            MarketDataset dataset = readyDataset();
            dataset.setStatus(status);
            when(datasets.findById(DATASET_ID)).thenReturn(dataset);
            assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATASET_NOT_READY,
                    () -> service.load(DATASET_ID, START, START.plusMillis(STEP)));
            verifyNoInteractions(candles);
        }
    }

    @Test
    void rejectsUnalignedUncoveredAndTooLargeRanges() {
        MarketDataset dataset = readyDataset();
        when(datasets.findById(DATASET_ID)).thenReturn(dataset);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_RANGE_NOT_ALIGNED,
                () -> service.load(DATASET_ID, START.plusMillis(1), START.plusMillis(STEP)));
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_RANGE_NOT_COVERED,
                () -> service.load(DATASET_ID, START.minusMillis(STEP), START.plusMillis(STEP)));
        service = new MarketDataSnapshotService(datasets, candles, 2);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_TOO_LARGE,
                () -> service.load(DATASET_ID, START, START.plusMillis(STEP * 3)));
        verifyNoInteractions(candles);
    }

    @Test
    void rejectsCountMismatchOverflowAndMalformedCandle() {
        MarketDataset dataset = readyDataset();
        when(datasets.findById(DATASET_ID)).thenReturn(dataset);
        when(candles.countRangeExclusive(anyLong(), anyLong(), anyLong())).thenReturn(2L);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_COUNT_MISMATCH,
                () -> service.load(DATASET_ID, START, START.plusMillis(STEP * 3)));

        reset(candles);
        when(candles.countRangeExclusive(anyLong(), anyLong(), anyLong())).thenReturn(3L);
        List<HistoricalCandle> overflow = new ArrayList<>(candles(3));
        overflow.add(candles(3).get(0));
        when(candles.findRangeAscending(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(overflow);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_QUERY_OVERFLOW,
                () -> service.load(DATASET_ID, START, START.plusMillis(STEP * 3)));

        reset(candles);
        when(candles.countRangeExclusive(anyLong(), anyLong(), anyLong())).thenReturn(3L);
        List<HistoricalCandle> malformed = candles(3);
        malformed.get(1).setOpenTime(START.plusMillis(STEP * 3));
        when(candles.findRangeAscending(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(malformed);
        assertCode(MarketDataSnapshotException.ErrorCode.SNAPSHOT_DATA_INVALID,
                () -> service.load(DATASET_ID, START, START.plusMillis(STEP * 3)));
    }

    private MarketDataset readyDataset() {
        MarketDataset d = new MarketDataset();
        d.setId(DATASET_ID);
        d.setProvider(MarketProviderId.BINANCE_USDM);
        d.setMarketType(MarketType.USDM_PERPETUAL);
        d.setDataType(MarketDataType.CANDLE);
        d.setSymbol("BTCUSDT");
        d.setInterval(KlineInterval.M1);
        d.setEarliestOpenTime(START);
        d.setLatestOpenTime(START.plusMillis(STEP * 2));
        d.setCandleCount(3);
        d.setStatus(MarketDatasetStatus.CONTIGUOUS);
        d.setLastValidatedAt(START);
        return d;
    }

    private List<HistoricalCandle> candles(int count) {
        List<HistoricalCandle> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HistoricalCandle c = new HistoricalCandle();
            c.setDatasetId(DATASET_ID);
            c.setProvider(MarketProviderId.BINANCE_USDM);
            c.setMarketType(MarketType.USDM_PERPETUAL);
            c.setSymbol("BTCUSDT");
            c.setInterval(KlineInterval.M1);
            c.setOpenTime(START.plusMillis(STEP * i));
            c.setCloseTime(START.plusMillis(STEP * (i + 1) - 1));
            c.setOpenPrice(new BigDecimal("100"));
            c.setHighPrice(new BigDecimal("110"));
            c.setLowPrice(new BigDecimal("90"));
            c.setClosePrice(new BigDecimal("105"));
            c.setVolume(BigDecimal.ONE);
            c.setQuoteVolume(BigDecimal.ONE);
            c.setTradeCount(1);
            result.add(c);
        }
        return result;
    }

    private void assertCode(MarketDataSnapshotException.ErrorCode expected, Runnable action) {
        MarketDataSnapshotException error = assertThrows(MarketDataSnapshotException.class, action::run);
        assertEquals(expected, error.getErrorCode());
    }
}
