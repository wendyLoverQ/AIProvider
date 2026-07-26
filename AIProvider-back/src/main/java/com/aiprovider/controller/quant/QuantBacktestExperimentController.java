package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.service.quant.BacktestExperimentCreationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quant/backtests/experiments")
public class QuantBacktestExperimentController {
    private final BacktestExperimentCreationService creation; private final com.aiprovider.service.quant.BacktestExperimentService service;
    public QuantBacktestExperimentController(BacktestExperimentCreationService creation,com.aiprovider.service.quant.BacktestExperimentService service){this.creation=creation;this.service=service;}
    @PostMapping public Result<BacktestExperimentDtos.CreateResponse> create(@RequestBody BacktestExperimentCreateRequest request){return Result.success(creation.create(request));}
    @GetMapping public Result<BacktestDtos.Page<BacktestExperimentDtos.ExperimentSummary>> page(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String status,@RequestParam(required=false)String symbol,@RequestParam(required=false)String strategyCode){return Result.success(service.page(page,pageSize,status,symbol,strategyCode));}
    @GetMapping("/{experimentId}") public Result<BacktestExperimentDtos.ExperimentSummary> get(@PathVariable String experimentId){return Result.success(service.get(experimentId));}
    @GetMapping("/{experimentId}/candidates") public Result<BacktestDtos.Page<BacktestExperimentDtos.CandidateResult>> candidates(@PathVariable String experimentId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int pageSize,@RequestParam(defaultValue="CANDIDATE_INDEX")String sortBy,@RequestParam(defaultValue="ASC")String order){return Result.success(service.candidates(experimentId,page,pageSize,sortBy,order));}
}
