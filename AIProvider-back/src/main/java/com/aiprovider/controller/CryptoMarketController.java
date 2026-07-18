package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.vo.CryptoMarketHealthVO;
import com.aiprovider.model.vo.CryptoMarketSymbolVO;
import com.aiprovider.service.CryptoMarketProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crypto-market")
public class CryptoMarketController {
    private final CryptoMarketProxyService service;

    public CryptoMarketController(CryptoMarketProxyService service) { this.service = service; }

    @GetMapping("/health")
    public Result<CryptoMarketHealthVO> health() { return Result.success(service.health()); }

    @GetMapping("/exchanges")
    public Result<JsonNode> exchanges() { return Result.success(service.exchanges()); }

    @GetMapping("/exchange-info")
    public Result<JsonNode> exchangeInfo() { return Result.success(service.exchanges()); }

    @GetMapping("/symbols")
    public Result<List<CryptoMarketSymbolVO>> symbols(@RequestParam String exchange,
                                                       @RequestParam(defaultValue = "USDT") String quote,
                                                       @RequestParam(defaultValue = "500") int limit) {
        return Result.success(service.symbols(exchange, quote, limit));
    }

    @GetMapping("/ticker")
    public Result<JsonNode> ticker(@RequestParam String exchange, @RequestParam String symbol) {
        return Result.success(service.ticker(exchange, symbol));
    }

    @GetMapping("/klines")
    public Result<JsonNode> klines(@RequestParam String exchange, @RequestParam String symbol, @RequestParam String interval,
                                   @RequestParam(defaultValue = "240") int limit) {
        return Result.success(service.klines(exchange, symbol, interval, limit));
    }

    @GetMapping("/depth")
    public Result<JsonNode> depth(@RequestParam String exchange, @RequestParam String symbol,
                                  @RequestParam(defaultValue = "20") int limit) {
        return Result.success(service.depth(exchange, symbol, limit));
    }
}
