package com.aiprovider.quant.market.history.model;

/**
 * 单个待下载的 Binance 官方历史数据包描述。
 *
 * 一个实例对应规划器计算出的一个 ZIP 数据包，包含其在官方仓库中的相对路径、
 * 校验文件名以及覆盖的 K 线开时间范围。
 *
 * 路径规则来源：binance/binance-public-data (MIT License)
 *   data/futures/um/{monthly|daily}/klines/{SYMBOL}/{INTERVAL}/{filename}
 * 例：
 *   data/futures/um/monthly/klines/BTCUSDT/15m/BTCUSDT-15m-2025-01.zip
 *   data/futures/um/daily/klines/BTCUSDT/15m/BTCUSDT-15m-2025-01-15.zip
 */
public class ArchiveKlineFile {

    private ArchiveImportMode sourceMode;
    private String relativePath;
    private String zipFileName;
    private String checksumFileName;
    private long rangeStart;
    private long rangeEndExclusive;

    /**
     * 构造一个归档文件描述。
     *
     * @param sourceMode        该文件所属的导入来源模式
     * @param relativePath      基址之后的相对 URL 路径，
     *                          例如 "data/futures/um/monthly/klines/BTCUSDT/15m/BTCUSDT-15m-2025-01.zip"
     * @param zipFileName       仅 ZIP 文件名，例如 "BTCUSDT-15m-2025-01.zip"
     * @param checksumFileName  校验文件名，例如 "BTCUSDT-15m-2025-01.zip.CHECKSUM"
     * @param rangeStart        该文件覆盖的第一根 K 线 openTime，epoch 毫秒
     * @param rangeEndExclusive 该文件不包含的第一根 K 线 openTime，epoch 毫秒（独占结束）
     */
    public ArchiveKlineFile(ArchiveImportMode sourceMode, String relativePath, String zipFileName,
                            String checksumFileName, long rangeStart, long rangeEndExclusive) {
        this.sourceMode = sourceMode;
        this.relativePath = relativePath;
        this.zipFileName = zipFileName;
        this.checksumFileName = checksumFileName;
        this.rangeStart = rangeStart;
        this.rangeEndExclusive = rangeEndExclusive;
    }

    public ArchiveImportMode getSourceMode() { return sourceMode; }
    public void setSourceMode(ArchiveImportMode sourceMode) { this.sourceMode = sourceMode; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getZipFileName() { return zipFileName; }
    public void setZipFileName(String zipFileName) { this.zipFileName = zipFileName; }

    public String getChecksumFileName() { return checksumFileName; }
    public void setChecksumFileName(String checksumFileName) { this.checksumFileName = checksumFileName; }

    public long getRangeStart() { return rangeStart; }
    public void setRangeStart(long rangeStart) { this.rangeStart = rangeStart; }

    public long getRangeEndExclusive() { return rangeEndExclusive; }
    public void setRangeEndExclusive(long rangeEndExclusive) { this.rangeEndExclusive = rangeEndExclusive; }
}
