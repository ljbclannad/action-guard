package io.github.actionguard.store.mysql.mapper;

import java.sql.Timestamp;

public class ActionOutboxRow {

    private String id;
    private String actionInstanceId;
    private String topic;
    private String status;
    private Timestamp availableAt;
    private int attemptCount;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActionInstanceId() { return actionInstanceId; }
    public void setActionInstanceId(String actionInstanceId) { this.actionInstanceId = actionInstanceId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getAvailableAt() { return availableAt; }
    public void setAvailableAt(Timestamp availableAt) { this.availableAt = availableAt; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
