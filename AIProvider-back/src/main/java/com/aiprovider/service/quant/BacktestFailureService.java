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
    public void markFailed(String runId,String code,String message){
        com.aiprovider.logging.BusinessOperationLogger.start("service.quant.BacktestFailureService.markFailed", new String[] { "runId", "code", "message" }, new Object[] { runId, code, message });
        int affected=mapper.fail(runId,code,message==null?"":message.substring(0,Math.min(1000,message.length())),Instant.now());if(affected!=1)throw new IllegalStateException("BACKTEST_STATE_CONFLICT runId="+runId+" fail update affected="+affected);}
}
