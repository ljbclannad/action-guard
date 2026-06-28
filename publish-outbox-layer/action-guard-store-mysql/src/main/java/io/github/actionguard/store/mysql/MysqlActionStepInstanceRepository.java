package io.github.actionguard.store.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.store.mysql.mapper.ActionStepInstanceMapper;
import io.github.actionguard.store.mysql.mapper.ActionStepInstanceRow;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class MysqlActionStepInstanceRepository implements ActionStepInstanceRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ActionStepInstanceMapper mapper;
    private final ObjectMapper objectMapper;

    public MysqlActionStepInstanceRepository(ActionStepInstanceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ActionStepInstance save(ActionStepInstance stepInstance) {
        ActionStepInstanceRow row = toRow(stepInstance);
        ActionStepInstanceRow existing = mapper.selectById(stepInstance.id());
        if (existing == null) {
            mapper.insert(row);
            return stepInstance;
        }
        if (mapper.updateOptimistically(row) != 1) {
            throw new OptimisticLockingFailureException("ActionStepInstance version conflict: " + stepInstance.id());
        }
        return withIncrementedVersion(stepInstance);
    }

    @Override
    public List<ActionStepInstance> saveAll(List<ActionStepInstance> stepInstances) {
        stepInstances.forEach(this::save);
        return List.copyOf(stepInstances);
    }

    @Override
    public List<ActionStepInstance> findByActionInstanceId(String actionInstanceId) {
        return mapper.selectByActionInstanceId(actionInstanceId).stream().map(this::toModel).toList();
    }

    private ActionStepInstanceRow toRow(ActionStepInstance stepInstance) {
        ActionStepInstanceRow row = new ActionStepInstanceRow();
        row.setId(stepInstance.id());
        row.setActionInstanceId(stepInstance.actionInstanceId());
        row.setStepIndex(stepInstance.stepIndex());
        row.setStepName(stepInstance.stepName());
        row.setStepType(stepInstance.stepType());
        row.setTarget(stepInstance.target());
        row.setStatus(stepInstance.status().name());
        row.setAttemptCount(stepInstance.attemptCount());
        row.setPayloadJson(toJson(stepInstance.payload()));
        row.setLastErrorCode(stepInstance.lastErrorCode());
        row.setLastErrorMessage(stepInstance.lastErrorMessage());
        row.setVersion(stepInstance.version());
        row.setCreatedAt(Timestamp.from(stepInstance.createdAt()));
        row.setUpdatedAt(Timestamp.from(stepInstance.updatedAt()));
        return row;
    }

    private ActionStepInstance toModel(ActionStepInstanceRow row) {
        return new ActionStepInstance(
                row.getId(),
                row.getActionInstanceId(),
                row.getStepIndex(),
                row.getStepName(),
                row.getStepType(),
                row.getTarget(),
                ActionStepStatus.valueOf(row.getStatus()),
                row.getAttemptCount(),
                fromJson(row.getPayloadJson()),
                row.getLastErrorCode(),
                row.getLastErrorMessage(),
                row.getVersion(),
                row.getCreatedAt().toInstant(),
                row.getUpdatedAt().toInstant()
        );
    }

    private ActionStepInstance withIncrementedVersion(ActionStepInstance stepInstance) {
        return new ActionStepInstance(
                stepInstance.id(),
                stepInstance.actionInstanceId(),
                stepInstance.stepIndex(),
                stepInstance.stepName(),
                stepInstance.stepType(),
                stepInstance.target(),
                stepInstance.status(),
                stepInstance.attemptCount(),
                stepInstance.payload(),
                stepInstance.lastErrorCode(),
                stepInstance.lastErrorMessage(),
                stepInstance.version() + 1,
                stepInstance.createdAt(),
                stepInstance.updatedAt()
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize step payload", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize step payload", ex);
        }
    }
}
