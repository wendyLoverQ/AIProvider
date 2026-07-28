package com.aiprovider.quant.risk.pretrade;

import com.aiprovider.quant.execution.order.ExecutionOrderRequest;

public interface PreTradeRiskEngine {
    PreTradeRiskDecision evaluate(
            ExecutionOrderRequest request,
            PreTradeRiskContext context,
            PreTradeRiskPolicy policy);
}
