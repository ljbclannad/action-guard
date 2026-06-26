package io.github.actionguard.core.runtime;

public interface ActionCompensationExecutor {

    void compensate(String actionInstanceId);
}
