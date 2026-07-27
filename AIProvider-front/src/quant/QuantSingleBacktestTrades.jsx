import { ArrowsClockwise, CaretLeft, CaretRight, Warning } from "@phosphor-icons/react";
import {
  formatDecimalString,
  formatInstant,
  formatRatioString,
} from "./quantBacktestsFormat";
import {
  formatOrderSide,
  formatPositionSide,
} from "./quantExecutionContext";

function LoadState({ label }) {
  return (
    <div className="quant-loading" role="status">
      {label}
    </div>
  );
}

function ErrorState({ error, retry }) {
  return (
    <div className="quant-error" role="alert">
      <Warning weight="fill" />
      <div>
        <strong>交易记录加载失败</strong>
        <span>{error}</span>
      </div>
      <button type="button" className="quant-error-retry" onClick={retry}>
        <ArrowsClockwise />
        重试
      </button>
    </div>
  );
}

export default function QuantSingleBacktestTrades({
  run,
  page,
  data,
  state,
  error,
  onPage,
  retry,
}) {
  if (run?.status !== "COMPLETED") return null;
  const totalPages = Math.max(1, Math.ceil((data?.total || 0) / 100));
  return (
    <section className="backtest-card">
      <header className="quant-section-head">
        <h4>交易记录</h4>
        <small>{data?.total || 0} 条</small>
      </header>
      {state === "loading" ? (
        <LoadState label="正在读取交易…" />
      ) : state === "error" ? (
        <ErrorState
          error={error || data?.error || "交易接口请求失败"}
          retry={retry}
        />
      ) : (
        <>
          <div className="backtest-table-wrap">
            <table>
              <thead>
                <tr>
                  {[
                    "编号",
                    "持仓",
                    "开仓",
                    "平仓",
                    "入场时间",
                    "入场价",
                    "退出时间",
                    "退出价",
                    "数量",
                    "净利润",
                    "收益率",
                    "持有 Bar",
                    "退出原因",
                  ].map((label) => (
                    <th key={label}>{label}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(data?.records || []).map((trade) => (
                  <tr key={trade.tradeNo}>
                    <td>{trade.tradeNo}</td>
                    <td>{formatPositionSide(trade.positionSide)}</td>
                    <td>{formatOrderSide(trade.entryOrderSide)}</td>
                    <td>{formatOrderSide(trade.exitOrderSide)}</td>
                    <td>{formatInstant(trade.entryTime)}</td>
                    <td>{formatDecimalString(trade.entryPrice)}</td>
                    <td>{formatInstant(trade.exitTime)}</td>
                    <td>{formatDecimalString(trade.exitPrice)}</td>
                    <td>{formatDecimalString(trade.amount)}</td>
                    <td>{formatDecimalString(trade.netProfit)}</td>
                    <td>{formatRatioString(trade.returnRatio)}</td>
                    <td>{trade.barsHeld ?? "—"}</td>
                    <td>
                      {trade.exitReason === "END_OF_SERIES" || trade.forcedExit
                        ? "期末强平"
                        : "策略退出"}
                    </td>
                  </tr>
                ))}
                {!(data?.records || []).length && (
                  <tr>
                    <td colSpan="13" className="backtest-empty">
                      已完成但没有交易
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="backtest-pagination">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => onPage(page - 1)}
            >
              <CaretLeft />
              上一页
            </button>
            <span>
              第 {page} / {totalPages} 页
            </span>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => onPage(page + 1)}
            >
              下一页
              <CaretRight />
            </button>
          </div>
        </>
      )}
    </section>
  );
}
