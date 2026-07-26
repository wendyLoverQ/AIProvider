package com.aiprovider.mapper;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExperimentMapperSqlParseTest {
    @Test void annotationMappersParseWithMyBatis(){
        Configuration configuration=new Configuration();
        assertDoesNotThrow(() -> configuration.addMapper(BacktestExperimentMapper.class));
        assertDoesNotThrow(() -> configuration.addMapper(BacktestExperimentCandidateMapper.class));
    }
}
