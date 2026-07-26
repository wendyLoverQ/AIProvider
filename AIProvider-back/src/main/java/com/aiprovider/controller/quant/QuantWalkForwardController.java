package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.service.quant.WalkForwardStudyCreationService;
import com.aiprovider.service.quant.WalkForwardStudyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quant/backtests/walk-forward-studies")
public class QuantWalkForwardController {
  private final WalkForwardStudyCreationService creation;
  private final WalkForwardStudyService studies;

  public QuantWalkForwardController(
      WalkForwardStudyCreationService creation, WalkForwardStudyService studies) {
    this.creation = creation;
    this.studies = studies;
  }

  @PostMapping
  public Result<WalkForwardStudyDtos.CreateResponse> create(
      @RequestBody WalkForwardStudyCreateRequest request) {
    return Result.success(creation.create(request));
  }

  @GetMapping
  public Result<BacktestDtos.Page<WalkForwardStudyDtos.StudySummary>> page(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String strategyCode) {
    return Result.success(studies.page(page, pageSize, status, symbol, strategyCode));
  }

  @GetMapping("/{studyId}")
  public Result<WalkForwardStudyDtos.StudyDetail> get(@PathVariable String studyId) {
    return Result.success(studies.get(studyId));
  }

  @GetMapping("/{studyId}/folds")
  public Result<BacktestDtos.Page<WalkForwardStudyDtos.FoldResult>> folds(
      @PathVariable String studyId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize) {
    return Result.success(studies.folds(studyId, page, pageSize));
  }

  @GetMapping("/{studyId}/oos-equity")
  public Result<WalkForwardStudyDtos.OosEquity> oos(
      @PathVariable String studyId, @RequestParam(defaultValue = "1200") int limit) {
    return Result.success(studies.oosEquity(studyId, limit));
  }
}
