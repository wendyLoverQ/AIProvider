package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.row.BacktestRunRow;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BacktestRunRecoveryService {
    private final BacktestRunMapper runs;
    private final BacktestRunService service;
    private final AtomicBoolean executed = new AtomicBoolean();
    public BacktestRunRecoveryService(BacktestRunMapper runs, BacktestRunService service) { this.runs=runs; this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        if (!executed.compareAndSet(false,true)) return;
        for (BacktestRunRow row : runs.findNonTerminal()) {
            if ("QUEUED".equals(row.status)) service.resubmitQueued(row);
            else service.markInterruptedOnRestart(row.runId, row.status);
        }
    }
}
