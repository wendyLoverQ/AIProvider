/**
 * Quant 根包。
 *
 * Quant 模块以模块化单体方式内嵌于 AIProvider 同一个 Spring Boot 进程，
 * 当前阶段只建立基础骨架，不实现任何具体量化业务，不接入数据库、交易所私有 API
 * 或外部行情订阅。
 *
 * 各职责子包内通过 package-info.java 描述职责与当前状态。
 * 对外受限管理入口由 {@code management} 子包提供。
 */
package com.aiprovider.quant;
