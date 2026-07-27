package com.aiprovider.quant.research;

import com.aiprovider.quant.strategy.EmaCrossLongOnlyDefinition;
import com.aiprovider.quant.strategy.MacdTrendLongOnlyDefinition;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.RsiMeanReversionLongOnlyDefinition;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyParameterSpaceExpanderTest {
    private final StrategyParameterSpaceExpander expander = new StrategyParameterSpaceExpander();

    @Test
    void expandsTheThreeDefaultSpacesDeterministically() {
        ParameterSpaceExpansion ema = expander.expand(new EmaCrossLongOnlyDefinition(), new EmaCrossLongOnlyDefinition().researchSpace(), 64);
        assertThat(ema.candidateCount()).isEqualTo(12);
        LinkedHashMap<String, Integer> firstEma = new LinkedHashMap<>();
        firstEma.put("fastPeriod", 5);
        firstEma.put("slowPeriod", 30);
        assertThat(ema.combinations().get(0)).containsExactlyEntriesOf(firstEma);
        assertThat(ema.combinations().get(1).values()).containsExactly(5, 50);
        assertThat(ema.maximumRequiredBars()).isEqualTo(71);

        assertThat(expander.expand(new RsiMeanReversionLongOnlyDefinition(), new RsiMeanReversionLongOnlyDefinition().researchSpace(), 64).candidateCount()).isEqualTo(27);
        assertThat(expander.expand(new MacdTrendLongOnlyDefinition(), new MacdTrendLongOnlyDefinition().researchSpace(), 64).candidateCount()).isEqualTo(27);
    }

    @Test
    void explicitGridUsesTheSameOrderAndIsImmutable() {
        QuantStrategyDefinition definition = new EmaCrossLongOnlyDefinition();
        LinkedHashMap<String, List<Integer>> grid = new LinkedHashMap<>();
        grid.put("fastPeriod", List.of(5, 10));
        grid.put("slowPeriod", List.of(30, 50));
        ParameterSpaceExpansion expansion = expander.expandExplicitGrid(definition, grid, 64);
        assertThat(expansion.combinations().get(0).values()).containsExactly(5, 30);
        assertThat(expansion.combinations().get(1).values()).containsExactly(5, 50);
        assertThat(expansion.combinations().get(2).values()).containsExactly(10, 30);
        assertThat(expansion.combinations().get(3).values()).containsExactly(10, 50);
        assertThatThrownBy(() -> expansion.grid().get("fastPeriod").add(15)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> expansion.combinations().get(0).put("fastPeriod", 15)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(expansion.kind()).isEqualTo(ParameterSpaceKind.EXPLICIT_GRID);
        assertThat(expansion.rangeSpace()).isNull();
        assertThatThrownBy(() -> expansion.grid().put("other", List.of(1))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> expansion.combinations().add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMismatchedSpacesInvalidValuesAndCapacity() {
        QuantStrategyDefinition definition = new EmaCrossLongOnlyDefinition();
        StrategyResearchException mismatch = org.junit.jupiter.api.Assertions.assertThrows(StrategyResearchException.class,
                () -> expander.expand(definition, new StrategyResearchSpace("OTHER", definition.version(), definition.researchSpace().parameters()), 64));
        assertThat(mismatch.getErrorCode()).isEqualTo("STRATEGY_RESEARCH_SPACE_INVALID");
        LinkedHashMap<String, List<Integer>> tooLarge = new LinkedHashMap<>();
        tooLarge.put("fastPeriod", List.of(5, 10, 15, 20));
        tooLarge.put("slowPeriod", List.of(30, 50, 70));
        assertThatThrownBy(() -> expander.expandExplicitGrid(definition, tooLarge, 10)).isInstanceOf(StrategyResearchException.class)
                .extracting(exception -> ((StrategyResearchException) exception).getErrorCode()).isEqualTo("STRATEGY_RESEARCH_SPACE_TOO_LARGE");
    }

    @Test
    void validatesIntegerRangeWithoutOverflow() {
        assertThat(new IntegerParameterRange( "x", Integer.MAX_VALUE, Integer.MAX_VALUE, 1).values()).containsExactly(Integer.MAX_VALUE);
        assertThatThrownBy(() -> new IntegerParameterRange("x", Integer.MIN_VALUE, Integer.MAX_VALUE, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegerParameterRange("x", 1, 4, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangeAppliesPerParameterCapacityAndDefinitionBounds() {
        QuantStrategyDefinition definition = new EmaCrossLongOnlyDefinition();
        StrategyResearchSpace twenty = new StrategyResearchSpace(definition.code(), definition.version(), List.of(
                new IntegerParameterRange("fastPeriod", 2, 21, 1),
                new IntegerParameterRange("slowPeriod", 30, 30, 1)));
        assertThat(expander.expand(definition, twenty, 64).candidateCount()).isEqualTo(20);

        StrategyResearchSpace twentyOne = new StrategyResearchSpace(definition.code(), definition.version(), List.of(
                new IntegerParameterRange("fastPeriod", 2, 22, 1),
                new IntegerParameterRange("slowPeriod", 30, 30, 1)));
        assertThatThrownBy(() -> expander.expand(definition, twentyOne, 64))
                .extracting(exception -> ((StrategyResearchException) exception).getErrorCode())
                .isEqualTo("STRATEGY_RESEARCH_SPACE_TOO_LARGE");

        StrategyResearchSpace belowMinimum = new StrategyResearchSpace(definition.code(), definition.version(), List.of(
                new IntegerParameterRange("fastPeriod", 1, 2, 1),
                new IntegerParameterRange("slowPeriod", 30, 30, 1)));
        assertThatThrownBy(() -> expander.expand(definition, belowMinimum, 64))
                .extracting(exception -> ((StrategyResearchException) exception).getErrorCode())
                .isEqualTo("STRATEGY_RESEARCH_SPACE_INVALID");

        StrategyResearchSpace aboveMaximum = new StrategyResearchSpace(definition.code(), definition.version(), List.of(
                new IntegerParameterRange("fastPeriod", 2, 50, 48),
                new IntegerParameterRange("slowPeriod", 10, 201, 191)));
        assertThatThrownBy(() -> expander.expand(definition, aboveMaximum, 64))
                .extracting(exception -> ((StrategyResearchException) exception).getErrorCode())
                .isEqualTo("STRATEGY_RESEARCH_SPACE_INVALID");
    }

    @Test
    void equivalentRangeAndExplicitGridHaveIdenticalExpansion() {
        QuantStrategyDefinition definition = new EmaCrossLongOnlyDefinition();
        StrategyResearchSpace range = new StrategyResearchSpace(definition.code(), definition.version(), List.of(
                new IntegerParameterRange("fastPeriod", 5, 15, 5),
                new IntegerParameterRange("slowPeriod", 30, 50, 20)));
        LinkedHashMap<String, List<Integer>> explicit = new LinkedHashMap<>();
        explicit.put("fastPeriod", List.of(5, 10, 15));
        explicit.put("slowPeriod", List.of(30, 50));
        ParameterSpaceExpansion rangeExpansion = expander.expand(definition, range, 64);
        ParameterSpaceExpansion explicitExpansion = expander.expandExplicitGrid(definition, explicit, 64);
        assertThat(rangeExpansion.kind()).isEqualTo(ParameterSpaceKind.INTEGER_RANGE);
        assertThat(rangeExpansion.rangeSpace()).isEqualTo(range);
        assertThat(rangeExpansion.grid()).isEqualTo(explicit);
        assertThat(explicitExpansion.combinations()).isEqualTo(rangeExpansion.combinations());
        assertThat(explicitExpansion.candidateCount()).isEqualTo(rangeExpansion.candidateCount());
        assertThat(explicitExpansion.maximumRequiredBars()).isEqualTo(rangeExpansion.maximumRequiredBars());
        assertThatThrownBy(() -> rangeExpansion.rangeSpace().parameters().add(new IntegerParameterRange("other", 1, 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
