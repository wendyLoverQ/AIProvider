package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.SyncTaskCreateRequest;
import com.aiprovider.quant.market.history.model.HistoricalKlinePage;
import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.service.MarketHistoryQueryService;
import com.aiprovider.service.quant.MarketHistoryTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 量化历史行情数据控制器。
 *
 * 对接前端"量化 → 行情数据"页面，提供同步任务管理、数据集查看、
 * 缺口查询和 K 线预览能力。所有返回使用统一 {@link Result} 封装。
 *
 * 同步任务创建后异步执行，前端通过轮询非终态任务获取进度。
 */
@RestController
@RequestMapping("/api/quant/market-data")
public class QuantMarketDataController {

    private final MarketHistoryTaskService taskService;
    private final MarketHistoryQueryService queryService;

    public QuantMarketDataController(MarketHistoryTaskService taskService,
                                     MarketHistoryQueryService queryService) {
        this.taskService = taskService;
        this.queryService = queryService;
    }

    // ---- 同步任务 ----

    /**
     * 创建历史行情同步任务。
     *
     * 请求体包含合约符号、K 线周期和起止时间。
     * 后端校验后异步执行，返回任务 ID。
     */
    @PostMapping("/sync")
    public Result<Map<String, String>> createSyncTask(@RequestBody SyncTaskCreateRequest request) {
        String taskId = taskService.createSyncTask(
                request.getSymbol(),
                request.getInterval(),
                request.getStartTime(),
                request.getEndTime());
        return Result.success(Collections.singletonMap("taskId", taskId));
    }

    /**
     * 创建历史行情归档导入任务（Binance 官方 ZIP 数据源）。
     *
     * 使用 data.binance.vision 官方月包和日包下载历史 K 线，
     * 适用于大范围历史数据回填。归档截止之后的数据需要另行创建 REST 同步任务。
     */
    @PostMapping("/archive-import")
    public Result<Map<String, String>> createArchiveImportTask(@RequestBody SyncTaskCreateRequest request) {
        String taskId = taskService.createArchiveImportTask(
                request.getSymbol(),
                request.getInterval(),
                request.getStartTime(),
                request.getEndTime());
        return Result.success(Collections.singletonMap("taskId", taskId));
    }

    /**
     * 分页查询同步任务列表，按排队时间倒序。
     */
    @GetMapping("/tasks")
    public Result<List<MarketSyncTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }
        return Result.success(taskService.listTasks(page, pageSize));
    }

    /**
     * 查询非终态任务（用于前端轮询进度）。
     */
    @GetMapping("/tasks/non-terminal")
    public Result<List<MarketSyncTask>> listNonTerminalTasks() {
        return Result.success(taskService.listNonTerminalTasks());
    }

    /**
     * 查询单个任务详情。
     */
    @GetMapping("/tasks/{taskId}")
    public Result<MarketSyncTask> getTask(@PathVariable String taskId) {
        MarketSyncTask task = taskService.getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return Result.success(task);
    }

    // ---- 数据集 ----

    /**
     * 分页查询数据集列表。
     */
    @GetMapping("/datasets")
    public Result<List<MarketDataset>> listDatasets(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }
        return Result.success(queryService.listDatasets(null, symbol, interval, status, page, pageSize));
    }

    /**
     * 查询数据集详情。
     */
    @GetMapping("/datasets/{datasetId}")
    public Result<MarketDataset> getDataset(@PathVariable long datasetId) {
        MarketDataset dataset = queryService.getDataset(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        return Result.success(dataset);
    }

    /**
     * 查询数据集缺口列表。
     */
    @GetMapping("/datasets/{datasetId}/gaps")
    public Result<List<MarketDataGap>> getGaps(@PathVariable long datasetId) {
        return Result.success(queryService.getGaps(datasetId));
    }

    /**
     * 分页预览数据集 K 线，按 openTime 倒序。
     */
    @GetMapping("/datasets/{datasetId}/candles")
    public Result<HistoricalKlinePage> getCandles(
            @PathVariable long datasetId,
            @RequestParam(required = false) Long startOpenTime,
            @RequestParam(required = false) Long endOpenTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        return Result.success(queryService.getCandles(datasetId, startOpenTime, endOpenTime, page, pageSize));
    }
}
