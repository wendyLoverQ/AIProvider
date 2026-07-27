package com.aiprovider.quant.strategy;

import com.aiprovider.quant.research.StrategyParameterSpaceExpander;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StrategyRegistry {
    private final Map<String, QuantStrategyDefinition> definitions = new LinkedHashMap<>();
    public StrategyRegistry() { register(new EmaCrossLongOnlyDefinition()); register(new RsiMeanReversionLongOnlyDefinition()); register(new MacdTrendLongOnlyDefinition()); }
    public void register(QuantStrategyDefinition definition) {
        if (definition == null || definition.code() == null || definitions.containsKey(definition.code())) throw new StrategyException("STRATEGY_CODE_DUPLICATE", "duplicate strategy code");
        try {
            if (!definition.code().equals(definition.researchSpace().strategyCode()) || !definition.version().equals(definition.researchSpace().strategyVersion())) throw new StrategyException("STRATEGY_RESEARCH_SPACE_INVALID", "research space identity does not match strategy");
            new StrategyParameterSpaceExpander().expand(definition, definition.researchSpace(), 64);
        }
        catch (RuntimeException exception) { throw new StrategyException("STRATEGY_RESEARCH_SPACE_INVALID", exception.getMessage()); }
        definitions.put(definition.code(), definition);
    }
    public QuantStrategyDefinition get(String code) {
        QuantStrategyDefinition definition = definitions.get(code);
        if (definition == null) throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategyCode=" + code);
        return definition;
    }
    public Collection<QuantStrategyDefinition> list() { return ListCopy.copy(definitions.values()); }
    private static final class ListCopy { static <T> Collection<T> copy(Collection<T> values) { return java.util.List.copyOf(values); } }
}
