package com.aiprovider.controller.quant;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class BacktestCreateRequestValidationTest {
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
    @Test void rejectsFeeAboveOnePercentAndForcedCloseFalse(){BacktestCreateRequest r=validRequest();r.setFeeRate(new BigDecimal("0.02"));r.setForceCloseAtEnd(false);assertFalse(validator.validate(r).isEmpty());}
    @Test void requiresPositiveInitialCapital(){BacktestCreateRequest r=validRequest();r.setInitialCapital(null);assertTrue(validator.validate(r).stream().anyMatch(v->"initialCapital".equals(v.getPropertyPath().toString())));r.setInitialCapital(BigDecimal.ZERO);assertTrue(validator.validate(r).stream().anyMatch(v->"initialCapital".equals(v.getPropertyPath().toString())));}
    private BacktestCreateRequest validRequest(){BacktestCreateRequest r=new BacktestCreateRequest();r.setDatasetId(1);r.setStartOpenTimeInclusive(Instant.EPOCH);r.setEndOpenTimeExclusive(Instant.ofEpochMilli(60000));r.setStrategyCode("EMA_CROSS_LONG_ONLY");r.setStrategyVersion("1.0.0");r.setInitialCapital(new BigDecimal("1000"));r.setOrderAmount(BigDecimal.ONE);r.setFeeRate(new BigDecimal("0.001"));r.setForceCloseAtEnd(true);return r;}
}
