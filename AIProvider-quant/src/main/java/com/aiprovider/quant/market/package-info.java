/**
 * Quant 行情模块。
 *
 * 职责：行情事件来源与行情数据接入边界。
 *
 * 当前阶段：Binance USDⓈ-M Futures 公共行情已接通，通过 {@code market.port} 端口与
 * {@code market.service} 服务层提供统一公共行情查询。不订阅 WebSocket，不读取交易所私有 API。
 */
package com.aiprovider.quant.market;
