package com.aiprovider.quant.strategy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StrategyRegistry {
    private final Map<String, QuantStrategyDefinition> definitions = new LinkedHashMap<>();
    public StrategyRegistry() { register(new EmaCrossLongOnlyDefinition()); }
    public void register(QuantStrategyDefinition definition) {
        if (definition == null || definition.code() == null || definitions.putIfAbsent(definition.code(), definition) != null) throw new StrategyException("STRATEGY_CODE_DUPLICATE", "duplicate strategy code");
    }
    public QuantStrategyDefinition get(String code) {
        QuantStrategyDefinition definition = definitions.get(code);
        if (definition == null) throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategyCode=" + code);
        return definition;
    }
    public Collection<QuantStrategyDefinition> list() { return ListCopy.copy(definitions.values()); }
    private static final class ListCopy { static <T> Collection<T> copy(Collection<T> values) { return java.util.List.copyOf(values); } }
}
