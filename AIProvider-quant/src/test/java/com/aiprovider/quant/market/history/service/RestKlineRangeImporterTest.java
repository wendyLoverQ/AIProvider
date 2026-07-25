package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RestKlineRangeImporter} 单元测试。
 *
 * 验证核心修复点：
 * <ul>
 *   <li>首批 previousLastOpenTime = -1（不传当前批次最后 openTime，避免误触发 DUPLICATE_OPEN_TIME）</li>
 *   <li>第二批使用上一批最后 openTime 作为 previousLastOpenTime</li>
 *   <li>分页游标正确推进（cursor = currentBatchLastOpenTime + durationMs）</li>
 *   <li>游标停滞时抛出 RestImportException（CURSOR_NOT_ADVANCING）</li>
 * </ul>
 *
 * 使用 Mockito mock 端口依赖，不访问真实 Binance。
 * 所有 openTime 使用 M1 真实步长（60000ms 的倍数），保证游标推进逻辑与生产一致。
 */
class RestKlineRangeImporterTest {

    private static final KlineInterval INTERVAL = KlineInterval.M1; // 60000ms
    private static final long DURATION = INTERVAL.durationMillis();  // 60000
    private static final String SYMBOL = "BTCUSDT";
    private static final long SERVER_TIME = 2_000_000_000_000L;

    @Test
    void firstBatchPassesPreviousLastOpenTimeMinusOne() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        // 一批 3 根 K 线，openTime: 0, 60000, 120000
        List<MarketCandle> batch1 = candles(0, 60000, 120000);
        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(batch1)
                .thenReturn(Collections.emptyList());

        when(ingestService.ingestBatch(anyLong(), anyList(), eq(INTERVAL), eq(SYMBOL),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new MarketCandleIngestService.BatchResult(3, 0, 0));

        // range [0, 300000)，batchSize=500 → batchEnd=300000，一次请求全部
        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 500, 100_000);
        MarketSyncTask task = task(0, 300000);
        RestKlineRangeImporter.ImportStats stats = importer.importRange(task, 0, 300000, SERVER_TIME, -1);

        // 验证首批传入的 lastOpenTime = -1
        verify(ingestService).ingestBatch(
                eq(task.getDatasetId()), eq(batch1), eq(INTERVAL), eq(SYMBOL),
                eq(0L), eq(300000L), eq(SERVER_TIME), eq(-1L), eq("BINANCE_USDM_REST"));
        assertThat(stats.fetched).isEqualTo(3);
        assertThat(stats.batches).isEqualTo(1);
        assertThat(stats.lastOpenTime).isEqualTo(120000L);
    }

    @Test
    void secondBatchUsesPreviousBatchLastOpenTime() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        // 第一批：openTime 0, 60000, 120000
        List<MarketCandle> batch1 = candles(0, 60000, 120000);
        // 第二批：openTime 180000, 240000
        List<MarketCandle> batch2 = candles(180000, 240000);
        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(batch1)
                .thenReturn(batch2)
                .thenReturn(Collections.emptyList());

        when(ingestService.ingestBatch(anyLong(), anyList(), eq(INTERVAL), eq(SYMBOL),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new MarketCandleIngestService.BatchResult(3, 0, 0))
                .thenReturn(new MarketCandleIngestService.BatchResult(2, 0, 0));

        // batchSize=3 → batchEnd = 3*60000 = 180000，拆成两批
        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 3, 100_000);
        MarketSyncTask task = task(0, 300000);
        RestKlineRangeImporter.ImportStats stats = importer.importRange(task, 0, 300000, SERVER_TIME, -1);

        // 验证第一批传入 lastOpenTime = -1
        verify(ingestService, times(1)).ingestBatch(
                eq(task.getDatasetId()), eq(batch1), eq(INTERVAL), eq(SYMBOL),
                eq(0L), eq(300000L), eq(SERVER_TIME), eq(-1L), eq("BINANCE_USDM_REST"));

        // 验证第二批传入 lastOpenTime = 120000（第一批最后 openTime）
        verify(ingestService, times(1)).ingestBatch(
                eq(task.getDatasetId()), eq(batch2), eq(INTERVAL), eq(SYMBOL),
                eq(180000L), eq(300000L), eq(SERVER_TIME), eq(120000L), eq("BINANCE_USDM_REST"));

        assertThat(stats.fetched).isEqualTo(5);
        assertThat(stats.batches).isEqualTo(2);
        assertThat(stats.lastOpenTime).isEqualTo(240000L);
    }

    @Test
    void paginationAdvancesCursor() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        // 批次 1: openTime 0
        List<MarketCandle> batch1 = candles(0);
        // 批次 2: openTime 60000
        List<MarketCandle> batch2 = candles(60000);
        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(batch1)
                .thenReturn(batch2)
                .thenReturn(Collections.emptyList());

        when(ingestService.ingestBatch(anyLong(), anyList(), eq(INTERVAL), eq(SYMBOL),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new MarketCandleIngestService.BatchResult(1, 0, 0));

        // batchSize=1 → batchEnd = 1*60000 = 60000
        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 1, 100_000);
        MarketSyncTask task = task(0, 120000);
        RestKlineRangeImporter.ImportStats stats = importer.importRange(task, 0, 120000, SERVER_TIME, -1);

        // 第一次请求 cursor=0, batchEnd=60000
        verify(provider).fetchClosedKlines(SYMBOL, INTERVAL, 0L, 60000L, 1, SERVER_TIME);
        // 第二次请求 cursor=60000, batchEnd=120000
        verify(provider).fetchClosedKlines(SYMBOL, INTERVAL, 60000L, 120000L, 1, SERVER_TIME);

        assertThat(stats.batches).isEqualTo(2);
    }

    @Test
    void cursorStallThrowsException() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        // 第一批：openTime 0, 60000, 120000
        List<MarketCandle> batch1 = candles(0, 60000, 120000);
        // 第二批返回的 openTime 不超过 120000（游标停滞）
        List<MarketCandle> batch2 = candles(120000);
        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(batch1)
                .thenReturn(batch2);

        when(ingestService.ingestBatch(anyLong(), anyList(), eq(INTERVAL), eq(SYMBOL),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new MarketCandleIngestService.BatchResult(3, 0, 0));

        // batchSize=3 → batchEnd = 180000，第一批后 cursor=180000
        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 3, 100_000);
        MarketSyncTask task = task(0, 300000);

        // 第二批的 currentBatchLastOpenTime (120000) <= previousLastOpenTime (120000) → 游标停滞
        assertThatThrownBy(() -> importer.importRange(task, 0, 300000, SERVER_TIME, -1))
                .isInstanceOf(RestKlineRangeImporter.RestImportException.class)
                .hasMessageContaining("分页游标未推进");
    }

    @Test
    void emptyBatchTerminatesLoop() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(Collections.emptyList());

        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 500, 100_000);
        MarketSyncTask task = task(0, 360000);
        RestKlineRangeImporter.ImportStats stats = importer.importRange(task, 0, 360000, SERVER_TIME, -1);

        assertThat(stats.fetched).isZero();
        assertThat(stats.batches).isZero();
        assertThat(stats.lastOpenTime).isEqualTo(-1L);
        verify(ingestService, never()).ingestBatch(anyLong(), anyList(), any(), anyString(),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void maxCandlesExceededThrowsException() {
        HistoricalMarketDataProvider provider = mock(HistoricalMarketDataProvider.class);
        MarketCandleIngestService ingestService = mock(MarketCandleIngestService.class);
        MarketSyncTaskRepository taskRepo = mock(MarketSyncTaskRepository.class);

        // 每批 3 根，maxCandlesPerTask=5 → 第二批后超过
        List<MarketCandle> batch1 = candles(0, 60000, 120000);
        List<MarketCandle> batch2 = candles(180000, 240000, 300000);
        when(provider.fetchClosedKlines(eq(SYMBOL), eq(INTERVAL), anyLong(), anyLong(), anyInt(), eq(SERVER_TIME)))
                .thenReturn(batch1)
                .thenReturn(batch2)
                .thenReturn(Collections.emptyList());

        when(ingestService.ingestBatch(anyLong(), anyList(), eq(INTERVAL), eq(SYMBOL),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new MarketCandleIngestService.BatchResult(3, 0, 0));

        RestKlineRangeImporter importer = new RestKlineRangeImporter(provider, ingestService, taskRepo, 500, 5);
        MarketSyncTask task = task(0, 360000);

        assertThatThrownBy(() -> importer.importRange(task, 0, 360000, SERVER_TIME, -1))
                .isInstanceOf(RestKlineRangeImporter.RestImportException.class)
                .hasMessageContaining("超过上限");
    }

    // ---- 辅助方法 ----

    private MarketSyncTask task(long startMs, long endMs) {
        MarketSyncTask task = new MarketSyncTask();
        task.setTaskId("test-task-id");
        task.setDatasetId(1L);
        task.setSymbol(SYMBOL);
        task.setInterval(INTERVAL);
        task.setNormalizedStartTime(Instant.ofEpochMilli(startMs));
        task.setNormalizedEndTime(Instant.ofEpochMilli(endMs));
        return task;
    }

    private List<MarketCandle> candles(long... openTimes) {
        List<MarketCandle> result = new ArrayList<>(openTimes.length);
        for (long openTime : openTimes) {
            result.add(candle(openTime));
        }
        return result;
    }

    private MarketCandle candle(long openTime) {
        MarketCandle c = new MarketCandle();
        c.setProvider(MarketProviderId.BINANCE_USDM);
        c.setMarketType(MarketType.USDM_PERPETUAL);
        c.setSymbol(SYMBOL);
        c.setInterval(INTERVAL);
        c.setOpenTime(Instant.ofEpochMilli(openTime));
        c.setCloseTime(Instant.ofEpochMilli(openTime + DURATION - 1));
        c.setOpen(BigDecimal.ONE);
        c.setHigh(BigDecimal.ONE);
        c.setLow(BigDecimal.ONE);
        c.setClose(BigDecimal.ONE);
        c.setVolume(BigDecimal.ONE);
        c.setQuoteVolume(BigDecimal.ONE);
        c.setTradeCount(1);
        c.setTakerBuyBaseVolume(BigDecimal.ONE);
        c.setTakerBuyQuoteVolume(BigDecimal.ONE);
        return c;
    }
}
