package com.aiprovider.quant.research;

public interface StrategyParameterSpaceProvider {
    default StrategyResearchSpace researchSpace() {
        throw new UnsupportedOperationException("researchSpace must be explicitly defined");
    }
}
