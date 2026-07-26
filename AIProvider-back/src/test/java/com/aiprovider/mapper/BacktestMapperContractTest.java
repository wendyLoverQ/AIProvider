package com.aiprovider.mapper;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BacktestMapperContractTest {
    @Test void backtestMappersDoNotUseSelectStar(){
        for(String file:List.of("BacktestRunMapper.java","BacktestTradeMapper.java","BacktestEquityMapper.java")){
            try{String source=Files.readString(Path.of("src/main/java/com/aiprovider/mapper",file));assertFalse(source.toUpperCase().contains("SELECT *"),file);}catch(Exception e){throw new AssertionError(e);}
        }
    }
}
