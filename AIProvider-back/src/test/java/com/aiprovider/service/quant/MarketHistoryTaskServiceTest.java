package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantMarketHistoryProperties;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.service.ArchiveImportService;
import com.aiprovider.quant.market.history.service.MarketHistorySyncService;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.service.PublicMarketQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MarketHistoryTaskService} 单元测试。
 *
 * 验证动态合约校验（复用 PublicMarketQueryService）和 sourceMode 统一路由：
 * <ul>
 *   <li>有效合约（TRADING + PERPETUAL + 支持周期）→ 任务创建成功</li>
 *   <li>缺失合约 / 非 TRADING / 非 PERPETUAL → 抛 CONTRACT_NOT_FOUND</li>
 *   <li>AUTO / ARCHIVE_MONTHLY / ARCHIVE_DAILY → 路由到 ArchiveImportService</li>
 *   <li>REST_GAP_REPAIR → 路由到 MarketHistorySyncService</li>
 *   <li>非法 sourceMode → 抛 INVALID_SOURCE_MODE</li>
 * </ul>
 */
class MarketHistoryTaskServiceTest {

    private MarketHistorySyncService syncService;
    private ArchiveImportService archiveImportService;
    private MarketSyncTaskRepository taskRepository;
    private MarketDatasetRepository datasetRepository;
    private QuantMarketHistoryProperties properties;
    private ThreadPoolTaskExecutor executor;
    private PublicMarketQueryService publicMarketQueryService;

    private MarketHistoryTaskService service;

    @BeforeEach
    void setUp() {
        syncService = mock(MarketHistorySyncService.class);
        archiveImportService = mock(ArchiveImportService.class);
        taskRepository = mock(MarketSyncTaskRepository.class);
        datasetRepository = mock(MarketDatasetRepository.class);
        properties = new QuantMarketHistoryProperties();
        executor = mock(ThreadPoolTaskExecutor.class);
        publicMarketQueryService = mock(PublicMarketQueryService.class);

        // executor.execute(runnable) 立即执行传入的 Runnable，用于验证路由
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        service = new MarketHistoryTaskService(syncService, archiveImportService,
                taskRepository, datasetRepository, properties, executor, publicMarketQueryService);
    }

    // ---- 合约校验 ----

    @Test
    void createTaskWithValidContractRoutesAutoToArchiveImport() {
        setupValidContract();
        setupExistingDataset();
        when(taskRepository.insert(any(MarketSyncTask.class))).thenReturn(1L);

        String taskId = service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO");

        assertThat(taskId).isNotBlank();
        verify(archiveImportService).executeArchiveImport(any(MarketSyncTask.class));
        verify(publicMarketQueryService).contracts(MarketProviderId.BINANCE_USDM, "USDT");
    }

    @Test
    void createTaskRoutesRestGapRepairToSyncService() {
        setupValidContract();
        setupExistingDataset();
        when(taskRepository.insert(any(MarketSyncTask.class))).thenReturn(1L);

        service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "REST_GAP_REPAIR");

        verify(syncService).executeSync(any(MarketSyncTask.class));
    }

    @Test
    void createTaskRoutesArchiveMonthlyToArchiveImport() {
        setupValidContract();
        setupExistingDataset();
        when(taskRepository.insert(any(MarketSyncTask.class))).thenReturn(1L);

        service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "ARCHIVE_MONTHLY");

        verify(archiveImportService).executeArchiveImport(any(MarketSyncTask.class));
    }

    @Test
    void createTaskWithMissingContractThrowsContractNotFound() {
        when(publicMarketQueryService.contracts(MarketProviderId.BINANCE_USDM, "USDT"))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO"))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONTRACT_NOT_FOUND");
    }

    @Test
    void createTaskWithNonTradingContractThrowsContractNotFound() {
        PerpetualContract contract = validContractBuilder();
        contract.setStatus("BREAK");
        when(publicMarketQueryService.contracts(MarketProviderId.BINANCE_USDM, "USDT"))
                .thenReturn(List.of(contract));

        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO"))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONTRACT_NOT_FOUND");
    }

    @Test
    void createTaskWithNonPerpetualContractThrowsContractNotFound() {
        PerpetualContract contract = validContractBuilder();
        contract.setContractType("CURRENT_QUARTER");
        when(publicMarketQueryService.contracts(MarketProviderId.BINANCE_USDM, "USDT"))
                .thenReturn(List.of(contract));

        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO"))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONTRACT_NOT_FOUND");
    }

    @Test
    void createTaskWithIntervalNotSupportedThrowsContractNotFound() {
        PerpetualContract contract = validContractBuilder();
        contract.setSupportedIntervals(List.of(KlineInterval.M15));
        when(publicMarketQueryService.contracts(MarketProviderId.BINANCE_USDM, "USDT"))
                .thenReturn(List.of(contract));

        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO"))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONTRACT_NOT_FOUND");
    }

    // ---- sourceMode 校验 ----

    @Test
    void createTaskWithInvalidSourceModeThrowsInvalidSourceMode() {
        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "UNKNOWN_MODE"))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SOURCE_MODE");
    }

    @Test
    void createTaskWithBlankSourceModeThrowsInvalidSourceMode() {
        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                ""))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SOURCE_MODE");
    }

    @Test
    void createTaskWithNullSourceModeThrowsInvalidSourceMode() {
        assertThatThrownBy(() -> service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                null))
                .isInstanceOf(MarketHistoryTaskException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SOURCE_MODE");
    }

    @Test
    void createTaskSetsSourceModeOnTaskRecord() {
        setupValidContract();
        setupExistingDataset();

        List<MarketSyncTask> captured = new ArrayList<>();
        when(taskRepository.insert(any(MarketSyncTask.class))).thenAnswer(invocation -> {
            captured.add(invocation.getArgument(0));
            return 1L;
        });

        service.createTask("BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "REST_GAP_REPAIR");

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getSourceMode()).isEqualTo("REST_GAP_REPAIR");
    }

    // ---- 辅助方法 ----

    private void setupValidContract() {
        when(publicMarketQueryService.contracts(MarketProviderId.BINANCE_USDM, "USDT"))
                .thenReturn(List.of(validContractBuilder()));
    }

    private PerpetualContract validContractBuilder() {
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        contract.setStatus("TRADING");
        contract.setContractType("PERPETUAL");
        contract.setSupportedIntervals(List.of(KlineInterval.M1, KlineInterval.M5, KlineInterval.M15));
        return contract;
    }

    private void setupExistingDataset() {
        MarketDataset dataset = new MarketDataset();
        dataset.setId(1L);
        dataset.setProvider(MarketProviderId.BINANCE_USDM);
        dataset.setMarketType(MarketType.USDM_PERPETUAL);
        dataset.setDataType(MarketDataType.CANDLE);
        dataset.setSymbol("BTCUSDT");
        dataset.setInterval(KlineInterval.M1);
        dataset.setStatus(MarketDatasetStatus.EMPTY);
        when(datasetRepository.findByKey(
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                "CANDLE",
                "BTCUSDT",
                "1m"))
                .thenReturn(dataset);
    }
}
