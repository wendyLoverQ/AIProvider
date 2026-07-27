package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantResearchProperties;
import com.aiprovider.controller.quant.dto.ResearchStudyCreateRequest;
import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.research.IntegerParameterRange;
import com.aiprovider.quant.research.ParameterSpaceExpansion;
import com.aiprovider.quant.research.StrategyParameterSpaceExpander;
import com.aiprovider.quant.research.StrategyResearchException;
import com.aiprovider.quant.research.StrategyResearchSpace;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchStudyCreationService {
  private final ResearchStudyMapper research;
  private final WalkForwardStudyCreationService walkForward;
  private final com.aiprovider.quant.market.history.port.MarketDatasetRepository datasets;
  private final StrategyRegistry strategies;
  private final ObjectMapper json;
  private final QuantResearchProperties properties;

  public ResearchStudyCreationService(ResearchStudyMapper research, WalkForwardStudyCreationService walkForward,
      com.aiprovider.quant.market.history.port.MarketDatasetRepository datasets, StrategyRegistry strategies,
      ObjectMapper json, QuantResearchProperties properties) {
    this.research = research; this.walkForward = walkForward; this.datasets = datasets; this.strategies = strategies; this.json = json; this.properties = properties;
  }

  @Transactional
  public ResearchStudyDtos.CreateResponse create(ResearchStudyCreateRequest request) {
    validateRequest(request);
    MarketDataset dataset = datasets.findById(request.getDatasetId());
    if (dataset == null) throw error("RESEARCH_REQUEST_INVALID", "datasetId not found");
    QuantStrategyDefinition definition;
    try { definition = strategies.get(request.getStrategyCode().trim()); }
    catch (StrategyException exception) { throw error("RESEARCH_REQUEST_INVALID", exception.getMessage()); }
    if (!definition.version().equals(request.getStrategyVersion().trim())) throw error("RESEARCH_REQUEST_INVALID", "strategy version is not supported");
    StrategyResearchSpace space = buildSpace(request, definition);
    ParameterSpaceExpansion expansion;
    try { expansion = new StrategyParameterSpaceExpander().expand(definition, space, properties.getMaxCandidates()); }
    catch (StrategyResearchException exception) { throw mapSpace(exception); }
    String spaceJson = write(spaceJsonValue(space));
    String gridJson = write(expansion.grid());
    String researchId = UUID.randomUUID().toString(), childId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    ResearchStudyRow row = new ResearchStudyRow();
    row.researchStudyId = researchId; row.name = request.getName().trim(); row.description = request.getDescription() == null ? null : request.getDescription().trim();
    row.datasetId = dataset.getId(); row.provider = dataset.getProvider().name(); row.marketType = dataset.getMarketType().name(); row.dataType = dataset.getDataType().name(); row.symbol = dataset.getSymbol(); row.intervalCode = dataset.getInterval().code();
    row.strategyCode = definition.code(); row.strategyVersion = definition.version(); row.executionProfileCode = request.getExecutionProfileCode().trim(); row.directionMode = request.getDirectionMode().trim(); row.orderSizingMode = request.getOrderSizingMode().trim();
    row.evaluationMode = "WALK_FORWARD"; row.parameterSpaceMode = request.getParameterSpaceMode(); row.parameterSpaceJson = spaceJson; row.expandedParameterGridJson = gridJson; row.candidateCount = expansion.candidateCount();
    row.studyStartOpenTimeMs = request.getStudyStartOpenTimeInclusive().toEpochMilli(); row.studyEndOpenTimeMs = request.getStudyEndOpenTimeExclusive().toEpochMilli(); row.trainingBars = request.getTrainingBars(); row.validationBars = request.getValidationBars(); row.selectionMetric = request.getSelectionMetric().trim().toUpperCase(); row.minimumTrainTrades = request.getMinimumTrainTrades();
    row.orderAmount = request.getOrderAmount(); row.feeRate = request.getFeeRate(); row.forceCloseAtEnd = true; row.comparisonGroupKey = ResearchComparisonGroupKey.sha256(request); row.walkForwardStudyId = childId; row.createdAt = now; row.updatedAt = now;
    if (research.insert(row) != 1) throw error("RESEARCH_PERSISTENCE_FAILED", "research study insert affected an unexpected number of rows");
    walkForward.createWithStudyId(childId, childRequest(request, expansion.grid()));
    return new ResearchStudyDtos.CreateResponse(researchId, childId, expansion.candidateCount());
  }

  private Map<String, ResearchStudyDtos.IntegerRange> spaceJsonValue(StrategyResearchSpace space) {
    Map<String, ResearchStudyDtos.IntegerRange> result = new LinkedHashMap<>();
    for (IntegerParameterRange range : space.parameters()) result.put(range.parameterName(), new ResearchStudyDtos.IntegerRange(range.minimum(), range.maximum(), range.step()));
    return result;
  }

  private StrategyResearchSpace buildSpace(ResearchStudyCreateRequest request, QuantStrategyDefinition definition) {
    if ("STRATEGY_DEFAULT".equals(request.getParameterSpaceMode())) {
      if (request.getParameterSpace() != null && !request.getParameterSpace().isEmpty()) throw error("RESEARCH_PARAMETER_SPACE_INVALID", "parameterSpace must be empty for STRATEGY_DEFAULT");
      return definition.researchSpace();
    }
    if (request.getParameterSpace() == null || request.getParameterSpace().isEmpty()) throw error("RESEARCH_PARAMETER_SPACE_INVALID", "parameterSpace is required for CUSTOM_INTEGER_RANGE");
    List<IntegerParameterRange> ranges = new ArrayList<>();
    for (StrategyParameterDefinition parameter : definition.parameters()) {
      ResearchStudyCreateRequest.IntegerRangeRequest input = request.getParameterSpace().get(parameter.name());
      if (input == null) throw error("RESEARCH_PARAMETER_SPACE_INVALID", "missing parameter range " + parameter.name());
      try { ranges.add(new IntegerParameterRange(parameter.name(), input.getMinimum(), input.getMaximum(), input.getStep())); }
      catch (IllegalArgumentException exception) { throw error("RESEARCH_PARAMETER_SPACE_INVALID", exception.getMessage()); }
    }
    if (request.getParameterSpace().size() != ranges.size()) throw error("RESEARCH_PARAMETER_SPACE_INVALID", "parameter range keys do not match strategy definition");
    return new StrategyResearchSpace(definition.code(), definition.version(), ranges);
  }

  private WalkForwardStudyCreateRequest childRequest(ResearchStudyCreateRequest request, Map<String, List<Integer>> grid) {
    WalkForwardStudyCreateRequest child = new WalkForwardStudyCreateRequest(); child.setDatasetId(request.getDatasetId()); child.setStrategyCode(request.getStrategyCode().trim()); child.setStrategyVersion(request.getStrategyVersion().trim()); child.setExecutionProfileCode(request.getExecutionProfileCode().trim()); child.setDirectionMode(request.getDirectionMode().trim()); child.setOrderSizingMode(request.getOrderSizingMode().trim()); child.setParameterGrid(grid); child.setStudyStartOpenTimeInclusive(request.getStudyStartOpenTimeInclusive()); child.setStudyEndOpenTimeExclusive(request.getStudyEndOpenTimeExclusive()); child.setTrainingBars(request.getTrainingBars()); child.setValidationBars(request.getValidationBars()); child.setSelectionMetric(request.getSelectionMetric().trim()); child.setMinimumTrainTrades(request.getMinimumTrainTrades()); child.setOrderAmount(request.getOrderAmount()); child.setFeeRate(request.getFeeRate()); child.setForceCloseAtEnd(true); return child;
  }

  private void validateRequest(ResearchStudyCreateRequest request) {
    if (request == null || request.getName() == null || request.getName().trim().isEmpty() || request.getName().trim().length() > 120 || request.getDatasetId() <= 0 || blank(request.getStrategyCode()) || blank(request.getStrategyVersion()) || blank(request.getExecutionProfileCode()) || blank(request.getDirectionMode()) || blank(request.getOrderSizingMode()) || request.getStudyStartOpenTimeInclusive() == null || request.getStudyEndOpenTimeExclusive() == null || request.getTrainingBars() < 1 || request.getValidationBars() < 1 || request.getMinimumTrainTrades() < 0 || blank(request.getSelectionMetric()) || request.getOrderAmount() == null || request.getFeeRate() == null || !request.isForceCloseAtEnd()) throw error("RESEARCH_REQUEST_INVALID", "invalid research study request");
    if (request.getDescription() != null && request.getDescription().trim().length() > 2000) throw error("RESEARCH_REQUEST_INVALID", "description is too long");
    if (!"WALK_FORWARD".equals(request.getEvaluationMode()) || !("STRATEGY_DEFAULT".equals(request.getParameterSpaceMode()) || "CUSTOM_INTEGER_RANGE".equals(request.getParameterSpaceMode()))) throw error("RESEARCH_REQUEST_INVALID", "evaluationMode or parameterSpaceMode is invalid");
    if (!request.getStudyStartOpenTimeInclusive().isBefore(request.getStudyEndOpenTimeExclusive())) throw error("RESEARCH_REQUEST_INVALID", "study range is not ordered");
  }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
  private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw error("RESEARCH_PERSISTENCE_FAILED", "research JSON serialization failed"); } }
  private ResearchStudyTaskException mapSpace(StrategyResearchException exception) { return error("STRATEGY_RESEARCH_SPACE_TOO_LARGE".equals(exception.getErrorCode()) ? "RESEARCH_PARAMETER_SPACE_TOO_LARGE" : "RESEARCH_PARAMETER_SPACE_INVALID", exception.getMessage()); }
  private ResearchStudyTaskException error(String code, String message) { String clean = (message == null ? code : message).replaceAll("[\\r\\n]", " "); return new ResearchStudyTaskException(code, clean.substring(0, Math.min(1000, clean.length()))); }
}
