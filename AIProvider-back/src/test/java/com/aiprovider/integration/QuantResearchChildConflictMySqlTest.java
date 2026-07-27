package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.config.quant.QuantResearchProperties;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.service.quant.ResearchStudyAggregationScheduler;
import com.aiprovider.service.quant.ResearchStudyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
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
class QuantResearchChildConflictMySqlTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
      .withDatabaseName("aiprovider_research_child_conflict_test").withUsername("test").withPassword("test");

  @Test void marksMissingChildFailedAndKeepsResearchReadable() {
    try (AnnotationConfigApplicationContext app = open()) {
      JdbcTemplate jdbc = new JdbcTemplate((DataSource) app.getBean("dataSource"));
      clear(jdbc);
      String id = UUID.randomUUID().toString();
      new QuantResearchMySqlFixture(jdbc).insertResearchStudySnapshot(id, UUID.randomUUID().toString(), "QUEUED", "c".repeat(64), null, null, Instant.EPOCH, null, null);
      ResearchStudyMapper research = app.getBean(ResearchStudyMapper.class);
      ResearchStudyAggregationScheduler scheduler = new ResearchStudyAggregationScheduler(research, app.getBean(WalkForwardStudyMapper.class), new QuantResearchProperties());
      scheduler.tick();
      assertEquals("FAILED", jdbc.queryForObject("SELECT Status FROM q_research_study WHERE ResearchStudyId=?", String.class, id));
      assertEquals("RESEARCH_CHILD_CONFLICT", jdbc.queryForObject("SELECT ErrorCode FROM q_research_study WHERE ResearchStudyId=?", String.class, id));
      assertNull(jdbc.queryForObject("SELECT StartedAt FROM q_research_study WHERE ResearchStudyId=?", Object.class, id));
      assertNotNull(jdbc.queryForObject("SELECT FinishedAt FROM q_research_study WHERE ResearchStudyId=?", Object.class, id));
      ResearchStudyService service = new ResearchStudyService(research, new ObjectMapper());
      assertEquals(id, service.get(id).summary().researchStudyId());
      assertNull(service.get(id).summary().oosMaximumDrawdownRatio());
      assertEquals(1, service.page(1, 10, null, null, null, null).total());
      assertEquals(1, service.results("C".repeat(64), 1, 10, "OOS_TOTAL_RETURN_RATIO", "ASC").total());
    }
  }

  private static AnnotationConfigApplicationContext open() {
    DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    TestConfiguration.DATA_SOURCE = dataSource;
    return new AnnotationConfigApplicationContext(TestConfiguration.class);
  }

  private static void clear(JdbcTemplate jdbc) { jdbc.update("DELETE FROM q_research_study"); }

  @Configuration
  @MapperScan(basePackages = "com.aiprovider.mapper")
  static class TestConfiguration {
    static DataSource DATA_SOURCE;
    @Bean DataSource dataSource() { return DATA_SOURCE; }
    @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception { SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.afterPropertiesSet(); return factory.getObject(); }
  }
}
