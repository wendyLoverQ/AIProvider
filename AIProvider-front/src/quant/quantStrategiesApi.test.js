import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchQuantStrategies, parseStrategyList } from "./quantStrategiesApi";

const strategy = { code: "ema", name: "EMA 趋势", version: "1.0.0", description: "趋势规则", minimumRequiredBars: 30, parameters: [{ name: "period", defaultValue: 20, minValue: 2, maxValue: 200 }] };
const response = (data, overrides = {}) => ({ ok: true, status: 200, json: async () => ({ code: 200, data }), ...overrides });

describe("quant strategy API contracts", () => {
  afterEach(() => vi.restoreAllMocks());

  it("requests the real endpoint and forwards AbortSignal", async () => {
    const controller = new AbortController();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(response([strategy]));
    await expect(fetchQuantStrategies(controller.signal)).resolves.toEqual([strategy]);
    expect(fetchMock).toHaveBeenCalledWith("/api/quant/backtests/strategies", expect.objectContaining({ signal: controller.signal }));
  });

  it("accepts an empty list and rejects malformed strategy contracts", () => {
    expect(parseStrategyList([])).toEqual([]);
    expect(() => parseStrategyList({})).toThrow("策略服务响应格式异常");
    expect(() => parseStrategyList([{ ...strategy, code: "" }])).toThrow();
    expect(() => parseStrategyList([{ ...strategy, name: "" }])).toThrow();
    expect(() => parseStrategyList([{ ...strategy, version: "" }])).toThrow();
    expect(() => parseStrategyList([{ ...strategy, minimumRequiredBars: 0 }])).toThrow();
    expect(() => parseStrategyList([{ ...strategy }, { ...strategy }])).toThrow();
    expect(() => parseStrategyList([{ ...strategy, parameters: [{ name: "period", defaultValue: 201, minValue: 2, maxValue: 200 }] }])).toThrow();
  });

  it("rejects non-200 Result responses and non-2xx HTTP responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, status: 200, json: async () => ({ code: 500, message: "策略服务失败" }) });
    await expect(fetchQuantStrategies()).rejects.toThrow("策略服务失败");
    vi.restoreAllMocks();
    vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status: 503, json: async () => ({ code: 503, message: "暂不可用" }) });
    await expect(fetchQuantStrategies()).rejects.toThrow("暂不可用");
  });
});
