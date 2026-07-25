package com.aiprovider.quant.market.history.model;

import java.util.List;

/**
 * 一次同步任务的完整归档导入计划。
 *
 * 由规划器根据目标时间范围、已有数据覆盖和缺口计算得出，描述需要下载哪些官方数据包、
 * 整体使用哪种模式以及是否需要 REST 尾部修补。
 *
 * 路径规则来源：binance/binance-public-data (MIT License)
 */
public class ArchiveImportPlan {

    private List<ArchiveKlineFile> files;
    private ArchiveImportMode mode;
    private long totalRangeStart;
    private long totalRangeEndExclusive;
    private int monthlyFileCount;
    private int dailyFileCount;
    private boolean hasRestTail;
    private Long restTailStartInclusive;
    private Long restTailEndExclusive;

    /**
     * 构造一个归档导入计划。
     *
     * @param files              待下载文件的有序列表
     * @param mode               整体导入模式
     * @param totalRangeStart    计划覆盖的起始 openTime，epoch 毫秒
     * @param totalRangeEndExclusive 计划覆盖的独占结束 openTime，epoch 毫秒
     * @param monthlyFileCount   月包数量
     * @param dailyFileCount     日包数量
     * @param hasRestTail        是否需要 REST 尾部修补
     * @param restTailStartInclusive REST 尾部起始时间（包含），null 表示无 REST 尾部
     * @param restTailEndExclusive   REST 尾部结束时间（不包含），null 表示无 REST 尾部
     */
    public ArchiveImportPlan(List<ArchiveKlineFile> files, ArchiveImportMode mode, long totalRangeStart,
                             long totalRangeEndExclusive, int monthlyFileCount, int dailyFileCount,
                             boolean hasRestTail,
                             Long restTailStartInclusive, Long restTailEndExclusive) {
        this.files = files;
        this.mode = mode;
        this.totalRangeStart = totalRangeStart;
        this.totalRangeEndExclusive = totalRangeEndExclusive;
        this.monthlyFileCount = monthlyFileCount;
        this.dailyFileCount = dailyFileCount;
        this.hasRestTail = hasRestTail;
        this.restTailStartInclusive = restTailStartInclusive;
        this.restTailEndExclusive = restTailEndExclusive;
    }

    public List<ArchiveKlineFile> getFiles() { return files; }
    public void setFiles(List<ArchiveKlineFile> files) { this.files = files; }

    public ArchiveImportMode getMode() { return mode; }
    public void setMode(ArchiveImportMode mode) { this.mode = mode; }

    public long getTotalRangeStart() { return totalRangeStart; }
    public void setTotalRangeStart(long totalRangeStart) { this.totalRangeStart = totalRangeStart; }

    public long getTotalRangeEndExclusive() { return totalRangeEndExclusive; }
    public void setTotalRangeEndExclusive(long totalRangeEndExclusive) { this.totalRangeEndExclusive = totalRangeEndExclusive; }

    public int getMonthlyFileCount() { return monthlyFileCount; }
    public void setMonthlyFileCount(int monthlyFileCount) { this.monthlyFileCount = monthlyFileCount; }

    public int getDailyFileCount() { return dailyFileCount; }
    public void setDailyFileCount(int dailyFileCount) { this.dailyFileCount = dailyFileCount; }

    public boolean isHasRestTail() { return hasRestTail; }
    public void setHasRestTail(boolean hasRestTail) { this.hasRestTail = hasRestTail; }

    public Long getRestTailStartInclusive() { return restTailStartInclusive; }
    public void setRestTailStartInclusive(Long restTailStartInclusive) { this.restTailStartInclusive = restTailStartInclusive; }

    public Long getRestTailEndExclusive() { return restTailEndExclusive; }
    public void setRestTailEndExclusive(Long restTailEndExclusive) { this.restTailEndExclusive = restTailEndExclusive; }

    /**
     * 返回待下载文件总数。
     *
     * @return files 列表大小
     */
    public int totalFileCount() {
        return files.size();
    }
}
