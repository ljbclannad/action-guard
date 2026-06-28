package io.github.actionguard.core.runtime.recovery;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ActionStuckDetectionService {

    private static final List<ActionStatus> STUCK_CANDIDATE_STATUSES = List.of(
            ActionStatus.NEW,
            ActionStatus.DISPATCHING,
            ActionStatus.RETRYING,
            ActionStatus.COMPENSATING
    );

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionObservabilityService actionObservabilityService;
    private final Clock clock;
    private final Map<String, String> alertedFingerprints = new ConcurrentHashMap<>();

    public ActionStuckDetectionService(
            ActionInstanceRepository actionInstanceRepository,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int detectStuckActions(int batchSize, Duration timeout) {
        if (batchSize <= 0) {
            return 0;
        }
        Instant threshold = clock.instant().minus(timeout);
        List<ActionInstance> candidates = actionInstanceRepository.findByStatusesAndUpdatedBefore(
                STUCK_CANDIDATE_STATUSES,
                threshold,
                batchSize
        );
        int detectedCount = 0;
        for (ActionInstance candidate : candidates) {
            String fingerprint = candidate.status().name() + "@" + candidate.updatedAt();
            String previous = alertedFingerprints.putIfAbsent(candidate.id(), fingerprint);
            if (fingerprint.equals(previous)) {
                continue;
            }
            alertedFingerprints.put(candidate.id(), fingerprint);
            actionObservabilityService.actionStuck(candidate, timeout);
            detectedCount++;
        }
        return detectedCount;
    }
}
