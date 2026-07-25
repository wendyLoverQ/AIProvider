package com.aiprovider.repository.quant;

import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * {@link SyncUnitOfWork} 的 Spring 实现。
 *
 * 使用 {@link TransactionTemplate} 封装每批同步操作的事务边界。
 * 每批操作（读取已有数据 → 校验冲突 → 批量写入 → 更新进度）在同一个事务内完成，
 * 如果 action 抛出异常，事务自动回滚，已提交的批次不受影响。
 */
@Repository
public class SyncUnitOfWorkImpl implements SyncUnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public SyncUnitOfWorkImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
