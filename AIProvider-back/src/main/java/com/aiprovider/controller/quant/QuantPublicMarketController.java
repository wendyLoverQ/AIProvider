package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;
import com.aiprovider.quant.market.service.PublicMarketQueryService;
import com.aiprovider.quant.market.service.PublicMarketQueryService.ProviderInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quant 公共行情控制器。
 *
 * 对接前端量化市场行情页面，所有返回使用统一 {@link Result} 封装。
 * 参数校验在此层完成：provider 合法枚举、symbol 大写英数字且存在于合约目录、interval 合法枚举、limit 20-500。
 */
@RestController
@RequestMapping("/api/quant/market")
public class QuantPublicMarketController {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,32}$");

    private final PublicMarketQueryService queryService;

    public QuantPublicMarketController(PublicMarketQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/providers")
    public Result<List<ProviderInfo>> providers() {
        return Result.success(queryService.providers());
    }

    @GetMapping("/health")
    public Result<PublicMarketHealth> health(@RequestParam String provider) {
        MarketProviderId providerId = parseProvider(provider);
        return Result.success(queryService.health(providerId));
    }

    @GetMapping("/contracts")
    public Result<List<PerpetualContract>> contracts(@RequestParam String provider,
                                                      @RequestParam(defaultValue = "USDT") String quoteAsset) {
        MarketProviderId providerId = parseProvider(provider);
        return Result.success(queryService.contracts(providerId, quoteAsset));
    }

    @GetMapping("/snapshot")
    public Result<MarketSnapshot> snapshot(@RequestParam String provider,
                                           @RequestParam String symbol) {
        MarketProviderId providerId = parseProvider(provider);
        String sym = requireSymbol(symbol);
        requireSymbolExists(providerId, sym);
        return Result.success(queryService.snapshot(providerId, sym));
    }

    @GetMapping("/klines")
    public Result<List<MarketCandle>> klines(@RequestParam String provider,
                                             @RequestParam String symbol,
                                             @RequestParam String interval,
                                             @RequestParam(defaultValue = "120") int limit) {
        MarketProviderId providerId = parseProvider(provider);
        String sym = requireSymbol(symbol);
        requireSymbolExists(providerId, sym);
        KlineInterval intervalEnum = KlineInterval.fromCode(interval);
        if (limit < 20 || limit > 500) {
            throw new IllegalArgumentException("K 线数量必须在 20 到 500 之间");
        }
        return Result.success(queryService.klines(providerId, sym, intervalEnum, limit));
    }

    private MarketProviderId parseProvider(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("行情提供方不能为空");
        }
        try {
            return MarketProviderId.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的行情提供方: " + provider);
        }
    }

    private String requireSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("合约符号格式不正确，应为大写英数字");
        }
        return normalized;
    }

    private void requireSymbolExists(MarketProviderId providerId, String symbol) {
        List<PerpetualContract> contracts = queryService.contracts(providerId, "USDT");
        for (PerpetualContract contract : contracts) {
            if (symbol.equals(contract.getSymbol())) {
                return;
            }
        }
        throw new IllegalArgumentException("合约符号不存在: " + symbol);
    }
}
