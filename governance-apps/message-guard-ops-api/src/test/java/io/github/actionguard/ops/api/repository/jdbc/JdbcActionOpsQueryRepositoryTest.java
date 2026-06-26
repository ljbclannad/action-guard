package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.ops.api.model.ActionQueryFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActionOpsQueryRepositoryTest {

    @Test
    void shouldPageActionsAndLoadActionDetails() {
        JdbcActionOpsQueryRepository repository = TestActionOpsQueryRepositoryFactory.createWithSeedData();

        var page = repository.queryActions(new ActionQueryFilter(1, 10, "order-cancel-flow", null, null, null, null));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).actionName()).isEqualTo("order-cancel-flow");

        var detail = repository.getActionDetail("act-1").orElseThrow();
        assertThat(detail.steps()).hasSize(2);
        assertThat(detail.consumes()).hasSize(1);
    }
}
