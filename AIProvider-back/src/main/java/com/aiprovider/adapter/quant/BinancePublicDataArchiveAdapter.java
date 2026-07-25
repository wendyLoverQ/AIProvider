package com.aiprovider.adapter.quant;

import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.history.port.HistoricalArchiveProvider;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 路径规则来源：binance/binance-public-data (MIT License)
 *   data/futures/um/{daily|monthly}/klines/{SYMBOL}/{INTERVAL}/{filename}
 *
 * CSV 格式（无表头，12 列）：open_time, open, high, low, close, volume,
 * close_time, quote_asset_volume, number_of_trades,
 * taker_buy_base_asset_volume, taker_buy_quote_asset_volume, ignore。
 */
public class BinancePublicDataArchiveAdapter implements HistoricalArchiveProvider {

    private static final Logger log = LoggerFactory.getLogger(BinancePublicDataArchiveAdapter.class);

    /** Binance 公共数据下载基址。 */
    private static final String BASE_URL = "https://data.binance.vision/";

    /** CSV 解析批大小，每批回调一次 consumer。 */
    private static final int BATCH_SIZE = 1000;

    /** 单次 HTTP 请求超时（连接建立后），覆盖 CHECKSUM 与 ZIP 下载。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final HttpClient httpClient;

    public BinancePublicDataArchiveAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public void downloadAndParse(ArchiveKlineFile file, String symbol, KlineInterval interval,
                                 Consumer<List<MarketCandle>> consumer) {
        String zipUrl = BASE_URL + file.getRelativePath();
        String checksumUrl = zipUrl + ".CHECKSUM";

        log.info("开始下载 Binance 归档数据 symbol={} interval={} rangeStart={} rangeEndExclusive={} zipUrl={}",
                symbol, interval.code(), file.getRangeStart(), file.getRangeEndExclusive(), zipUrl);

        Path tempZip = null;
        try {
            String expectedSha256 = downloadChecksum(checksumUrl);

            tempZip = Files.createTempFile("binance-archive-", ".zip");
            downloadZip(zipUrl, tempZip);

            String actualSha256 = sha256(tempZip);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new RuntimeException("SHA-256 校验失败 zipUrl=" + zipUrl
                        + " expected=" + expectedSha256 + " actual=" + actualSha256);
            }
            log.info("SHA-256 校验通过 zipUrl={} sha256={}", zipUrl, actualSha256);

            long parsedRows = parseZip(tempZip, symbol, interval, consumer);
            log.info("归档解析完成 symbol={} interval={} parsedRows={} zipUrl={}",
                    symbol, interval.code(), parsedRows, zipUrl);
        } catch (IOException e) {
            throw new RuntimeException("下载或解析 Binance 归档失败 zipUrl=" + zipUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("下载或解析 Binance 归档被中断 zipUrl=" + zipUrl, e);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException e) {
                    log.warn("删除临时文件失败 path={}", tempZip, e);
                }
            }
        }
    }

    /**
     * 下载 CHECKSUM 文件并解析出 SHA-256 哈希。
     *
     * CHECKSUM 文件格式：{@code <sha256>  <filename>}，取第一个空白分隔字段。
     */
    private String downloadChecksum(String checksumUrl) throws IOException, InterruptedException {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(checksumUrl))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status != 200) {
            throw new RuntimeException("下载 CHECKSUM 失败 status=" + status + " url=" + checksumUrl);
        }
        String body = resp.body();
        if (body == null || body.isBlank()) {
            throw new RuntimeException("CHECKSUM 内容为空 url=" + checksumUrl);
        }
        String hash = body.trim().split("\\s+")[0];
        if (hash.length() != 64 || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new RuntimeException("CHECKSUM 哈希格式非法 hash=" + hash + " url=" + checksumUrl);
        }
        log.info("已下载 CHECKSUM url={} sha256={}", checksumUrl, hash);
        return hash;
    }

    /** 下载 ZIP 数据包到临时文件。 */
    private void downloadZip(String zipUrl, Path target) throws IOException, InterruptedException {
        HttpResponse<Path> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(zipUrl))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofFile(target));
        int status = resp.statusCode();
        if (status != 200) {
            throw new RuntimeException("下载 ZIP 失败 status=" + status + " url=" + zipUrl);
        }
        log.info("已下载 ZIP url={} status={} bytes={}", zipUrl, status, Files.size(target));
    }

    /** 计算文件 SHA-256，返回小写十六进制字符串。 */
    private String sha256(Path path) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
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

    /**
     * 安全解压并流式解析 ZIP 内的 CSV，每 {@value #BATCH_SIZE} 行转一批 {@link MarketCandle} 回调。
     *
     * @return 解析出的总行数
     */
    private long parseZip(Path zipPath, String symbol, KlineInterval interval,
                          Consumer<List<MarketCandle>> consumer) throws IOException {
        long totalRows = 0;
        boolean parsedAny = false;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName == null || entryName.isEmpty()) {
                    throw new RuntimeException("ZIP 条目名称为空 zipPath=" + zipPath);
                }
                // Zip Slip 防护：归档内条目不得为绝对路径或包含路径穿越
                Path normalized = Path.of(entryName).normalize();
                if (normalized.isAbsolute() || entryName.contains("..")) {
                    throw new RuntimeException("非法 ZIP 条目名称，疑似 Zip Slip 攻击 entry=" + entryName
                            + " zipPath=" + zipPath);
                }
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    log.debug("跳过非 CSV 条目 entry={} zipPath={}", entryName, zipPath);
                    zis.closeEntry();
                    continue;
                }

                List<MarketCandle> batch = new ArrayList<>(BATCH_SIZE);
                // NonClosingReader 防止 CSVParser 关闭底层共享的 ZipInputStream
                try (CSVParser parser = CSVFormat.DEFAULT.parse(
                        new NonClosingReader(new InputStreamReader(zis, StandardCharsets.UTF_8)))) {
                    for (CSVRecord record : parser) {
                        batch.add(toCandle(record, symbol, interval));
                        totalRows++;
                        if (batch.size() >= BATCH_SIZE) {
                            consumer.accept(batch);
                            batch = new ArrayList<>(BATCH_SIZE);
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    consumer.accept(batch);
                }
                parsedAny = true;
                zis.closeEntry();
            }
        }
        if (!parsedAny) {
            throw new RuntimeException("ZIP 内未找到可解析的 CSV 条目 zipPath=" + zipPath);
        }
        return totalRows;
    }

    /** 将一行 Binance K 线 CSV 转换为 {@link MarketCandle}。 */
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
        } catch (RuntimeException e) {
            throw new RuntimeException("CSV 行解析失败 symbol=" + symbol
                    + " interval=" + interval.code() + " record=" + record, e);
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
