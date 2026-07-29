package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;

import java.time.Instant;
import java.util.Objects;

public final class PaperRuntimeCheckpoint {
    private final String sessionId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;
    private final long version;
    private final PaperRuntimeSnapshot runtime;
    private final PaperOrderAuditLedger ledger;
    private final PaperReconciliationReport reconciliationReport;
    private final Instant createdAt;

    public PaperRuntimeCheckpoint(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            PaperReconciliationReport reconciliationReport,
            long version,
            Instant createdAt) {
        this(ledger == null ? null : ledger.getSessionId(),
                ledger == null ? null : ledger.getProvider(),
                ledger == null ? null : ledger.getMarketType(),
                ledger == null ? null : ledger.getSymbol(),
                runtime == null ? null : runtime.getConfig().getMarketKey().getInterval(),
                version, runtime, ledger, reconciliationReport, createdAt);
    }

    public PaperRuntimeCheckpoint(
            String sessionId,
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            KlineInterval interval,
            long version,
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            PaperReconciliationReport reconciliationReport,
            Instant createdAt) {
        if (blank(sessionId) || provider == null || marketType == null || blank(symbol)
                || interval == null || version < 0 || runtime == null || ledger == null
                || reconciliationReport == null || createdAt == null) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "checkpoint fields are required and version must be non-negative");
        }
        if (!sessionId.equals(runtime.getTradingSession().getConfig().getSessionId())
                || !sessionId.equals(ledger.getSessionId())
                || provider != runtime.getConfig().getMarketKey().getProvider()
                || provider != ledger.getProvider()
                || marketType != runtime.getConfig().getMarketKey().getMarketType()
                || marketType != ledger.getMarketType()
                || !symbol.equals(runtime.getConfig().getMarketKey().getSymbol())
                || !symbol.equals(ledger.getSymbol())
                || interval != runtime.getConfig().getMarketKey().getInterval()) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_CONTEXT_MISMATCH,
                    "checkpoint context does not match Runtime and Ledger");
        }
        if (reconciliationReport.getStatus() != PaperReconciliationStatus.CONSISTENT) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT,
                    "checkpoint reconciliation report must be CONSISTENT");
        }
        if (!createdAt.equals(reconciliationReport.getReconciledAt())) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID,
                    "reconciliationReport.reconciledAt must equal createdAt");
        }
        Instant latestStateTime = latestStateTime(runtime, ledger);
        if (createdAt.isBefore(latestStateTime)) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID,
                    "createdAt must not precede latest Runtime, Ledger or market state time: "
                            + latestStateTime);
        }
        this.sessionId = sessionId;
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.version = version;
        this.runtime = runtime;
        this.ledger = ledger;
        this.reconciliationReport = reconciliationReport;
        this.createdAt = copy(createdAt);
    }

    public String getSessionId() { return sessionId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public long getVersion() { return version; }
    public PaperRuntimeSnapshot getRuntime() { return runtime; }
    public PaperRuntimeSnapshot getPaperRuntimeSnapshot() { return runtime; }
    public PaperOrderAuditLedger getLedger() { return ledger; }
    public PaperOrderAuditLedger getPaperOrderAuditLedger() { return ledger; }
    public PaperReconciliationReport getReconciliationReport() { return reconciliationReport; }
    public PaperReconciliationReport getPaperReconciliationReport() {
        return reconciliationReport;
    }
    public Instant getCreatedAt() { return copy(createdAt); }

    /** Returns the deterministic lower bound for a checkpoint creation time. */
    static Instant latestStateTime(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger) {
        Instant latest = runtime.getTradingSession().getLastUpdatedAt();
        latest = later(latest, ledger.getLastUpdatedAt());
        latest = later(latest, runtime.getLastProcessedEventTime());
        latest = later(latest, runtime.getMarketState().getLastKlineEventTime());
        latest = later(latest, runtime.getMarketState().getLastBookTickerEventTime());
        latest = later(latest, runtime.getMarketState().getLastMarkPriceEventTime());
        return latest;
    }

    private static Instant later(Instant left, Instant right) {
        return right != null && right.isAfter(left) ? right : left;
    }

    private static Instant copy(Instant value) {
        return Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PaperCheckpointException error(String code, String message) {
        return new PaperCheckpointException(code, message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperRuntimeCheckpoint that)) return false;
        return version == that.version
                && sessionId.equals(that.sessionId)
                && provider == that.provider
                && marketType == that.marketType
                && symbol.equals(that.symbol)
                && interval == that.interval
                && runtime.equals(that.runtime)
                && ledger.equals(that.ledger)
                && reconciliationReport.equals(that.reconciliationReport)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, provider, marketType, symbol, interval, version,
                runtime, ledger, reconciliationReport, createdAt);
    }
}
