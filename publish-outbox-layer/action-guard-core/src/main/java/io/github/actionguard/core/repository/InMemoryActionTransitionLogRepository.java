package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionTransitionLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryActionTransitionLogRepository implements ActionTransitionLogRepository {

    private final CopyOnWriteArrayList<ActionTransitionLog> storage = new CopyOnWriteArrayList<>();

    @Override
    public ActionTransitionLog save(ActionTransitionLog log) {
        storage.add(log);
        return log;
    }

    @Override
    public List<ActionTransitionLog> findByActionInstanceId(String actionInstanceId) {
        return storage.stream()
                .filter(log -> log.actionInstanceId().equals(actionInstanceId))
                .sorted(java.util.Comparator.comparing(ActionTransitionLog::createdAt))
                .toList();
    }
}
