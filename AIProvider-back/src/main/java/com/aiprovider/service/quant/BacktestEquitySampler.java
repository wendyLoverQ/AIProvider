package com.aiprovider.service.quant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Deterministic index selection; the database query only returns these points. */
public final class BacktestEquitySampler {
    private BacktestEquitySampler() {}
    public static List<Integer> indices(int totalPoints, int maxPoints) {
        if (totalPoints <= 0 || maxPoints <= 0) return List.of();
        if (totalPoints <= maxPoints) { List<Integer> all=new ArrayList<>(); for(int i=0;i<totalPoints;i++) all.add(i); return all; }
        LinkedHashSet<Integer> selected=new LinkedHashSet<>();
        for(int i=0;i<maxPoints;i++) selected.add((int)Math.floor((double)i*(totalPoints-1)/(maxPoints-1)));
        return new ArrayList<>(selected);
    }
}
