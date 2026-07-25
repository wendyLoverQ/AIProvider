package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.service.quant.BacktestRunService;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;

@RestController
@RequestMapping("/api/quant/backtests")
public class QuantBacktestController {
    private final BacktestRunService service;
    public QuantBacktestController(BacktestRunService service) { this.service = service; }
    @GetMapping("/strategies") public Result<?> strategies(){return Result.success(service.strategies());}
    @PostMapping("/runs") public Result<?> create(@RequestBody BacktestCreateRequest request){return Result.success(new BacktestDtos.RunIdResponse(service.create(request)));}
    @GetMapping("/runs") public Result<?> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String symbol,@RequestParam(required=false) String strategyCode){return Result.success(service.page(page,pageSize,status,symbol,strategyCode));}
    @GetMapping("/runs/non-terminal") public Result<?> nonTerminal(){return Result.success(service.nonTerminal());}
    @GetMapping("/runs/{runId}") public Result<?> get(@PathVariable String runId){return Result.success(service.get(runId));}
    @GetMapping("/runs/{runId}/trades") public Result<?> trades(@PathVariable String runId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int pageSize){return Result.success(service.trades(runId,page,pageSize));}
    @GetMapping("/runs/{runId}/equity") public Result<?> equity(@PathVariable String runId,@RequestParam(defaultValue="1200") int maxPoints){return Result.success(service.equity(runId,maxPoints));}
}
