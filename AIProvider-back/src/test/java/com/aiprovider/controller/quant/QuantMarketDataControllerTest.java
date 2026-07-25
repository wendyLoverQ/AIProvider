package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.SyncTaskCreateRequest;
import com.aiprovider.quant.market.history.model.HistoricalKlinePage;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.service.MarketHistoryQueryService;
import com.aiprovider.service.quant.MarketHistoryTaskService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuantMarketDataController} 单元测试。
 *
 * 验证统一 /sync-tasks API、404 资源不存在、400 非法分页，
 * 以及各端点正确委托到 taskService / queryService。
 */
class QuantMarketDataControllerTest {

    // ---- sync-tasks API ----

    @Test
    void createSyncTaskDelegatesToTaskService() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(taskService.createTask(eq("BINANCE_USDM"), eq("USDM_PERPETUAL"), eq("BTCUSDT"), eq("1m"), any(), any(), eq("AUTO")))
                .thenReturn("task-uuid-123");

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        SyncTaskCreateRequest req = new SyncTaskCreateRequest();
        req.setSymbol("BTCUSDT");
        req.setInterval("1m");
        req.setStartTime(Instant.parse("2025-01-01T00:00:00Z"));
        req.setEndTime(Instant.parse("2025-01-01T00:01:00Z"));
        req.setSourceMode("AUTO");
        req.setProvider("BINANCE_USDM");
        req.setMarketType("USDM_PERPETUAL");

        Result<Map<String, String>> result = controller.createSyncTask(req);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().get("taskId")).isEqualTo("task-uuid-123");
        verify(taskService).createTask("BINANCE_USDM", "USDM_PERPETUAL", "BTCUSDT", "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:00Z"),
                "AUTO");
    }

    @Test
    void listTasksDelegatesToTaskService() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(taskService.listTasks(1, 20)).thenReturn(Collections.emptyList());

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<List<MarketSyncTask>> result = controller.listTasks(1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        verify(taskService).listTasks(1, 20);
    }

    @Test
    void listTasksRejectsInvalidPaging() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.listTasks(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.listTasks(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.listTasks(1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listNonTerminalTasksDelegates() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(taskService.listNonTerminalTasks()).thenReturn(Collections.emptyList());

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<List<MarketSyncTask>> result = controller.listNonTerminalTasks();

        assertThat(result.getCode()).isEqualTo(200);
        verify(taskService).listNonTerminalTasks();
    }

    @Test
    void getTaskReturns404WhenNotFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(taskService.getTask("missing-id")).thenReturn(null);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.getTask("missing-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTaskReturnsDataWhenFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        MarketSyncTask task = new MarketSyncTask();
        task.setTaskId("task-1");
        when(taskService.getTask("task-1")).thenReturn(task);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<MarketSyncTask> result = controller.getTask("task-1");

        assertThat(result.getData()).isSameAs(task);
    }

    // ---- datasets ----

    @Test
    void getDatasetReturns404WhenNotFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(queryService.getDataset(999L)).thenReturn(null);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.getDataset(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDatasetReturnsDataWhenFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        MarketDataset dataset = new MarketDataset();
        dataset.setId(1L);
        when(queryService.getDataset(1L)).thenReturn(dataset);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<MarketDataset> result = controller.getDataset(1L);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    // ---- gaps ----

    @Test
    void getGapsReturns404WhenDatasetNotFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(queryService.getDataset(404L)).thenReturn(null);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.getGaps(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGapsDelegatesWhenDatasetExists() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        MarketDataset dataset = new MarketDataset();
        dataset.setId(1L);
        when(queryService.getDataset(1L)).thenReturn(dataset);
        when(queryService.getGaps(1L)).thenReturn(Collections.emptyList());

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<?> result = controller.getGaps(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(queryService).getGaps(1L);
    }

    // ---- candles ----

    @Test
    void getCandlesReturns404WhenDatasetNotFound() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        when(queryService.getDataset(404L)).thenReturn(null);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.getCandles(404L, null, null, 1, 100))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCandlesRejectsInvalidPaging() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        MarketDataset dataset = new MarketDataset();
        dataset.setId(1L);
        when(queryService.getDataset(1L)).thenReturn(dataset);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.getCandles(1L, null, null, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.getCandles(1L, null, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.getCandles(1L, null, null, 1, 501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCandlesDelegatesWithRangeFilter() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        MarketDataset dataset = new MarketDataset();
        dataset.setId(1L);
        when(queryService.getDataset(1L)).thenReturn(dataset);

        HistoricalKlinePage page = new HistoricalKlinePage();
        page.setTotal(42);
        page.setPage(1);
        page.setPageSize(100);
        page.setCandles(Collections.emptyList());
        when(queryService.getCandles(eq(1L), eq(1000L), eq(2000L), eq(1), eq(100)))
                .thenReturn(page);

        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);
        Result<HistoricalKlinePage> result = controller.getCandles(1L, 1000L, 2000L, 1, 100);

        assertThat(result.getData().getTotal()).isEqualTo(42);
        verify(queryService).getCandles(1L, 1000L, 2000L, 1, 100);
    }

    @Test
    void listDatasetsRejectsInvalidPaging() {
        MarketHistoryTaskService taskService = mock(MarketHistoryTaskService.class);
        MarketHistoryQueryService queryService = mock(MarketHistoryQueryService.class);
        QuantMarketDataController controller = new QuantMarketDataController(taskService, queryService);

        assertThatThrownBy(() -> controller.listDatasets(null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.listDatasets(null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
