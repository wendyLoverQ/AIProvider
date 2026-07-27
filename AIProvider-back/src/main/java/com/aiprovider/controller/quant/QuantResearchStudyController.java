package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.controller.quant.dto.ResearchStudyCreateRequest;
import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import com.aiprovider.service.quant.ResearchStudyCreationService;
import com.aiprovider.service.quant.ResearchStudyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quant/backtests/research-studies")
public class QuantResearchStudyController {
  private final ResearchStudyCreationService creation;
  private final ResearchStudyService studies;
  public QuantResearchStudyController(ResearchStudyCreationService creation, ResearchStudyService studies) { this.creation = creation; this.studies = studies; }

  @PostMapping
  public Result<ResearchStudyDtos.CreateResponse> create(@RequestBody ResearchStudyCreateRequest request) { return Result.success(creation.create(request)); }

  @GetMapping
  public Result<BacktestDtos.Page<ResearchStudyDtos.Summary>> page(@RequestParam(defaultValue="1") int page,
      @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) String status,
      @RequestParam(required=false) Long datasetId, @RequestParam(required=false) String strategyCode,
      @RequestParam(required=false) String comparisonGroupKey) {
    return Result.success(studies.page(page, pageSize, status, datasetId, strategyCode, comparisonGroupKey));
  }

  @GetMapping("/{researchStudyId}")
  public Result<ResearchStudyDtos.Detail> get(@PathVariable String researchStudyId) { return Result.success(studies.get(researchStudyId)); }

  @GetMapping("/{researchStudyId}/parameter-space")
  public Result<ResearchStudyDtos.ParameterSpaceResponse> parameterSpace(@PathVariable String researchStudyId) { return Result.success(studies.parameterSpace(researchStudyId)); }

  @GetMapping("/comparison-groups/{comparisonGroupKey}/results")
  public Result<BacktestDtos.Page<ResearchStudyDtos.Summary>> results(@PathVariable String comparisonGroupKey,
      @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
      @RequestParam(required=false) String sortBy, @RequestParam(required=false) String sortDirection) {
    return Result.success(studies.results(comparisonGroupKey, page, pageSize, sortBy, sortDirection));
  }
}
