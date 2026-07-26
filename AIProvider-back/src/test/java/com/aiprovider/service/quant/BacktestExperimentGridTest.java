package com.aiprovider.service.quant;

import com.aiprovider.quant.strategy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExperimentGridTest {
    private final StrategyRegistry registry = new StrategyRegistry();

    @Test void expandsInDefinitionOrderAndLastParameterChangesFastest() {
        QuantStrategyDefinition definition = registry.get(EmaCrossLongOnlyDefinition.CODE);
        LinkedHashMap<String,List<Integer>> input = new LinkedHashMap<>();
        input.put("fastPeriod", List.of(5, 7)); input.put("slowPeriod", List.of(20, 30));
        BacktestExperimentGrid.Result result = BacktestExperimentGrid.expand(input, definition, 64);
        assertEquals(List.of("fastPeriod", "slowPeriod"), new ArrayList<>(result.grid().keySet()));
        assertEquals(List.of(5, 20), new ArrayList<>(result.combinations().get(0).values()));
        assertEquals(List.of(5, 30), new ArrayList<>(result.combinations().get(1).values()));
        assertEquals(List.of(7, 20), new ArrayList<>(result.combinations().get(2).values()));
        assertEquals(List.of(7, 30), new ArrayList<>(result.combinations().get(3).values()));
        assertEquals(List.of(5, 7), input.get("fastPeriod"));
        assertThrows(UnsupportedOperationException.class, () -> result.grid().get("fastPeriod").add(9));
        assertThrows(UnsupportedOperationException.class, () -> result.combinations().get(0).put("fastPeriod", 9));
    }

    @Test void validatesRsiAndMacdUsingTheirRealDefinitions() {
        LinkedHashMap<String,List<Integer>> rsi = new LinkedHashMap<>();
        rsi.put("rsiPeriod", List.of(7, 14)); rsi.put("entryThreshold", List.of(20)); rsi.put("exitThreshold", List.of(55));
        assertEquals(2, BacktestExperimentGrid.expand(rsi, registry.get(RsiMeanReversionLongOnlyDefinition.CODE), 64).combinations().size());
        LinkedHashMap<String,List<Integer>> macd = new LinkedHashMap<>();
        macd.put("fastPeriod", List.of(5)); macd.put("slowPeriod", List.of(20)); macd.put("signalPeriod", List.of(3, 5));
        assertEquals(2, BacktestExperimentGrid.expand(macd, registry.get(MacdTrendLongOnlyDefinition.CODE), 64).combinations().size());
        macd.put("fastPeriod", List.of(30));
        assertThrows(BacktestTaskException.class, () -> BacktestExperimentGrid.expand(macd, registry.get(MacdTrendLongOnlyDefinition.CODE), 64));
    }

    @Test void rejectsDuplicateUnknownAndMoreThan64Candidates() {
        QuantStrategyDefinition definition = definition("a", "b");
        LinkedHashMap<String,List<Integer>> duplicate = new LinkedHashMap<>(); duplicate.put("a", List.of(1, 1)); duplicate.put("b", List.of(2));
        BacktestTaskException duplicateError = assertThrows(BacktestTaskException.class, () -> BacktestExperimentGrid.expand(duplicate, definition, 64));
        assertEquals("BACKTEST_EXPERIMENT_GRID_INVALID", duplicateError.getErrorCode());
        LinkedHashMap<String,List<Integer>> tooMany = new LinkedHashMap<>(); tooMany.put("a", List.of(1,2,3,4,5)); tooMany.put("b", new ArrayList<>(java.util.stream.IntStream.rangeClosed(1,13).boxed().toList()));
        assertThrows(BacktestTaskException.class, () -> BacktestExperimentGrid.expand(tooMany, definition, 64));
        LinkedHashMap<String,List<Integer>> unknown = new LinkedHashMap<>(); unknown.put("a", List.of(1)); unknown.put("c", List.of(1));
        assertThrows(BacktestTaskException.class, () -> BacktestExperimentGrid.expand(unknown, definition, 64));
    }

    private QuantStrategyDefinition definition(String... names) {
        List<StrategyParameterDefinition> parameters = Arrays.stream(names).map(name -> new StrategyParameterDefinition(name,1,1,20)).toList();
        return new QuantStrategyDefinition() {
            public String code(){return "TEST";} public String name(){return "test";} public String version(){return "1";} public String description(){return "test";}
            public List<StrategyParameterDefinition> parameters(){return parameters;} public int minimumRequiredBars(Map<String,Integer> values){return 1;} public StrategyBuildResult build(Map<String,Integer> values,int barCount){return null;}
        };
    }
}
