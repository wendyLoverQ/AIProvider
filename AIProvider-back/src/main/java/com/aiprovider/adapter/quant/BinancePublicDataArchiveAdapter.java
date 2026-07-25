package com.aiprovider.adapter.quant;

import com.aiprovider.config.quant.QuantMarketHistoryProperties;
import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.history.port.HistoricalArchiveProvider;
import com.aiprovider.quant.market.history.service.ArchiveDataException;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Binance 官方公共数据归档适配器。
 *
 * 实现 {@link HistoricalArchiveProvider} 端口，从 {@code https://data.binance.vision/}
 * 下载官方 ZIP K 线数据包，校验 SHA-256 CHECKSUM，流式解析 CSV 并转换为 {@link MarketCandle}。
 *
 * <p>路径规则来源：binance/binance-public-data (MIT License)
 *   data/futures/um/{daily|monthly}/klines/{SYMBOL}/{INTERVAL}/{filename}</p>
 *
 * <p>CSV 格式（12 列）：open_time, open, high, low, close, volume,
 * close_time, quote_asset_volume, number_of_trades,
 * taker_buy_base_asset_volume, taker_buy_quote_asset_volume, ignore。</p>
 *
 * <p>所有失败抛出 {@link ArchiveDataException}，携带稳定错误码，不统一降级。</p>
 */
public class BinancePublicDataArchiveAdapter implements HistoricalArchiveProvider {

    private static final List<String> OFFICIAL_CSV_HEADER = List.of(
            "open_time", "open", "high", "low", "close", "volume", "close_time",
            "quote_asset_volume", "number_of_trades", "taker_buy_base_asset_volume",
            "taker_buy_quote_asset_volume", "ignore");

    private static final Logger log = LoggerFactory.getLogger(BinancePublicDataArchiveAdapter.class);

    private final String baseUrl;
    private final Path workDir;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final long maxZipSizeBytes;
    private final int parseBatchSize;
    private final HttpClient httpClient;

    public BinancePublicDataArchiveAdapter(QuantMarketHistoryProperties properties) {
        QuantMarketHistoryProperties.Archive cfg = properties.getArchive();
        this.baseUrl = cfg.getBaseUrl();
        this.workDir = Paths.get(cfg.getWorkDir());
        this.connectTimeout = Duration.ofMillis(cfg.getConnectTimeoutMs());
        this.requestTimeout = Duration.ofMillis(cfg.getRequestTimeoutMs());
        this.maxZipSizeBytes = cfg.getMaxZipSizeBytes();
        this.parseBatchSize = cfg.getParseBatchSize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            log.warn("operation=archive-adapter-init msg=工作目录创建失败 dir={} err={}", workDir, e.getMessage());
        }

        log.info("operation=archive-adapter-init baseUrl={} workDir={} connectTimeoutMs={} requestTimeoutMs={} maxZipSizeBytes={} parseBatchSize={}",
                baseUrl, workDir, cfg.getConnectTimeoutMs(), cfg.getRequestTimeoutMs(),
                maxZipSizeBytes, parseBatchSize);
    }

    @Override
    public void downloadAndParse(ArchiveKlineFile file, String symbol, KlineInterval interval,
                                 Consumer<List<MarketCandle>> consumer) {
        String zipUrl = baseUrl + file.getRelativePath();
        String checksumUrl = zipUrl + ".CHECKSUM";
        String expectedZipFileName = file.getZipFileName();

        log.info("operation=archive-download-start symbol={} interval={} rangeStart={} rangeEnd={} zipUrl={}",
                symbol, interval.code(), file.getRangeStart(), file.getRangeEndExclusive(), zipUrl);

        Path tempZip = null;
        Path partFile = null;
        try {
            // 1. 下载并校验 CHECKSUM
            String expectedSha256 = downloadChecksum(checksumUrl, expectedZipFileName);

            // 2. 下载 ZIP（带大小限制，.part 临时文件 + 安全重命名）
            partFile = workDir.resolve(expectedZipFileName + ".part");
            downloadZip(zipUrl, partFile);

            // 3. SHA-256 校验
            String actualSha256 = sha256(partFile);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CHECKSUM_INVALID,
                        "SHA-256 校验失败 zipUrl=" + zipUrl
                                + " expected=" + expectedSha256 + " actual=" + actualSha256);
            }
            log.info("operation=archive-checksum-ok zipUrl={} sha256={}", zipUrl, actualSha256);

            // 4. 安全重命名 .part → 最终文件名
            tempZip = workDir.resolve(expectedZipFileName);
            Files.move(partFile, tempZip, StandardCopyOption.REPLACE_EXISTING);
            partFile = null;

            // 5. 解析 ZIP
            long parsedRows = parseZip(tempZip, expectedZipFileName, symbol, interval, consumer);
            log.info("operation=archive-parse-complete symbol={} interval={} parsedRows={} zipUrl={}",
                    symbol, interval.code(), parsedRows, zipUrl);

        } catch (ArchiveDataException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_DOWNLOAD_FAILED,
                    "下载或解析 Binance 归档失败 zipUrl=" + zipUrl + " err=" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_INTERRUPTED,
                    "下载或解析 Binance 归档被中断 zipUrl=" + zipUrl, e);
        } catch (RuntimeException e) {
            if (e instanceof ArchiveDataException) {
                throw e;
            }
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_DOWNLOAD_FAILED,
                    "下载或解析 Binance 归档失败 zipUrl=" + zipUrl + " err=" + e.getMessage(), e);
        } finally {
            if (partFile != null) {
                deleteQuietly(partFile);
            }
            if (tempZip != null) {
                deleteQuietly(tempZip);
            }
        }
    }

    // ---- CHECKSUM ----

    /**
     * 下载 CHECKSUM 文件并解析出 SHA-256 哈希。
     *
     * 校验：文件存在（404 → ARCHIVE_CHECKSUM_MISSING）、内容非空、哈希 64 位十六进制、
     * 文件名与计划 ZIP 文件名匹配。
     */
    private String downloadChecksum(String checksumUrl, String expectedZipFileName)
            throws IOException, InterruptedException {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(checksumUrl))
                        .timeout(requestTimeout)
                        .GET()
                        .build(),
                BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status == 404) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CHECKSUM_MISSING,
                    "CHECKSUM 文件不存在 url=" + checksumUrl);
        }
        if (status != 200) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_DOWNLOAD_FAILED,
                    "下载 CHECKSUM 失败 status=" + status + " url=" + checksumUrl);
        }
        String body = resp.body();
        if (body == null || body.isBlank()) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CHECKSUM_MISSING,
                    "CHECKSUM 内容为空 url=" + checksumUrl);
        }

        // Binance CHECKSUM 格式：<sha256>  <filename>
        String trimmed = body.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String hash = parts[0];

        if (hash.length() != 64 || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CHECKSUM_INVALID,
                    "CHECKSUM 哈希格式非法 hash=" + hash + " url=" + checksumUrl);
        }

        // 校验 CHECKSUM 文件名与计划 ZIP 文件名匹配
        if (parts.length > 1) {
            String checksumFileName = parts[1].trim();
            if (!checksumFileName.isEmpty() && !checksumFileName.equals(expectedZipFileName)) {
                throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CHECKSUM_INVALID,
                        "CHECKSUM 文件名不匹配 expected=" + expectedZipFileName
                                + " actual=" + checksumFileName + " url=" + checksumUrl);
            }
        }

        log.info("operation=archive-checksum-downloaded url={} sha256={}", checksumUrl, hash);
        return hash;
    }

    // ---- ZIP 下载 ----

    /**
     * 下载 ZIP 到 .part 临时文件，带 Content-Length 和流式大小限制。
     */
    private void downloadZip(String zipUrl, Path target) throws IOException, InterruptedException {
        HttpResponse<InputStream> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(zipUrl))
                        .timeout(requestTimeout)
                        .GET()
                        .build(),
                BodyHandlers.ofInputStream());

        int status = resp.statusCode();
        if (status == 404) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_NOT_FOUND,
                    "ZIP 文件不存在 status=404 url=" + zipUrl);
        }
        if (status != 200) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_DOWNLOAD_FAILED,
                    "下载 ZIP 失败 status=" + status + " url=" + zipUrl);
        }

        // Content-Length 预检
        long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (contentLength > maxZipSizeBytes) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_TOO_LARGE,
                    "ZIP Content-Length 超过限制 contentLength=" + contentLength
                            + " max=" + maxZipSizeBytes + " url=" + zipUrl);
        }

        // 流式下载 + 累计大小限制
        long total = 0;
        try (InputStream body = resp.body();
             BufferedInputStream bis = new BufferedInputStream(body);
             java.io.OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = bis.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                if (total > maxZipSizeBytes) {
                    throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_TOO_LARGE,
                            "ZIP 流式下载超过限制 downloaded=" + total
                                    + " max=" + maxZipSizeBytes + " url=" + zipUrl);
                }
            }
        }

        log.info("operation=archive-zip-downloaded url={} status={} bytes={}", zipUrl, status, total);
    }

    // ---- SHA-256 ----

    private String sha256(Path path) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_DOWNLOAD_FAILED,
                    "SHA-256 算法不可用", e);
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    // ---- ZIP 解析 ----

    /**
     * 安全解压并流式解析 ZIP 内的 CSV。
     *
     * 校验：
     * <ul>
     *   <li>Zip Slip 防护（绝对路径、路径穿越）</li>
     *   <li>有且仅有一个 CSV 条目</li>
     *   <li>CSV 文件名与计划 ZIP 文件名一致（去掉 .zip 后加 .csv）</li>
     *   <li>每行恰好 12 列</li>
     *   <li>第一条可识别官方 header</li>
     * </ul>
     */
    private long parseZip(Path zipPath, String expectedZipFileName, String symbol, KlineInterval interval,
                          Consumer<List<MarketCandle>> consumer) throws IOException {
        long totalRows = 0;
        int csvEntryCount = 0;
        String expectedCsvName = expectedZipFileName.substring(0, expectedZipFileName.length() - 4) + ".csv";

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName == null || entryName.isEmpty()) {
                    throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID,
                            "ZIP 条目名称为空 zipPath=" + zipPath);
                }

                // Zip Slip 防护
                Path normalized = Path.of(entryName).normalize();
                if (normalized.isAbsolute() || entryName.contains("..")) {
                    throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID,
                            "非法 ZIP 条目名称，疑似 Zip Slip entry=" + entryName + " zipPath=" + zipPath);
                }

                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    log.debug("operation=archive-zip-skip-non-csv entry={} zipPath={}", entryName, zipPath);
                    zis.closeEntry();
                    continue;
                }

                csvEntryCount++;
                if (csvEntryCount > 1) {
                    throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID,
                            "ZIP 包含多个 CSV 条目 count=" + csvEntryCount + " zipPath=" + zipPath);
                }

                // CSV 文件名校验（支持带子目录的 entry name）
                String simpleEntryName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf('/') + 1)
                        : entryName;
                if (!simpleEntryName.equals(expectedCsvName)) {
                    throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID,
                            "CSV 文件名与计划不匹配 expected=" + expectedCsvName
                                    + " actual=" + simpleEntryName + " zipPath=" + zipPath);
                }

                List<MarketCandle> batch = new ArrayList<>(parseBatchSize);
                try (CSVParser parser = CSVFormat.DEFAULT.parse(
                        new NonClosingReader(new InputStreamReader(zis, StandardCharsets.UTF_8)))) {
                    boolean headerChecked = false;
                    for (CSVRecord record : parser) {
                        if (!headerChecked) {
                            headerChecked = true;
                            if (matchesOfficialHeader(record)) {
                                continue;
                            }
                            if (!isValidDataRecordShape(record)) {
                                throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                                        "CSV 首行既不是官方 header 也不是合法数据行 entry=" + entryName);
                            }
                        }

                        if (record.size() != 12) {
                            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                                    "CSV 列数不为 12 columns=" + record.size()
                                            + " record=" + record + " entry=" + entryName);
                        }

                        MarketCandle candle = toCandle(record, symbol, interval);
                        if (candle.getTradeCount() < 0) {
                            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                                    "number_of_trades 不能为负数 entry=" + entryName);
                        }
                        batch.add(candle);
                        totalRows++;
                        if (batch.size() >= parseBatchSize) {
                            consumer.accept(batch);
                            batch = new ArrayList<>(parseBatchSize);
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    consumer.accept(batch);
                }
                zis.closeEntry();
            }
        }

        if (csvEntryCount == 0) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID,
                    "ZIP 内未找到 CSV 条目 zipPath=" + zipPath);
        }
        if (totalRows == 0) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                    "CSV 为空或只有 header zipPath=" + zipPath);
        }

        return totalRows;
    }

    // ---- CSV 转换 ----

    private boolean matchesOfficialHeader(CSVRecord record) {
        if (record.size() != OFFICIAL_CSV_HEADER.size()) {
            return false;
        }
        for (int i = 0; i < OFFICIAL_CSV_HEADER.size(); i++) {
            if (!OFFICIAL_CSV_HEADER.get(i).equals(record.get(i).trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidDataRecordShape(CSVRecord record) {
        if (record.size() != OFFICIAL_CSV_HEADER.size()) {
            return false;
        }
        try {
            Long.parseLong(record.get(0).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private MarketCandle toCandle(CSVRecord record, String symbol, KlineInterval interval) {
        try {
            MarketCandle candle = new MarketCandle();
            candle.setProvider(MarketProviderId.BINANCE_USDM);
            candle.setMarketType(MarketType.USDM_PERPETUAL);
            candle.setSymbol(symbol);
            candle.setInterval(interval);
            candle.setOpenTime(Instant.ofEpochMilli(Long.parseLong(record.get(0))));
            candle.setOpen(new BigDecimal(record.get(1)));
            candle.setHigh(new BigDecimal(record.get(2)));
            candle.setLow(new BigDecimal(record.get(3)));
            candle.setClose(new BigDecimal(record.get(4)));
            candle.setVolume(new BigDecimal(record.get(5)));
            candle.setCloseTime(Instant.ofEpochMilli(Long.parseLong(record.get(6))));
            candle.setQuoteVolume(new BigDecimal(record.get(7)));
            candle.setTradeCount(Long.parseLong(record.get(8)));
            candle.setTakerBuyBaseVolume(new BigDecimal(record.get(9)));
            candle.setTakerBuyQuoteVolume(new BigDecimal(record.get(10)));
            candle.setClosed(true);
            return candle;
        } catch (NumberFormatException e) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                    "CSV 数值解析失败 symbol=" + symbol + " interval=" + interval.code()
                            + " record=" + record + " err=" + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ArchiveDataException(ArchiveDataException.ERR_ARCHIVE_CSV_INVALID,
                    "CSV 行解析失败 symbol=" + symbol + " interval=" + interval.code()
                            + " record=" + record + " err=" + e.getMessage(), e);
        }
    }

    // ---- 辅助 ----

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("operation=archive-cleanup-fail path={} err={}", path, e.getMessage());
        }
    }

    /**
     * 不关闭底层流的 Reader 包装。CSVParser 在 close 时会关闭传入的 Reader，
     * 此包装避免其关闭共享的 ZipInputStream，从而支持 ZIP 内多条目流式解析。
     */
    private static final class NonClosingReader extends FilterReader {
        NonClosingReader(Reader in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            // 刻意不关闭底层流，由外层 ZipInputStream 统一管理
        }
    }
}
