package io.github.actionguard.store.redis;

public class RedisActionLockSupport {

    public boolean tryLock(String actionKey) {
        return actionKey != null && !actionKey.isBlank();
    }
}
