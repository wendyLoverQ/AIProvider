package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class BacktestExperimentDispatcher {
    private final BacktestExperimentMapper experiments; private final BacktestExperimentCandidateMapper candidates; private final BacktestRunMapper runs; private final BacktestRunService runService; private final BacktestExperimentService aggregate; private final com.aiprovider.config.quant.QuantExperimentProperties properties; private final ObjectMapper json;
    public BacktestExperimentDispatcher(BacktestExperimentMapper e,BacktestExperimentCandidateMapper c,BacktestRunMapper r,BacktestRunService s,BacktestExperimentService a,com.aiprovider.config.quant.QuantExperimentProperties p,ObjectMapper j){experiments=e;candidates=c;runs=r;runService=s;aggregate=a;properties=p;json=j;}
    @Scheduled(fixedDelayString="${quant.experiment.dispatcher-fixed-delay-ms:2000}")
    public void tick(){for(BacktestExperimentRow e:experiments.findNonTerminal()){refresh(e);int active=candidates.countActive(e.experimentId);while(active<properties.getMaxActiveCandidatesPerExperiment()){String token=UUID.randomUUID().toString();if(candidates.claimNextPending(e.experimentId,token,Instant.now())!=1)break;BacktestExperimentCandidateRow c=candidates.findClaimed(e.experimentId,token);if(c==null)break;try{BacktestCreateRequest train=request(e,c.parametersJson,e.trainingStartOpenTimeMs,e.trainingEndOpenTimeMs);runService.createWithRunId(c.trainingRunId,train);BacktestCreateRequest validation=request(e,c.parametersJson,e.validationStartOpenTimeMs,e.validationEndOpenTimeMs);runService.createWithRunId(c.validationRunId,validation);candidates.markDispatched(c.candidateId,token,Instant.now());active++;}catch(RuntimeException ex){candidates.markDispatchFailed(c.candidateId,token,"BACKTEST_EXPERIMENT_DISPATCH_FAILED",message(ex),Instant.now());}}refresh(e);}}
    private void refresh(BacktestExperimentRow e){aggregate.get(e.experimentId);}
    private BacktestCreateRequest request(BacktestExperimentRow e,String params,long start,long end){BacktestCreateRequest q=new BacktestCreateRequest();q.setDatasetId(e.datasetId);q.setStartOpenTimeInclusive(Instant.ofEpochMilli(start));q.setEndOpenTimeExclusive(Instant.ofEpochMilli(end));q.setStrategyCode(e.strategyCode);q.setStrategyVersion(e.strategyVersion);try{q.setStrategyParameters(json.readValue(params,new TypeReference<LinkedHashMap<String,Integer>>(){}));}catch(Exception x){throw new BacktestTaskException("BACKTEST_EXPERIMENT_DISPATCH_FAILED","candidate parameters are invalid");}q.setOrderAmount(e.orderAmount);q.setFeeRate(e.feeRate);q.setForceCloseAtEnd(true);return q;}
    private String message(RuntimeException e){String s=e.getMessage()==null?"candidate dispatch failed":e.getMessage().replaceAll("[\\r\\n]"," ");return s.substring(0,Math.min(1000,s.length()));}
}
