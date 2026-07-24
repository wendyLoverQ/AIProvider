package com.aiprovider.quant.management;

import com.aiprovider.quant.model.vo.QuantModuleVO;
import com.aiprovider.quant.model.vo.QuantOverviewVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Quant 总览服务。
 *
 * 只返回当前真实骨架状态，不访问数据库，不调用任何外部服务，
 * 不启动线程、定时任务或 WebSocket，不读取密钥和环境变量。
 *
 * 当前阶段固定为 FOUNDATION：基础骨架已建立，实盘交易未启用，
 * 交易所未配置，数据存储未创建。所有模块状态为 SKELETON。
 */
@Service
public class QuantOverviewService {

    private static final String PHASE = "FOUNDATION";
    private static final String STATUS_SKELETON = "SKELETON";
    private static final String EXCHANGE_STATE = "NOT_CONFIGURED";
    private static final String STORAGE_STATE = "NOT_CREATED";

    /** 研究链路模块分组。 */
    private static final String GROUP_RESEARCH = "research";
    /** 交易链路模块分组。 */
    private static final String GROUP_TRADING = "trading";
    /** 运行管理模块分组。 */
    private static final String GROUP_OPERATIONS = "operations";

    public QuantOverviewVO overview() {
        QuantOverviewVO vo = new QuantOverviewVO();
        vo.setPhase(PHASE);
        vo.setLiveTradingEnabled(false);
        vo.setExchangeState(EXCHANGE_STATE);
        vo.setStorageState(STORAGE_STATE);
        vo.setModules(buildModules());
        return vo;
    }

    private List<QuantModuleVO> buildModules() {
        return Collections.unmodifiableList(Arrays.asList(
                new QuantModuleVO("market", "行情", "行情事件与行情来源", GROUP_RESEARCH, STATUS_SKELETON),
                new QuantModuleVO("indicator", "指标", "通用指标计算", GROUP_RESEARCH, STATUS_SKELETON),
                new QuantModuleVO("strategy", "策略", "策略定义与信号生成", GROUP_RESEARCH, STATUS_SKELETON),
                new QuantModuleVO("backtest", "回测", "历史回放、模拟执行和统计", GROUP_RESEARCH, STATUS_SKELETON),
                new QuantModuleVO("risk", "风控", "交易前、交易中和账户级风控", GROUP_TRADING, STATUS_SKELETON),
                new QuantModuleVO("execution", "订单执行", "订单意图与执行状态", GROUP_TRADING, STATUS_SKELETON),
                new QuantModuleVO("portfolio", "账户与仓位", "余额、仓位、盈亏和资金占用", GROUP_TRADING, STATUS_SKELETON),
                new QuantModuleVO("exchange", "交易所适配", "交易所适配边界", GROUP_TRADING, STATUS_SKELETON),
                new QuantModuleVO("reconciliation", "对账恢复", "订单、成交和仓位对账恢复", GROUP_TRADING, STATUS_SKELETON),
                new QuantModuleVO("monitoring", "监控告警", "Quant 运行状态和告警边界", GROUP_OPERATIONS, STATUS_SKELETON),
                new QuantModuleVO("management", "管理接口", "提供给前端的受限管理入口", GROUP_OPERATIONS, STATUS_SKELETON)
        ));
    }
}
