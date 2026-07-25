/**
 * Binance USDⓈ-M Futures（U 本位合约）公共行情适配。
 *
 * 使用 Java 17 java.net.http.HttpClient 直连 Binance fapi 公共端点，不使用第三方 SDK、不使用 CCXT。
 * 仅访问公共行情接口，不读取私有账户 API，不订阅 WebSocket。
 *
 * 当前阶段：公共行情已接通，私有交易未启用。
 */
package com.aiprovider.quant.exchange.binance.usdm;
