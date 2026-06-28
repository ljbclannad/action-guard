package io.github.actionguard.core.runtime.compensation;

public interface ActionCompensationExecutor {

    void compensate(String actionInstanceId);
}
