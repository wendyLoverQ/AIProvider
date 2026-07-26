package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service public class BacktestFailureService {
    private final BacktestRunMapper mapper;
    public BacktestFailureService(BacktestRunMapper mapper){this.mapper=mapper;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void markFailed(String runId,String code,String message){mapper.fail(runId,code,message==null?"":message.substring(0,Math.min(1000,message.length())),Instant.now());}
}
