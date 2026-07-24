package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.quant.management.QuantOverviewService;
import com.aiprovider.quant.model.vo.QuantOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quant 管理入口（Web 应用层）。
 *
 * 当前只提供骨架总览接口，返回真实骨架状态，不暴露任何交易、下单、
 * 仓位或行情订阅能力。依赖方向为 AIProvider-back → AIProvider-quant，
 * 本控制器注入并调用 quant 模块中的 {@link QuantOverviewService}。
 */
@RestController
@RequestMapping("/api/quant")
public class QuantOverviewController {

    private final QuantOverviewService service;

    public QuantOverviewController(QuantOverviewService service) {
        this.service = service;
    }

    /**
     * 返回 Quant 模块当前真实骨架状态。
     *
     * 当前阶段固定为 FOUNDATION：基础骨架已建立，实盘交易未启用，
     * 交易所未配置，数据存储未创建。
     */
    @GetMapping("/overview")
    public Result<QuantOverviewVO> overview() {
        return Result.success(service.overview());
    }
}
