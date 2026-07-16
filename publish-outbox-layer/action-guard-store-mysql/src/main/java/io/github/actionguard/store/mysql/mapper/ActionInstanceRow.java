package io.github.actionguard.store.mysql.mapper;

import java.sql.Timestamp;

public class ActionInstanceRow {

    private String id;
    private String actionName;
    private int definitionVersion;
    private String bizKey;
    private String idempotencyKey;
    private String status;
    private int currentStepIndex;
    private int totalStepCount;
    private String attributesJson;
    private String lastErrorCode;
    private String lastErrorMessage;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public int getDefinitionVersion() { return definitionVersion; }
    public void setDefinitionVersion(int definitionVersion) { this.definitionVersion = definitionVersion; }
    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public int getTotalStepCount() { return totalStepCount; }
    public void setTotalStepCount(int totalStepCount) { this.totalStepCount = totalStepCount; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String attributesJson) { this.attributesJson = attributesJson; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
