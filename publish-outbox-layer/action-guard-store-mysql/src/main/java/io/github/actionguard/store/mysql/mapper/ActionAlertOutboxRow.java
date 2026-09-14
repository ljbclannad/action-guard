package io.github.actionguard.store.mysql.mapper;

import java.sql.Timestamp;

public class ActionAlertOutboxRow {
    private String id;
    private String eventId;
    private String dedupeKey;
    private String type;
    private String level;
    private String title;
    private String message;
    private String actionName;
    private String actionInstanceId;
    private String stepName;
    private String stepType;
    private Timestamp occurredAt;
    private String detailsJson;
    private String status;
    private Timestamp availableAt;
    private int deliveryAttemptCount;
    private String lastErrorMessage;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getActionInstanceId() {
        return actionInstanceId;
    }

    public void setActionInstanceId(String actionInstanceId) {
        this.actionInstanceId = actionInstanceId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Timestamp getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Timestamp occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(Timestamp availableAt) {
        this.availableAt = availableAt;
    }

    public int getDeliveryAttemptCount() {
        return deliveryAttemptCount;
    }

    public void setDeliveryAttemptCount(int deliveryAttemptCount) {
        this.deliveryAttemptCount = deliveryAttemptCount;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
