package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.mapper.BacktestEquityMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.BacktestTradeMapper;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.service.MarketDataSnapshotService;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BacktestRunRetryMySqlIT {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("aiprovider_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void firstQueueRejectionIsPersistedAndFixedRunIsRetriedExactlyOnce() throws Exception {
    try (TestContext context = openContext()) {
      RecordingExecutor executor = new RecordingExecutor(true);
      BacktestRunService service = service(context.runs(), executor);
      String runId = UUID.randomUUID().toString();
      BacktestCreateRequest request = request();

      BacktestTaskException first =
          assertThrows(BacktestTaskException.class, () -> service.createWithRunId(runId, request));
      assertEquals("BACKTEST_QUEUE_FULL", first.getErrorCode());
      BacktestRunRow failed = context.runs().findByRunId(runId);
      assertEquals("FAILED", failed.status);
      assertEquals("BACKTEST_QUEUE_FULL", failed.errorCode);
      assertEquals(1, context.count(runId));

      service.createWithRunId(runId, request);
      assertTrue(executor.accepted.await(5, TimeUnit.SECONDS));
      BacktestRunRow queued = context.runs().findByRunId(runId);
      assertEquals("QUEUED", queued.status);
      assertEquals(null, queued.errorCode);
      assertEquals(null, queued.errorMessage);
      assertEquals(null, queued.finishedAt);
      assertEquals(2, executor.attempts.get());
      assertEquals(1, executor.acceptedCount.get() - 1);
      assertEquals(1, context.count(runId));
      executor.stop();
    }
  }

  @Test
  void differentFailureCodeIsNeverRetried() {
    try (TestContext context = openContext()) {
      RecordingExecutor executor = new RecordingExecutor(false);
      BacktestRunService service = service(context.runs(), executor);
      String runId = UUID.randomUUID().toString();
      context.insert(runId, request());
      assertEquals(
          1,
          context
              .runs()
              .fail(runId, "BACKTEST_PARAMETER_INVALID", "invalid parameters", Instant.now()));

      service.createWithRunId(runId, request());

      BacktestRunRow failed = context.runs().findByRunId(runId);
      assertEquals("FAILED", failed.status);
      assertEquals("BACKTEST_PARAMETER_INVALID", failed.errorCode);
      assertEquals(0, executor.attempts.get());
      assertEquals(1, context.count(runId));
      executor.stop();
    }
  }

  @Test
  void concurrentRetryHasOneCasWinnerAndOneEnqueue() throws Exception {
    try (TestContext context = openContext()) {
      RecordingExecutor executor = new RecordingExecutor(false);
      BacktestRunService first = service(context.runs(), executor);
      BacktestRunService second = service(context.runs(), executor);
      String runId = UUID.randomUUID().toString();
      context.insert(runId, request());
      assertEquals(
          1, context.runs().fail(runId, "BACKTEST_QUEUE_FULL", "queue is full", Instant.now()));

      CountDownLatch start = new CountDownLatch(1);
      ExecutorService callers = Executors.newFixedThreadPool(2);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      try {
        var one =
            callers.submit(
                () -> {
                  await(start);
                  try {
                    first.createWithRunId(runId, request());
                  } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                  }
                });
        var two =
            callers.submit(
                () -> {
                  await(start);
                  try {
                    second.createWithRunId(runId, request());
                  } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                  }
                });
        start.countDown();
        one.get(10, TimeUnit.SECONDS);
        two.get(10, TimeUnit.SECONDS);
      } finally {
        callers.shutdownNow();
      }

      assertEquals(null, failure.get());
      assertTrue(executor.accepted.await(5, TimeUnit.SECONDS));
      assertEquals(1, executor.acceptedCount.get());
      assertEquals("QUEUED", context.runs().findByRunId(runId).status);
      assertEquals(1, context.count(runId));
      executor.stop();
    }
  }

  @Test
  void secondQueueRejectionReturnsToFailedForTheNextRetry() throws Exception {
    try (TestContext context = openContext()) {
      RecordingExecutor executor = new RecordingExecutor(true);
      BacktestRunService service = service(context.runs(), executor);
      String runId = UUID.randomUUID().toString();
      context.insert(runId, request());
      assertEquals(
          1, context.runs().fail(runId, "BACKTEST_QUEUE_FULL", "queue is full", Instant.now()));

      assertThrows(BacktestTaskException.class, () -> service.createWithRunId(runId, request()));
      BacktestRunRow failedAgain = context.runs().findByRunId(runId);
      assertEquals("FAILED", failedAgain.status);
      assertEquals("BACKTEST_QUEUE_FULL", failedAgain.errorCode);

      service.createWithRunId(runId, request());
      assertTrue(executor.accepted.await(5, TimeUnit.SECONDS));
      assertEquals("QUEUED", context.runs().findByRunId(runId).status);
      assertEquals(1, context.count(runId));
      executor.stop();
    }
  }

  private static BacktestRunService service(BacktestRunMapper runs, RecordingExecutor executor) {
    MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
    org.mockito.Mockito.when(datasets.findById(1)).thenReturn(dataset());
    return new BacktestRunService(
        runs,
        mock(BacktestTradeMapper.class),
        mock(BacktestEquityMapper.class),
        datasets,
        mock(MarketDataSnapshotService.class),
        new BacktestEngine(new StrategyRegistry()),
        new StrategyRegistry(),
        mock(BacktestPersistenceService.class),
        new BacktestFailureService(runs),
        executor,
        new ObjectMapper());
  }

  private static BacktestCreateRequest request() {
    BacktestCreateRequest request = new BacktestCreateRequest();
    request.setDatasetId(1);
    request.setStartOpenTimeInclusive(Instant.EPOCH);
    request.setEndOpenTimeExclusive(Instant.ofEpochMilli(60_000));
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setStrategyParameters(Map.of("fastPeriod", 5, "slowPeriod", 20));
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(new BigDecimal("0.001"));
    request.setForceCloseAtEnd(true);
    return request;
  }

  private static MarketDataset dataset() {
    MarketDataset dataset = new MarketDataset();
    dataset.setId(1);
    dataset.setProvider(MarketProviderId.BINANCE_USDM);
    dataset.setMarketType(MarketType.USDM_PERPETUAL);
    dataset.setDataType(MarketDataType.CANDLE);
    dataset.setSymbol("BTCUSDT");
    dataset.setInterval(KlineInterval.M1);
    return dataset;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static TestContext openContext() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    AnnotationConfigApplicationContext application = new AnnotationConfigApplicationContext();
    RunMapperConfiguration.DATA_SOURCE = dataSource;
    application.register(RunMapperConfiguration.class);
    application.refresh();
    return new TestContext(application, application.getBean(BacktestRunMapper.class), dataSource);
  }

  private static final class TestContext implements AutoCloseable {
    private final AnnotationConfigApplicationContext application;
    private final BacktestRunMapper runs;
    private final JdbcTemplate jdbc;

    private TestContext(
        AnnotationConfigApplicationContext application,
        BacktestRunMapper runs,
        DataSource dataSource) {
      this.application = application;
      this.runs = runs;
      this.jdbc = new JdbcTemplate(dataSource);
    }

    private BacktestRunMapper runs() {
      return runs;
    }

    private int count(String runId) {
      return jdbc.queryForObject(
          "SELECT COUNT(*) FROM q_backtest_run WHERE RunId=?", Integer.class, runId);
    }

    private void insert(String runId, BacktestCreateRequest request) {
      BacktestRunRow row = new BacktestRunRow();
      row.runId = runId;
      row.datasetId = request.getDatasetId();
      row.provider = "BINANCE_USDM";
      row.marketType = "USDM_PERPETUAL";
      row.dataType = "CANDLE";
      row.symbol = "BTCUSDT";
      row.intervalCode = "1m";
      row.startOpenTimeMs = request.getStartOpenTimeInclusive().toEpochMilli();
      row.endOpenTimeExclusiveMs = request.getEndOpenTimeExclusive().toEpochMilli();
      row.strategyCode = request.getStrategyCode();
      row.strategyVersion = request.getStrategyVersion();
      row.requestedParametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
      row.orderAmount = request.getOrderAmount();
      row.feeRate = request.getFeeRate();
      row.forceCloseAtEnd = request.isForceCloseAtEnd();
      row.queuedAt = Instant.now();
      row.updatedAt = row.queuedAt;
      assertEquals(1, runs.insert(row));
    }

    @Override
    public void close() {
      application.close();
    }
  }

  @Configuration
  @MapperScan(basePackageClasses = BacktestRunMapper.class)
  static class RunMapperConfiguration {
    private static DataSource DATA_SOURCE;

    @Bean
    DataSource dataSource() {
      return DATA_SOURCE;
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
      SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
      factory.setDataSource(dataSource);
      factory.afterPropertiesSet();
      return factory.getObject();
    }
  }

  private static final class RecordingExecutor extends ThreadPoolExecutor {
    private final AtomicBoolean rejectNext;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger acceptedCount = new AtomicInteger();
    private final CountDownLatch accepted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private RecordingExecutor(boolean rejectFirst) {
      super(0, 1, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
      rejectNext = new AtomicBoolean(rejectFirst);
    }

    private void stop() {
      release.countDown();
      shutdownNow();
      try {
        awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void execute(Runnable command) {
      attempts.incrementAndGet();
      if (rejectNext.compareAndSet(true, false)) {
        throw new RejectedExecutionException("queue is full");
      }
      super.execute(
          () -> {
            acceptedCount.incrementAndGet();
            accepted.countDown();
            await(release);
            command.run();
          });
    }
  }
}
