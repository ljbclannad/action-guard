package io.github.actionguard.store.mysql.mapper;

import java.sql.Timestamp;

public class ActionConsumeLogRow {

    private String id;
    private String messageId;
    private String actionInstanceId;
    private String consumerGroup;
    private String consumeStatus;
    private String dedupeKey;
    private int attemptCount;
    private String lastErrorMessage;
    private int version;
    private Timestamp firstReceivedAt;
    private Timestamp lastReceivedAt;
    private Timestamp updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getActionInstanceId() { return actionInstanceId; }
    public void setActionInstanceId(String actionInstanceId) { this.actionInstanceId = actionInstanceId; }
    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
    public String getConsumeStatus() { return consumeStatus; }
    public void setConsumeStatus(String consumeStatus) { this.consumeStatus = consumeStatus; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Timestamp getFirstReceivedAt() { return firstReceivedAt; }
    public void setFirstReceivedAt(Timestamp firstReceivedAt) { this.firstReceivedAt = firstReceivedAt; }
    public Timestamp getLastReceivedAt() { return lastReceivedAt; }
    public void setLastReceivedAt(Timestamp lastReceivedAt) { this.lastReceivedAt = lastReceivedAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
