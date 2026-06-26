package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionOutbox;

import java.util.Optional;

public interface ActionOutboxRepository {

    ActionOutbox save(ActionOutbox outbox);

    Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId);
}
