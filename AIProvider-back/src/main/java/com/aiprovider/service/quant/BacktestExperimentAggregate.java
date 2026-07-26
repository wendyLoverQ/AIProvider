package com.aiprovider.service.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Single source of truth for experiment progress, counts, and terminal status. */
public final class BacktestExperimentAggregate {
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
    private BacktestExperimentAggregate() {}

    public static Result calculate(int candidateCount,List<CandidateState> candidates){
        int pending=0,active=0,completedCandidates=0,failedCandidates=0,completedLegs=0,failedLegs=0;
        BigDecimal progress=BigDecimal.ZERO;
        for(CandidateState candidate:candidates){
            if("PENDING".equals(candidate.dispatchStatus())) pending++;
            RunState train=candidate.training(),validation=candidate.validation();
            boolean trainTerminal=train.terminal(),validationTerminal=validation.terminal();
            boolean dispatchFailed="FAILED".equals(candidate.dispatchStatus());
            boolean missingDispatched="DISPATCHED".equals(candidate.dispatchStatus())&&(!train.exists()||!validation.exists());
            if("CLAIMED".equals(candidate.dispatchStatus())||("DISPATCHED".equals(candidate.dispatchStatus())&&!missingDispatched&&(!trainTerminal||!validationTerminal))) active++;
            completedLegs+=train.completed()?1:0; completedLegs+=validation.completed()?1:0;
            failedLegs+=train.failed()?1:0; failedLegs+=validation.failed()?1:0;
            if(dispatchFailed||missingDispatched){if(!train.exists())failedLegs++;if(!validation.exists())failedLegs++;}
            progress=progress.add(legProgress(train,(dispatchFailed||missingDispatched)&&!train.exists())).add(legProgress(validation,(dispatchFailed||missingDispatched)&&!validation.exists()));
            boolean failed=dispatchFailed||train.failed()||validation.failed()||missingDispatched;
            if(failed) failedCandidates++; else if(train.completed()&&validation.completed()) completedCandidates++;
        }
        BigDecimal total=progress.divide(BigDecimal.valueOf(Math.max(1,candidateCount*2L)),2,RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(HUNDRED);
        String status;
        if(pending==0&&active==0&&completedCandidates+failedCandidates==candidateCount) status=failedCandidates==0?"COMPLETED":completedCandidates==0?"FAILED":"COMPLETED_WITH_FAILURES";
        else if(active>0||completedCandidates>0||failedCandidates>0) status="RUNNING";
        else status="QUEUED";
        return new Result(status,total,pending,active,completedCandidates,failedCandidates,completedLegs,failedLegs);
    }
    private static BigDecimal legProgress(RunState state,boolean missingFailed){if(missingFailed||state.completed()||state.failed())return HUNDRED;if(!state.exists()||state.progress()==null)return BigDecimal.ZERO;return state.progress().max(BigDecimal.ZERO).min(HUNDRED);}
    public record RunState(boolean exists,String status,BigDecimal progress){public boolean completed(){return exists&&"COMPLETED".equals(status);}public boolean failed(){return exists&&"FAILED".equals(status);}public boolean terminal(){return completed()||failed();}}
    public record CandidateState(String dispatchStatus,RunState training,RunState validation){}
    public record Result(String status,BigDecimal progressPercent,int pendingCandidates,int activeCandidates,int completedCandidates,int failedCandidates,int completedLegs,int failedLegs){}
}
