package com.aiprovider.service;

import com.aiprovider.model.vo.CryptoMarketHealthVO;
import com.aiprovider.model.vo.CryptoMarketSymbolVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CryptoMarketProxyService {
    private static final Pattern EXCHANGE_PATTERN = Pattern.compile("^[a-z0-9]{2,32}$");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,20}/[A-Z0-9]{1,20}(:[A-Z0-9]{1,20})?$");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^[A-Z0-9]{2,12}$");
    private static final Set<String> INTERVALS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w", "1M"
    )));

    private final String baseUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public CryptoMarketProxyService(@Value("${crypto-market.ccxt-base-url:http://127.0.0.1:8890}") String baseUrl,
                                    @Value("${crypto-market.connect-timeout-ms:3000}") int connectTimeoutMs,
                                    @Value("${crypto-market.read-timeout-ms:15000}") int readTimeoutMs) {
        this(baseUrl, createRestTemplate(connectTimeoutMs, readTimeoutMs));
    }

    CryptoMarketProxyService(String baseUrl, RestTemplate restTemplate) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.restTemplate = restTemplate;
    }

    public CryptoMarketHealthVO health() {
        long started = System.nanoTime();
        JsonNode result = fetch("/health");
        return new CryptoMarketHealthVO(
            result.path("provider").asText("CCXT"),
            result.path("available").asBoolean(false),
            Math.max(0L, (System.nanoTime() - started) / 1_000_000L),
            time(result.path("checkedAt").asText()),
            result.path("version").asText("unknown"),
            result.path("exchangeCount").asInt(0)
        );
    }

    public JsonNode exchanges() { return fetch("/exchanges"); }

    public List<CryptoMarketSymbolVO> symbols(String exchange, String quote, int limit) {
        String exchangeId = requireExchange(exchange);
        String quoteAsset = requireQuote(quote);
        if (limit < 1 || limit > 2000) throw new IllegalArgumentException("市场数量必须在 1 到 2000 之间");
        JsonNode markets = fetch(uri("/markets").queryParam("exchange", exchangeId).queryParam("quote", quoteAsset)
            .queryParam("limit", limit).build().encode().toUri());
        if (!markets.isArray()) throw new CryptoMarketUpstreamException("CCXT 市场目录格式不正确", null);
        List<CryptoMarketSymbolVO> result = new ArrayList<>();
        for (JsonNode item : markets) {
            result.add(new CryptoMarketSymbolVO(exchangeId, item.path("symbol").asText(), item.path("baseAsset").asText(), item.path("quoteAsset").asText()));
        }
        return result;
    }

    public JsonNode ticker(String exchange, String symbol) {
        return fetch(marketUri("/ticker", exchange, symbol).build().encode().toUri());
    }

    public JsonNode klines(String exchange, String symbol, String interval, int limit) {
        if (!INTERVALS.contains(interval)) throw new IllegalArgumentException("不支持的 K 线周期");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("K 线数量必须在 1 到 1000 之间");
        return fetch(marketUri("/ohlcv", exchange, symbol).queryParam("timeframe", interval)
            .queryParam("limit", limit).build().encode().toUri());
    }

    public JsonNode depth(String exchange, String symbol, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("深度档位必须在 1 到 200 之间");
        return fetch(marketUri("/order-book", exchange, symbol).queryParam("limit", limit).build().encode().toUri());
    }

    private UriComponentsBuilder marketUri(String path, String exchange, String symbol) {
        return uri(path).queryParam("exchange", requireExchange(exchange)).queryParam("symbol", requireSymbol(symbol));
    }

    private JsonNode fetch(String path) { return fetch(uri(path).build().encode().toUri()); }

    private JsonNode fetch(URI uri) {
        try {
            JsonNode result = restTemplate.getForObject(uri, JsonNode.class);
            if (result == null) throw new CryptoMarketUpstreamException("CCXT 行情网关返回空响应", null);
            return result;
        } catch (CryptoMarketUpstreamException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CryptoMarketUpstreamException("CCXT 公共行情网关暂时不可用", exception);
        }
    }

    private UriComponentsBuilder uri(String path) { return UriComponentsBuilder.fromHttpUrl(baseUrl).path(path); }

    private static String requireExchange(String exchange) {
        String normalized = exchange == null ? "" : exchange.trim().toLowerCase(Locale.ROOT);
        if (!EXCHANGE_PATTERN.matcher(normalized).matches()) throw new IllegalArgumentException("交易所 ID 格式不正确");
        return normalized;
    }

    private static String requireSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) throw new IllegalArgumentException("交易对格式不正确，应使用 BTC/USDT 形式");
        return normalized;
    }

    private static String requireQuote(String quote) {
        String normalized = quote == null ? "" : quote.trim().toUpperCase(Locale.ROOT);
        if (!QUOTE_PATTERN.matcher(normalized).matches()) throw new IllegalArgumentException("计价币种格式不正确");
        return normalized;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(normalized); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("CCXT 网关地址格式不正确", exception); }
        String host = uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        boolean scheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!loopback || !scheme || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new IllegalArgumentException("CCXT 网关必须使用本机回环地址");
        }
        return normalized;
    }

    private static OffsetDateTime time(String value) {
        try { return value == null || value.trim().isEmpty() ? OffsetDateTime.now() : OffsetDateTime.parse(value); }
        catch (DateTimeParseException ignored) { return OffsetDateTime.now(); }
    }

    private static RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        if (connectTimeoutMs < 100 || connectTimeoutMs > 30000 || readTimeoutMs < 100 || readTimeoutMs > 30000) {
            throw new IllegalArgumentException("行情服务超时必须在 100 到 30000 毫秒之间");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
