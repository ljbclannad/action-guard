package io.github.actionguard.store.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.store.mysql.mapper.ActionInstanceMapper;
import io.github.actionguard.store.mysql.mapper.ActionInstanceRow;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MysqlActionInstanceRepository implements ActionInstanceRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ActionInstanceMapper mapper;
    private final ObjectMapper objectMapper;

    public MysqlActionInstanceRepository(ActionInstanceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ActionInstance> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toModel);
    }

    @Override
    public Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey) {
        return Optional.ofNullable(mapper.selectByActionNameAndBizKey(actionName, bizKey)).map(this::toModel);
    }

    @Override
    public Optional<ActionInstance> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mapper.selectByIdempotencyKey(idempotencyKey)).map(this::toModel);
    }

    @Override
    public List<ActionInstance> findByStatusesAndUpdatedBefore(List<ActionStatus> statuses, Instant updatedBeforeOrAt, int limit) {
        if (statuses == null || statuses.isEmpty() || limit <= 0) {
            return List.of();
        }
        return mapper.selectByStatusesUpdatedBefore(
                        statuses.stream().map(Enum::name).toList(),
                        Timestamp.from(updatedBeforeOrAt),
                        limit
                ).stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    public ActionInstance save(ActionInstance instance) {
        ActionInstanceRow row = toRow(instance);
        ActionInstanceRow existing = mapper.selectById(instance.id());
        if (existing == null) {
            mapper.insert(row);
            return instance;
        }
        if (mapper.updateOptimistically(row) != 1) {
            throw new OptimisticLockingFailureException("ActionInstance version conflict: " + instance.id());
        }
        return new ActionInstance(
                instance.id(),
                instance.actionName(),
                instance.definitionVersion(),
                instance.bizKey(),
                instance.idempotencyKey(),
                instance.status(),
                instance.currentStepIndex(),
                instance.totalStepCount(),
                instance.attributes(),
                instance.lastErrorCode(),
                instance.lastErrorMessage(),
                instance.version() + 1,
                instance.createdAt(),
                instance.updatedAt()
        );
    }

    private ActionInstanceRow toRow(ActionInstance instance) {
        ActionInstanceRow row = new ActionInstanceRow();
        row.setId(instance.id());
        row.setActionName(instance.actionName());
        row.setDefinitionVersion(instance.definitionVersion());
        row.setBizKey(instance.bizKey());
        row.setIdempotencyKey(instance.idempotencyKey());
        row.setStatus(instance.status().name());
        row.setCurrentStepIndex(instance.currentStepIndex());
        row.setTotalStepCount(instance.totalStepCount());
        row.setAttributesJson(toJson(instance.attributes()));
        row.setLastErrorCode(instance.lastErrorCode());
        row.setLastErrorMessage(instance.lastErrorMessage());
        row.setVersion(instance.version());
        row.setCreatedAt(Timestamp.from(instance.createdAt()));
        row.setUpdatedAt(Timestamp.from(instance.updatedAt()));
        return row;
    }

    private ActionInstance toModel(ActionInstanceRow row) {
        return new ActionInstance(
                row.getId(),
                row.getActionName(),
                row.getDefinitionVersion(),
                row.getBizKey(),
                row.getIdempotencyKey(),
                ActionStatus.valueOf(row.getStatus()),
                row.getCurrentStepIndex(),
                row.getTotalStepCount(),
                fromJson(row.getAttributesJson()),
                row.getLastErrorCode(),
                row.getLastErrorMessage(),
                row.getVersion(),
                row.getCreatedAt().toInstant(),
                row.getUpdatedAt().toInstant()
        );
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize action instance attributes", ex);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize action instance attributes", ex);
        }
    }
}
