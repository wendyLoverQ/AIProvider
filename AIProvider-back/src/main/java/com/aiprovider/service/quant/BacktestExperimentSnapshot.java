package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import com.aiprovider.mapper.row.BacktestRunRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** One consistent in-memory view of candidates and their two child runs. */
public record BacktestExperimentSnapshot(
        List<BacktestExperimentCandidateRow> candidates,
        Map<String, BacktestRunRow> runsById) {

    private static final int MAX_RUN_IDS_PER_QUERY = 128;

    public static BacktestExperimentSnapshot load(
            BacktestExperimentRow experiment,
            BacktestExperimentCandidateMapper candidates,
            BacktestRunMapper runs) {
        return load(candidates.findAll(experiment.experimentId), runs);
    }

    public static BacktestExperimentSnapshot load(
            List<BacktestExperimentCandidateRow> candidateRows,
            BacktestRunMapper runs) {
        List<String> runIds = new ArrayList<>(candidateRows.size() * 2);
        for (BacktestExperimentCandidateRow candidate : candidateRows) {
            runIds.add(candidate.trainingRunId);
            runIds.add(candidate.validationRunId);
        }

        Map<String, BacktestRunRow> runRows = new HashMap<>();
        if (runIds.isEmpty()) {
            return new BacktestExperimentSnapshot(List.copyOf(candidateRows), Map.of());
        }
        for (int from = 0; from < runIds.size(); from += MAX_RUN_IDS_PER_QUERY) {
            int to = Math.min(from + MAX_RUN_IDS_PER_QUERY, runIds.size());
            for (BacktestRunRow run : runs.findByRunIds(runIds.subList(from, to))) {
                runRows.put(run.runId, run);
            }
        }
        return new BacktestExperimentSnapshot(List.copyOf(candidateRows), Map.copyOf(runRows));
    }

    public static Map<String, BacktestExperimentSnapshot> loadMany(
            List<BacktestExperimentRow> experiments,
            BacktestExperimentCandidateMapper candidates,
            BacktestRunMapper runs) {
        if (experiments.isEmpty()) {
            return Map.of();
        }
        List<String> experimentIds = experiments.stream().map(e -> e.experimentId).toList();
        Map<String, List<BacktestExperimentCandidateRow>> grouped = new LinkedHashMap<>();
        for (String experimentId : experimentIds) {
            grouped.put(experimentId, new ArrayList<>());
        }
        for (BacktestExperimentCandidateRow candidate : candidates.findAllByExperimentIds(experimentIds)) {
            grouped.computeIfAbsent(candidate.experimentId, ignored -> new ArrayList<>()).add(candidate);
        }
        List<BacktestExperimentCandidateRow> allCandidates = grouped.values().stream().flatMap(List::stream).toList();
        Map<String, BacktestExperimentSnapshot> runSnapshot = new LinkedHashMap<>();
        BacktestExperimentSnapshot all = load(allCandidates, runs);
        for (String experimentId : experimentIds) {
            List<BacktestExperimentCandidateRow> rows = grouped.getOrDefault(experimentId, List.of());
            Map<String, BacktestRunRow> runRows = new HashMap<>();
            for (BacktestExperimentCandidateRow row : rows) {
                if (all.runsById().containsKey(row.trainingRunId)) runRows.put(row.trainingRunId, all.runsById().get(row.trainingRunId));
                if (all.runsById().containsKey(row.validationRunId)) runRows.put(row.validationRunId, all.runsById().get(row.validationRunId));
            }
            runSnapshot.put(experimentId, new BacktestExperimentSnapshot(List.copyOf(rows), Map.copyOf(runRows)));
        }
        return Map.copyOf(runSnapshot);
    }

    public BacktestRunRow run(String runId) {
        return runsById.get(runId);
    }
}
