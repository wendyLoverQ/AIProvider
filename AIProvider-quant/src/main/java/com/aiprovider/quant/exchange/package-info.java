/**
 * Quant 交易所适配模块。
 *
 * 职责：交易所适配边界与连接配置。
 *
 * 当前阶段：Binance USDⓈ-M Futures 公共行情适配已接通（{@code exchange.binance.usdm}），
 * 仅访问公共行情接口。私有交易 API 未接入，不连接 Binance 或其他交易所私有 API。
 */
package com.aiprovider.quant.exchange;
