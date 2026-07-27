const MARKET_TYPES = new Set(["USDM_PERPETUAL"]);
const DIRECTION_MODES = new Set(["LONG_ONLY"]);
const ORDER_SIZING_MODES = new Set(["BASE_QUANTITY"]);

const unique = (values) => [...new Set(values)];
const sameValue = (left, right) =>
  left.marketType === right.marketType &&
  String(left.datasetId || "") === String(right.datasetId || "") &&
  left.strategyCode === right.strategyCode &&
  left.executionProfileCode === right.executionProfileCode;

export function datasetFeatures(dataset) {
  return dataset?.dataType === "KLINE" ? ["OHLCV"] : [];
}

function hasFeatures(required, available) {
  return required.every((feature) => available.includes(feature));
}

function isUsableDataset(dataset) {
  return (
    dataset &&
    dataset.status === "CONTIGUOUS" &&
    Number(dataset.gapCount) === 0 &&
    Number(dataset.gapSegmentCount) === 0
  );
}

export function marketTypesFromDatasets(datasets) {
  return unique(
    (datasets || [])
      .filter(isUsableDataset)
      .map((dataset) => dataset.marketType)
      .filter((marketType) => MARKET_TYPES.has(marketType)),
  );
}

export function compatibleDatasets(datasets, marketType) {
  if (!MARKET_TYPES.has(marketType)) return [];
  return (datasets || []).filter(
    (dataset) =>
      isUsableDataset(dataset) && dataset.marketType === marketType,
  );
}

export function compatibleStrategies(strategies, dataset) {
  if (!isUsableDataset(dataset)) return [];
  const features = datasetFeatures(dataset);
  return (strategies || []).filter(
    (strategy) =>
      strategy.supportedMarketTypes?.includes(dataset.marketType) &&
      hasFeatures(strategy.requiredMarketFeatures || [], features),
  );
}

export function compatibleProfiles(profiles, dataset, strategy) {
  if (!isUsableDataset(dataset) || !strategy) return [];
  const features = datasetFeatures(dataset);
  return (profiles || []).filter(
    (profile) =>
      profile.marketType === dataset.marketType &&
      strategy.supportedExecutionProfileCodes?.includes(profile.code) &&
      strategy.supportedDirectionModes?.includes(profile.directionMode) &&
      hasFeatures(profile.requiredMarketFeatures || [], features),
  );
}

export function resolveExecutionSelection({
  datasets = [],
  strategies = [],
  profiles = [],
  value = {},
}) {
  const marketTypes = marketTypesFromDatasets(datasets);
  const marketType = marketTypes.includes(value.marketType)
    ? value.marketType
    : marketTypes.length === 1
      ? marketTypes[0]
      : "";
  const nextDatasets = compatibleDatasets(datasets, marketType);
  const dataset = nextDatasets.find(
    (item) => String(item.id) === String(value.datasetId),
  );
  const nextStrategies = compatibleStrategies(strategies, dataset);
  const strategy = nextStrategies.find(
    (item) => item.code === value.strategyCode,
  );
  const nextProfiles = compatibleProfiles(profiles, dataset, strategy);
  const selectedProfile = nextProfiles.find(
    (item) => item.code === value.executionProfileCode,
  );
  const executionProfileCode = selectedProfile
    ? selectedProfile.code
    : nextProfiles.length === 1
      ? nextProfiles[0].code
      : "";
  return {
    marketType,
    datasetId: dataset ? String(dataset.id) : "",
    strategyCode: strategy?.code || "",
    executionProfileCode,
  };
}

export function validateExecutionSelection({
  datasets = [],
  strategies = [],
  profiles = [],
  value = {},
}) {
  const errors = {};
  const marketTypes = marketTypesFromDatasets(datasets);
  if (!value.marketType || !marketTypes.includes(value.marketType))
    errors.marketType = "请选择受支持的市场类型";
  const dataset = compatibleDatasets(datasets, value.marketType).find(
    (item) => String(item.id) === String(value.datasetId),
  );
  if (!dataset) errors.datasetId = "请选择连续且无缺口的数据集";
  const strategy = compatibleStrategies(strategies, dataset).find(
    (item) => item.code === value.strategyCode,
  );
  if (!strategy) errors.strategyCode = "请选择与数据集兼容的策略";
  const profile = compatibleProfiles(profiles, dataset, strategy).find(
    (item) => item.code === value.executionProfileCode,
  );
  if (!profile)
    errors.executionProfileCode = "请选择与数据集和策略兼容的执行模型";
  return { valid: Object.keys(errors).length === 0, errors, dataset, strategy, profile };
}

export function executionContextPayload(profile) {
  if (!profile) return null;
  return {
    executionProfileCode: profile.code,
    directionMode: profile.directionMode,
    orderSizingMode: profile.orderSizingMode,
  };
}

export function formatMarketType(value) {
  return value === "USDM_PERPETUAL" ? "USDT 本位永续合约" : value || "—";
}

export function formatDirectionMode(value) {
  return value === "LONG_ONLY" ? "只做多" : value || "—";
}

export function formatOrderSizingMode(value) {
  return value === "BASE_QUANTITY" ? "基础资产数量" : value || "—";
}

export function formatOrderSide(value) {
  return value === "BUY" ? "买入" : value === "SELL" ? "卖出" : value || "—";
}

export function formatPositionSide(value) {
  return value === "LONG" ? "多头" : value || "—";
}

export function executionSelectionEqual(left, right) {
  return sameValue(left || {}, right || {});
}
