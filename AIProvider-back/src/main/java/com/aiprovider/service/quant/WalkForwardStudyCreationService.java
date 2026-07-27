package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantWalkForwardProperties;
import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.execution.*;
import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkForwardStudyCreationService {
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final com.aiprovider.quant.market.history.port.MarketDatasetRepository datasets;
  private final StrategyRegistry strategies;
  private final BacktestCompatibilityService compatibility;
  private final ObjectMapper json;
  private final QuantWalkForwardProperties properties;

  public WalkForwardStudyCreationService(
      WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds,
      com.aiprovider.quant.market.history.port.MarketDatasetRepository datasets,
      StrategyRegistry strategies,
      BacktestCompatibilityService compatibility,
      ObjectMapper json,
      QuantWalkForwardProperties properties) {
    this.studies = studies;
    this.folds = folds;
    this.datasets = datasets;
    this.strategies = strategies;
    this.compatibility = compatibility;
    this.json = json;
    this.properties = properties;
  }

  @Transactional
  public WalkForwardStudyDtos.CreateResponse create(WalkForwardStudyCreateRequest request) {
    validateRequest(request);
    MarketDataset dataset = datasets.findById(request.getDatasetId());
    validateDataset(dataset, request);
    QuantStrategyDefinition definition;
    try {
      definition = strategies.get(request.getStrategyCode().trim());
    } catch (StrategyException e) {
      throw error("WALK_FORWARD_REQUEST_INVALID", e.getMessage());
    }
    if (!definition.version().equals(request.getStrategyVersion().trim()))
      throw error("WALK_FORWARD_REQUEST_INVALID", "strategy version is not supported");
    BacktestExperimentGrid.Result grid;
    try {
      BacktestExperimentGrid.candidateCount(request.getParameterGrid(), definition, 64);
      grid = BacktestExperimentGrid.expand(request.getParameterGrid(), definition, 64);
    } catch (BacktestTaskException e) {
      if ("WALK_FORWARD_TOO_LARGE".equals(e.getErrorCode())) throw e;
      throw error("WALK_FORWARD_REQUEST_INVALID", e.getMessage());
    }
    try {
      for (var parameters : grid.combinations()) {
        compatibility.validate(
            request.getExecutionProfileCode(),
            request.getDirectionMode(),
            request.getOrderSizingMode(),
            definition,
            marketContext(dataset),
            parameters,
            request.getOrderAmount(),
            request.getFeeRate());
      }
    } catch (BacktestException e) {
      throw error(e.getErrorCode(), e.getMessage());
    }
    WalkForwardFoldGenerator.Result generated =
        WalkForwardFoldGenerator.generate(
            request,
            dataset,
            definition,
            properties.getMaxFolds(),
            properties.getMaxTotalChildRuns());
    String studyId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    WalkForwardStudyRow study = new WalkForwardStudyRow();
    study.studyId = studyId;
    study.datasetId = request.getDatasetId();
    study.provider = dataset.getProvider().name();
    study.marketType = dataset.getMarketType().name();
    study.dataType = dataset.getDataType().name();
    study.symbol = dataset.getSymbol();
    study.intervalCode = dataset.getInterval().code();
    study.strategyCode = request.getStrategyCode().trim();
    study.strategyVersion = request.getStrategyVersion().trim();
    study.executionProfileCode = request.getExecutionProfileCode();
    study.directionMode = request.getDirectionMode();
    study.orderSizingMode = request.getOrderSizingMode();
    study.parameterGridJson = write(grid.grid());
    study.windowMode = "ROLLING";
    study.studyStartOpenTimeMs = request.getStudyStartOpenTimeInclusive().toEpochMilli();
    study.studyEndOpenTimeMs = request.getStudyEndOpenTimeExclusive().toEpochMilli();
    study.trainingBars = request.getTrainingBars();
    study.validationBars = request.getValidationBars();
    study.stepBars = request.getValidationBars();
    study.foldCount = generated.folds().size();
    study.candidateCountPerFold = generated.candidateCountPerFold();
    study.totalChildRuns = generated.totalChildRuns();
    study.selectionMetric = request.getSelectionMetric().trim().toUpperCase();
    study.minimumTrainTrades = request.getMinimumTrainTrades();
    study.orderAmount = request.getOrderAmount();
    study.feeRate = request.getFeeRate();
    study.forceCloseAtEnd = true;
    study.createdAt = now;
    study.updatedAt = now;
    if (studies.insert(study) != 1)
      throw error(
          "WALK_FORWARD_STATE_CONFLICT", "study insert affected an unexpected number of rows");
    List<WalkForwardFoldRow> foldRows = generated.folds();
    for (WalkForwardFoldRow fold : foldRows) {
      fold.studyId = studyId;
      fold.createdAt = now;
      fold.updatedAt = now;
    }
    if (folds.insertBatch(foldRows) != foldRows.size())
      throw error(
          "WALK_FORWARD_STATE_CONFLICT", "fold insert affected an unexpected number of rows");
    return new WalkForwardStudyDtos.CreateResponse(
        studyId, study.foldCount, study.candidateCountPerFold, study.totalChildRuns);
  }

  private void validateRequest(WalkForwardStudyCreateRequest request) {
    if (request == null
        || request.getDatasetId() <= 0
        || blank(request.getStrategyCode())
        || blank(request.getStrategyVersion())
        || request.getParameterGrid() == null
        || request.getStudyStartOpenTimeInclusive() == null
        || request.getStudyEndOpenTimeExclusive() == null
        || request.getTrainingBars() < 1
        || request.getTrainingBars() > 1_000_000
        || request.getValidationBars() < 1
        || request.getValidationBars() > 100_000
        || request.getMinimumTrainTrades() < 0
        || request.getMinimumTrainTrades() > 1_000_000
        || blank(request.getSelectionMetric())
        || request.getOrderAmount() == null
        || !decimal(request.getOrderAmount())
        || request.getOrderAmount().signum() <= 0
        || request.getFeeRate() == null
        || !decimal(request.getFeeRate())
        || request.getFeeRate().signum() < 0
        || request.getFeeRate().compareTo(new BigDecimal("0.01")) > 0
        || !request.isForceCloseAtEnd())
      throw error("WALK_FORWARD_REQUEST_INVALID", "invalid walk-forward study request");
    try {
      WalkForwardSelectionMetric.valueOf(request.getSelectionMetric().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw error("WALK_FORWARD_REQUEST_INVALID", "selectionMetric is invalid");
    }
  }

  private void validateDataset(MarketDataset dataset, WalkForwardStudyCreateRequest request) {
    if (dataset == null) throw error("WALK_FORWARD_REQUEST_INVALID", "datasetId not found");
    long interval =
        dataset.getInterval() == null || !dataset.getInterval().isFixedDuration()
            ? 0
            : dataset.getInterval().durationMillis();
    Instant start = request.getStudyStartOpenTimeInclusive(),
        end = request.getStudyEndOpenTimeExclusive();
    if (dataset.getStatus() == null
        || !"CONTIGUOUS".equals(dataset.getStatus().name())
        || dataset.getInterval() == null
        || !dataset.getInterval().isFixedDuration()
        || dataset.getEarliestOpenTime() == null
        || dataset.getLatestOpenTime() == null
        || dataset.getCandleCount() <= 0
        || dataset.getGapCount() != 0
        || dataset.getGapSegmentCount() != 0
        || dataset.getLastValidatedAt() == null
        || interval <= 0
        || start.isBefore(dataset.getEarliestOpenTime())
        || end.isAfter(dataset.getLatestOpenTime().plusMillis(interval)))
      throw error(
          "WALK_FORWARD_WINDOW_INVALID", "study range is outside contiguous dataset coverage");
  }

  private boolean decimal(BigDecimal value) {
    if (value.scale() > 18 || value.precision() > 38) return false;
    BigDecimal normalized = value.stripTrailingZeros();
    return Math.max(0, normalized.precision() - normalized.scale()) <= 20;
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw error("WALK_FORWARD_REQUEST_INVALID", "parameter grid cannot be serialized");
    }
  }

  private WalkForwardTaskException error(String code, String message) {
    String text = (message == null ? "walk-forward failed" : message).replaceAll("[\\r\\n]", " ");
    return new WalkForwardTaskException(code, text.substring(0, Math.min(1000, text.length())));
  }

  private BacktestMarketContext marketContext(MarketDataset dataset) {
    return new BacktestMarketContext(
        dataset.getProvider().name(),
        dataset.getMarketType(),
        dataset.getDataType().name(),
        dataset.getSymbol(),
        dataset.getInterval(),
        dataset.getDataType()
                == com.aiprovider.quant.market.history.model.MarketDataType.CANDLE
            ? Set.of(MarketFeature.OHLCV)
            : Set.of());
  }
}
