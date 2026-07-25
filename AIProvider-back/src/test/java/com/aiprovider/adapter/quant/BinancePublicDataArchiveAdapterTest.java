package com.aiprovider.adapter.quant;

import com.aiprovider.config.quant.QuantMarketHistoryProperties;
import com.aiprovider.quant.market.history.model.ArchiveImportMode;
import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.history.service.ArchiveDataException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BinancePublicDataArchiveAdapter} 单元测试。
 *
 * 使用 JDK 内置 {@link HttpServer} 搭建本地测试服务器，验证：
 * <ul>
 *   <li>正常 ZIP + 正确 CHECKSUM → 解析出 MarketCandle</li>
 *   <li>CHECKSUM 404 → ERR_ARCHIVE_CHECKSUM_MISSING</li>
 *   <li>CHECKSUM 哈希格式非法 → ERR_ARCHIVE_CHECKSUM_INVALID</li>
 *   <li>ZIP Content-Length 超限 → ERR_ARCHIVE_TOO_LARGE</li>
 *   <li>CSV 列数不为 12 → ERR_ARCHIVE_CSV_INVALID</li>
 *   <li>ZIP 包含多个 CSV 条目 → ERR_ARCHIVE_ZIP_INVALID</li>
 *   <li>ZIP 内无 CSV 条目 → ERR_ARCHIVE_ZIP_INVALID</li>
 * </ul>
 *
 * 不访问真实 Binance 服务器。
 */
class BinancePublicDataArchiveAdapterTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final KlineInterval INTERVAL = KlineInterval.M1;
    private static final String ZIP_FILE_NAME = "BTCUSDT-1m-2025-01.zip";
    private static final String CSV_ENTRY_NAME = "BTCUSDT-1m-2025-01.csv";
    private static final String RELATIVE_PATH =
            "data/futures/um/monthly/klines/BTCUSDT/1m/" + ZIP_FILE_NAME;

    @TempDir
    Path tempDir;

    private HttpServer server;

    // 12 列 CSV 表头 + 2 行数据
    private static final String VALID_CSV =
            "open_time,open,high,low,close,volume,close_time,quote_asset_volume," +
            "number_of_trades,taker_buy_base_asset_volume,taker_buy_quote_asset_volume,ignore\n" +
            "1000,1.0,2.0,0.5,1.5,100.0,2000,150.0,10,50.0,75.0,ignore\n" +
            "1600,1.5,2.5,1.0,2.0,200.0,2200,300.0,20,100.0,150.0,ignore\n";

    private static final String VALID_CSV_WITHOUT_HEADER =
            "1000,1.0,2.0,0.5,1.5,100.0,2000,150.0,10,50.0,75.0,ignore\n" +
            "1600,1.5,2.5,1.0,2.0,200.0,2200,300.0,20,100.0,150.0,ignore\n";

    // 11 列数据行（缺少最后一列 ignore）
    private static final String CSV_WRONG_COLUMNS =
            "open_time,open,high,low,close,volume,close_time,quote_asset_volume," +
            "number_of_trades,taker_buy_base_asset_volume,taker_buy_quote_asset_volume,ignore\n" +
            "1000,1.0,2.0,0.5,1.5,100.0,2000,150.0,10,50.0,75.0\n";

    @BeforeEach
    void setUp() {
        server = null;
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- 正常解析 ----

    @Test
    void validZipWithCorrectChecksumParsesCandles() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();
        adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).getOpenTime().toEpochMilli()).isEqualTo(1000L);
        assertThat(candles.get(1).getOpenTime().toEpochMilli()).isEqualTo(1600L);
    }

    @Test
    void validZipWithoutHeaderKeepsFirstDataRow() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV_WITHOUT_HEADER);
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;
        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) sendString(exchange, 200, checksum);
            else if (path.endsWith(".zip")) sendBytes(exchange, 200, zipBytes);
            else send404(exchange);
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();
        adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).getOpenTime().toEpochMilli()).isEqualTo(1000L);
    }

    // ---- CHECKSUM 缺失 ----

    @Test
    void checksumMissingThrowsArchiveChecksumMissing() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                send404(exchange);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_CHECKSUM_MISSING);
    }

    // ---- CHECKSUM 哈希格式非法 ----

    @Test
    void checksumInvalidFormatThrowsArchiveChecksumInvalid() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        String badChecksum = "not-a-valid-hash  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, badChecksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_CHECKSUM_INVALID);
    }

    // ---- CHECKSUM 哈希不匹配 ----

    @Test
    void checksumHashMismatchThrowsArchiveChecksumInvalid() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        // 64 位十六进制但与实际 ZIP 哈希不匹配
        String wrongHash = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa1111bbbb2222  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, wrongHash);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_CHECKSUM_INVALID);
    }

    // ---- ZIP 超过大小限制 ----

    @Test
    void zipTooLargeThrowsArchiveTooLarge() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                // Content-Length 声明远超限制
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(999_999_999L));
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } else {
                send404(exchange);
            }
        });

        // maxZipSizeBytes = 100 字节，远小于实际 ZIP
        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 100L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_TOO_LARGE);
    }

    // ---- CSV 列数不为 12 ----

    @Test
    void csvWrongColumnCountThrowsArchiveCsvInvalid() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, CSV_WRONG_COLUMNS);
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_CSV_INVALID);
    }

    // ---- ZIP 包含多个 CSV 条目 ----

    @Test
    void zipWithMultipleCsvEntriesThrowsArchiveZipInvalid() throws Exception {
        // 创建包含两个 CSV 条目的 ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(CSV_ENTRY_NAME));
            zos.write(VALID_CSV.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("extra.csv"));
            zos.write("1000,1.0,2.0,0.5,1.5,100.0,2000,150.0,10,50.0,75.0,ignore\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID);
    }

    // ---- ZIP 内无 CSV 条目 ----

    @Test
    void zipWithNoCsvEntriesThrowsArchiveZipInvalid() throws Exception {
        // 创建只含非 CSV 条目的 ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("not a csv".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_ZIP_INVALID);
    }

    // ---- ZIP 文件不存在 (404) ----

    @Test
    void zipNotFoundThrowsArchiveNotFound() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        String checksum = sha256Hex(zipBytes) + "  " + ZIP_FILE_NAME;

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                send404(exchange);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();

        assertThatThrownBy(() -> adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll))
                .isInstanceOf(ArchiveDataException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveDataException.ERR_ARCHIVE_NOT_FOUND);
    }

    // ---- CHECKSUM 文件名为空 → 校验通过（允许空文件名） ----

    @Test
    void checksumWithOnlyHashAndNoFileNameParsesSuccessfully() throws Exception {
        byte[] zipBytes = createZip(CSV_ENTRY_NAME, VALID_CSV);
        // 只有哈希，没有文件名
        String checksum = sha256Hex(zipBytes);

        server = startServer((path, exchange) -> {
            if (path.endsWith(".CHECKSUM")) {
                sendString(exchange, 200, checksum);
            } else if (path.endsWith(".zip")) {
                sendBytes(exchange, 200, zipBytes);
            } else {
                send404(exchange);
            }
        });

        BinancePublicDataArchiveAdapter adapter = createAdapter(server.getAddress().getPort(), 536_870_912L);
        List<MarketCandle> candles = new ArrayList<>();
        adapter.downloadAndParse(createTestFile(), SYMBOL, INTERVAL, candles::addAll);

        assertThat(candles).hasSize(2);
    }

    // ---- 辅助方法 ----

    private BinancePublicDataArchiveAdapter createAdapter(int port, long maxZipSizeBytes) {
        QuantMarketHistoryProperties properties = new QuantMarketHistoryProperties();
        properties.getArchive().setBaseUrl("http://localhost:" + port + "/");
        properties.getArchive().setWorkDir(tempDir.resolve("archive-work").toString());
        properties.getArchive().setConnectTimeoutMs(3000);
        properties.getArchive().setRequestTimeoutMs(5000);
        properties.getArchive().setMaxZipSizeBytes(maxZipSizeBytes);
        properties.getArchive().setParseBatchSize(1000);
        return new BinancePublicDataArchiveAdapter(properties);
    }

    private ArchiveKlineFile createTestFile() {
        return new ArchiveKlineFile(
                ArchiveImportMode.ARCHIVE_MONTHLY,
                RELATIVE_PATH,
                ZIP_FILE_NAME,
                ZIP_FILE_NAME + ".CHECKSUM",
                1735689600000L,
                1738368000000L);
    }

    private HttpServer startServer(RequestHandler handler) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/", exchange -> {
            try {
                handler.handle(exchange.getRequestURI().getPath(), exchange);
            } catch (IOException e) {
                exchange.close();
            }
        });
        s.start();
        return s;
    }

    private static void sendString(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void send404(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private static byte[] createZip(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    @FunctionalInterface
    private interface RequestHandler {
        void handle(String path, HttpExchange exchange) throws IOException;
    }
}
