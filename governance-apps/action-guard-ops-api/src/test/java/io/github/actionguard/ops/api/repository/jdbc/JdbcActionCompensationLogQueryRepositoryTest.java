package io.github.actionguard.ops.api.repository.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActionCompensationLogQueryRepositoryTest {

    @Test
    void shouldQueryCompensationLogsByActionInstanceId() {
        JdbcActionCompensationLogQueryRepository repository = TestActionCompensationLogQueryRepositoryFactory.createWithSeedData();

        assertThat(repository.findByActionInstanceId("act-1")).hasSize(2);
        assertThat(repository.findByActionInstanceId("act-1").get(0).compensationBatchId()).isEqualTo("batch-1");
    }
}
