package com.aiprovider.quant.portfolio.sizing;

public interface PositionSizingEngine {
    PositionSizingResult calculate(PositionSizingRequest request);
}
