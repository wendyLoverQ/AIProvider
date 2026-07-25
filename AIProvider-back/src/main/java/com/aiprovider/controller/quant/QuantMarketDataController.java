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
import javax.validation.Valid;

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
     * 创建历史行情同步任务（统一入口）。
     *
     * 请求体包含合约符号、K 线周期、起止时间和数据来源模式（sourceMode）。
     * 后端复用 PublicMarketQueryService 校验真实合约，按 sourceMode 路由到对应导入管线：
     * <ul>
     *   <li>{@code AUTO} — 单任务完成归档月包/日包 + REST 尾部</li>
     *   <li>{@code REST_GAP_REPAIR} — 只用 REST 修补指定范围</li>
     *   <li>{@code ARCHIVE_MONTHLY} / {@code ARCHIVE_DAILY} — 只导入归档包</li>
     * </ul>
     * 后端异步执行，返回任务 ID。
     */
    @PostMapping("/sync-tasks")
    public Result<Map<String, String>> createSyncTask(@Valid @RequestBody SyncTaskCreateRequest request) {
        String taskId = taskService.createTask(
                request.getProvider(), request.getMarketType(),
                request.getSymbol(),
                request.getInterval(),
                request.getStartTime(),
                request.getEndTime(),
                request.getSourceMode());
        return Result.success(Collections.singletonMap("taskId", taskId));
    }

    /**
     * 分页查询同步任务列表，按排队时间倒序。
     */
    @GetMapping("/sync-tasks")
    public Result<List<MarketSyncTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePaging(page, pageSize, 100);
        return Result.success(taskService.listTasks(page, pageSize));
    }

    /**
     * 查询非终态任务（用于前端轮询进度）。
     */
    @GetMapping("/sync-tasks/non-terminal")
    public Result<List<MarketSyncTask>> listNonTerminalTasks() {
        return Result.success(taskService.listNonTerminalTasks());
    }

    /**
     * 查询单个任务详情。
     */
    @GetMapping("/sync-tasks/{taskId}")
    public Result<MarketSyncTask> getTask(@PathVariable String taskId) {
        MarketSyncTask task = taskService.getTask(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务", taskId);
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
        validatePaging(page, pageSize, 100);
        return Result.success(queryService.listDatasets(null, symbol, interval, status, page, pageSize));
    }

    /**
     * 查询数据集详情。
     */
    @GetMapping("/datasets/{datasetId}")
    public Result<MarketDataset> getDataset(@PathVariable long datasetId) {
        MarketDataset dataset = queryService.getDataset(datasetId);
        if (dataset == null) {
            throw new ResourceNotFoundException("数据集", String.valueOf(datasetId));
        }
        return Result.success(dataset);
    }

    /**
     * 查询数据集缺口列表。
     *
     * 数据集不存在时返回 404。
     */
    @GetMapping("/datasets/{datasetId}/gaps")
    public Result<List<MarketDataGap>> getGaps(@PathVariable long datasetId) {
        requireDatasetExists(datasetId);
        return Result.success(queryService.getGaps(datasetId));
    }

    /**
     * 分页预览数据集 K 线，按 openTime 倒序。
     *
     * 数据集不存在时返回 404，非法分页返回 400。
     */
    @GetMapping("/datasets/{datasetId}/candles")
    public Result<HistoricalKlinePage> getCandles(
            @PathVariable long datasetId,
            @RequestParam(required = false) Long startOpenTime,
            @RequestParam(required = false) Long endOpenTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        requireDatasetExists(datasetId);
        validatePaging(page, pageSize, 500);
        return Result.success(queryService.getCandles(datasetId, startOpenTime, endOpenTime, page, pageSize));
    }

    // ---- 校验辅助 ----

    /**
     * 分页参数校验。page 必须 >=1，pageSize 必须 1..maxPageSize，否则抛 IllegalArgumentException（映射 400）。
     */
    private void validatePaging(int page, int pageSize, int maxPageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page 必须 >= 1: " + page);
        }
        if (pageSize < 1 || pageSize > maxPageSize) {
            throw new IllegalArgumentException("pageSize 必须在 1.." + maxPageSize + " 之间: " + pageSize);
        }
    }

    /**
     * 校验数据集存在，不存在抛 ResourceNotFoundException（映射 404）。
     */
    private void requireDatasetExists(long datasetId) {
        if (queryService.getDataset(datasetId) == null) {
            throw new ResourceNotFoundException("数据集", String.valueOf(datasetId));
        }
    }
}
