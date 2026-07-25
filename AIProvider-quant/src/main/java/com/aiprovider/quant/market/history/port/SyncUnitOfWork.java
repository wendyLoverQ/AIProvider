package com.aiprovider.quant.market.history.port;

import java.util.function.Supplier;

/**
 * 同步单元工作端口。
 *
 * 封装每批事务边界。定义在 AIProvider-quant 领域层，
 * 由 AIProvider-back 使用 Spring {@code TransactionTemplate} 实现。
 *
 * 每批操作（读取已有数据 → 校验冲突 → 批量写入 → 更新进度）在同一个事务内完成。
 * 如果 action 抛出异常，事务回滚。
 *
 * @param <T> action 返回类型
 */
public interface SyncUnitOfWork {

    /**
     * 在单个事务内执行给定 action。
     *
     * @param action 要在事务内执行的操作
     * @return action 的返回值
     */
    <T> T execute(Supplier<T> action);
}
