package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestExperimentSnapshotTest {
    @Test
    void oneExperimentReadsAtMost128RunIdsPerQuery() {
        BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
        BacktestRunMapper runs = mock(BacktestRunMapper.class);
        when(candidates.findAll("experiment")).thenReturn(rows(64, "experiment"));
        when(runs.findByRunIds(anyList())).thenReturn(List.of());

        BacktestExperimentSnapshot.load(row("experiment"), candidates, runs);

        verify(runs, times(1)).findByRunIds(anyList());
        verify(runs).findByRunIds(argThat(ids -> ids.size() == 128));
    }

    @Test
    void listSnapshotLoadsCandidatesOnceAndGroupsAllExperiments() {
        BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
        BacktestRunMapper runs = mock(BacktestRunMapper.class);
        List<BacktestExperimentRow> experiments = List.of(row("one"), row("two"));
        when(candidates.findAllByExperimentIds(List.of("one", "two"))).thenReturn(rows(2, "one"));
        when(runs.findByRunIds(anyList())).thenReturn(List.of());

        var snapshots = BacktestExperimentSnapshot.loadMany(experiments, candidates, runs);

        assertEquals(2, snapshots.size());
        assertEquals(2, snapshots.get("one").candidates().size());
        verify(candidates, times(1)).findAllByExperimentIds(List.of("one", "two"));
    }

    private static BacktestExperimentRow row(String id) {
        BacktestExperimentRow row = new BacktestExperimentRow();
        row.experimentId = id;
        return row;
    }

    private static List<BacktestExperimentCandidateRow> rows(int count, String experimentId) {
        List<BacktestExperimentCandidateRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BacktestExperimentCandidateRow row = new BacktestExperimentCandidateRow();
            row.experimentId = experimentId;
            row.trainingRunId = experimentId + "-train-" + i;
            row.validationRunId = experimentId + "-validation-" + i;
            rows.add(row);
        }
        return rows;
    }
}
