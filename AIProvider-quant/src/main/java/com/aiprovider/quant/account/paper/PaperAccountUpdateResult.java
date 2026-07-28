package com.aiprovider.quant.account.paper;

import java.util.Objects;

public final class PaperAccountUpdateResult {
    private final PaperAccountSnapshot account;
    private final boolean applied;

    public PaperAccountUpdateResult(PaperAccountSnapshot account, boolean applied) {
        this.account = Objects.requireNonNull(account, "account");
        this.applied = applied;
    }

    public PaperAccountSnapshot getAccount() { return account; }
    public boolean isApplied() { return applied; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperAccountUpdateResult that)) return false;
        return applied == that.applied && Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, applied);
    }
}
