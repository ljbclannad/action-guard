package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionInstance;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MysqlActionInstanceRepository {

    private final Map<String, ActionInstance> storage = new ConcurrentHashMap<>();

    public Optional<ActionInstance> find(String actionName, String bizKey) {
        return Optional.ofNullable(storage.get(key(actionName, bizKey)));
    }

    public ActionInstance save(ActionInstance instance) {
        storage.put(key(instance.actionName(), instance.bizKey()), instance);
        return instance;
    }

    private String key(String actionName, String bizKey) {
        return actionName + ":" + bizKey;
    }
}
