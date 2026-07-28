package com.aiprovider.quant.reconciliation.paper;

public interface PaperReconciliationEngine {
    PaperReconciliationReport reconcile(PaperReconciliationRequest request);
}
