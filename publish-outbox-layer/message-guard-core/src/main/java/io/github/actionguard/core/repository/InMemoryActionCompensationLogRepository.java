package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionCompensationLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryActionCompensationLogRepository implements ActionCompensationLogRepository {

    private final CopyOnWriteArrayList<ActionCompensationLog> storage = new CopyOnWriteArrayList<>();

    @Override
    public ActionCompensationLog save(ActionCompensationLog log) {
        storage.add(log);
        return log;
    }

    @Override
    public List<ActionCompensationLog> findByActionInstanceId(String actionInstanceId) {
        return storage.stream()
                .filter(log -> log.actionInstanceId().equals(actionInstanceId))
                .toList();
    }
}
