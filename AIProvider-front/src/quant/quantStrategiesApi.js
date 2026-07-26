import { readJsonResponse } from "../apiResponse";

const STRATEGIES_URL = "/api/quant/backtests/strategies";
const INVALID_RESPONSE = "策略服务响应格式异常";

const isNonEmptyString = (value) => typeof value === "string" && value.trim().length > 0;
const isSafeInteger = (value) => Number.isSafeInteger(value);

function parseParameter(parameter) {
  if (!parameter || !isNonEmptyString(parameter.name)
    || !isSafeInteger(parameter.defaultValue)
    || !isSafeInteger(parameter.minValue)
    || !isSafeInteger(parameter.maxValue)
    || parameter.minValue > parameter.maxValue
    || parameter.defaultValue < parameter.minValue
    || parameter.defaultValue > parameter.maxValue) {
    throw new Error(INVALID_RESPONSE);
  }
  return parameter;
}

export function parseStrategyList(data) {
  if (!Array.isArray(data)) throw new Error(INVALID_RESPONSE);
  const codes = new Set();
  return data.map((strategy) => {
    if (!strategy || !isNonEmptyString(strategy.code) || !isNonEmptyString(strategy.name)
      || !isNonEmptyString(strategy.version) || typeof strategy.description !== "string"
      || !isSafeInteger(strategy.minimumRequiredBars) || strategy.minimumRequiredBars <= 0
      || !Array.isArray(strategy.parameters) || codes.has(strategy.code)) {
      throw new Error(INVALID_RESPONSE);
    }
    codes.add(strategy.code);
    return { ...strategy, parameters: strategy.parameters.map(parseParameter) };
  });
}

export async function fetchQuantStrategies(signal) {
  const response = await fetch(STRATEGIES_URL, { signal, headers: { "Content-Type": "application/json" } });
  const payload = await readJsonResponse(response, INVALID_RESPONSE);
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  return parseStrategyList(payload.data);
}
