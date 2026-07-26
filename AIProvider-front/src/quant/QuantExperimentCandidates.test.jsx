/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantExperimentCandidates from "./QuantExperimentCandidates";

const metrics = (returnRatio, drawdown, profitFactor, tradeCount) => ({
  totalReturnRatio: returnRatio,
  maximumDrawdownRatio: drawdown,
  profitFactor,
  tradeCount,
});

const candidate = {
  candidateId: "candidate-1",
  candidateIndex: 4,
  parameters: { unknown: 9, entry: 30, period: 14 },
  dispatchStatus: "CLAIMED",
  training: { segmentType: "TRAIN", runId: "train-1", status: "COMPLETED", metrics: metrics("0.1", "0.02", "1.5", 10) },
  validation: { segmentType: "VALIDATION", runId: "validation-1", status: "COMPLETED", metrics: metrics("-0.03", "0.08", "0.7", 3) },
};

const baseProps = {
  data: { records: [candidate], total: 51, page: 1, pageSize: 50 },
  page: 1,
  sortBy: "CANDIDATE_INDEX",
  order: "ASC",
  strategy: { parameters: [{ name: "period" }, { name: "entry" }] },
  loading: false,
  error: "",
  selectedId: "",
  onPage: vi.fn(),
  onSort: vi.fn(),
  onSelect: vi.fn(),
  onRetry: vi.fn(),
};

describe("QuantExperimentCandidates", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps TRAIN and VALIDATION metrics separate, shows null as dash, and orders parameters", () => {
    render(<QuantExperimentCandidates {...baseProps} />);
    const row = screen.getByRole("button", { name: "查看候选 4" }).closest("tr");
    expect(row.textContent).toContain("period=14 · entry=30 · unknown=9");
    expect(row.textContent).toContain("+10.00%");
    expect(row.textContent).toContain("-3.00%");
    expect(row.textContent).toContain("正在派发");
    cleanup();
    render(<QuantExperimentCandidates {...baseProps} data={{ ...baseProps.data, records: [{ ...candidate, validation: { ...candidate.validation, metrics: { ...candidate.validation.metrics, profitFactor: null } } }] }} />);
    expect(screen.getByRole("button", { name: "查看候选 4" }).closest("tr").textContent).toContain("—");
  });

  it("selects through a native button and sends server sorting and paging", () => {
    render(<QuantExperimentCandidates {...baseProps} />);
    fireEvent.click(screen.getByRole("button", { name: "查看候选 4" }));
    expect(baseProps.onSelect).toHaveBeenCalledWith(candidate);
    fireEvent.click(screen.getByRole("button", { name: "TRAIN 总收益率" }));
    expect(baseProps.onSort).toHaveBeenCalledWith("TRAIN_TOTAL_RETURN_RATIO", "ASC");
    fireEvent.click(screen.getByRole("button", { name: /下一页/ }));
    expect(baseProps.onPage).toHaveBeenCalledWith(2);
  });
});
