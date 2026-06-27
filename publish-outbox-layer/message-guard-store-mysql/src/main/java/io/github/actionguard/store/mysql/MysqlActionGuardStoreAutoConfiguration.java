package io.github.actionguard.store.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.store.mysql.mapper.ActionConsumeLogMapper;
import io.github.actionguard.store.mysql.mapper.ActionInstanceMapper;
import io.github.actionguard.store.mysql.mapper.ActionOutboxMapper;
import io.github.actionguard.store.mysql.mapper.ActionStepInstanceMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@MapperScan(basePackageClasses = ActionInstanceMapper.class)
public class MysqlActionGuardStoreAutoConfiguration {

    @Bean
    public ObjectMapper actionGuardObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ActionInstanceRepository actionInstanceRepository(ActionInstanceMapper mapper, ObjectMapper objectMapper) {
        return new MysqlActionInstanceRepository(mapper, objectMapper);
    }

    @Bean
    public ActionStepInstanceRepository actionStepInstanceRepository(ActionStepInstanceMapper mapper, ObjectMapper objectMapper) {
        return new MysqlActionStepInstanceRepository(mapper, objectMapper);
    }

    @Bean
    public ActionOutboxRepository actionOutboxRepository(ActionOutboxMapper mapper) {
        return new MysqlActionOutboxRepository(mapper);
    }

    @Bean
    public ActionConsumeLogRepository actionConsumeLogRepository(ActionConsumeLogMapper mapper) {
        return new MysqlActionConsumeLogRepository(mapper);
    }

    @Bean
    public ActionGovernancePolicyRepository actionGovernancePolicyRepository(JdbcTemplate jdbcTemplate) {
        return new MysqlActionGovernancePolicyRepository(jdbcTemplate);
    }

    @Bean
    public ActionCompensationLogRepository actionCompensationLogRepository(JdbcTemplate jdbcTemplate) {
        return new MysqlActionCompensationLogRepository(jdbcTemplate);
    }
}
