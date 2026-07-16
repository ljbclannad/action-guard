package io.github.actionguard.api;

/** Result of accepting an Action publication request. */
public record ActionPublication(String actionInstanceId, boolean duplicate) {
}
