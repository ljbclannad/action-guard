package io.github.actionguard.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * 一次已发布的 Action 执行实例，保存业务关联信息、执行进度和状态，对应 action_instance 持久化记录。
 *
 * <p>与描述流程规则的 ActionDefinition、携带本次输入的 ActionRequest 不同，本模型表示运行时执行记录；
 * record 构造方法本身不执行校验或持久化，也不负责状态迁移。
 *
 * @param id Action 实例唯一标识，默认发布器生成 UUID；用于关联步骤、Outbox 和执行日志
 * @param actionName 本次执行使用的 Action 定义名称
 * @param definitionVersion 发布时从 Action 定义复制的版本号，用于记录本次执行采用的定义版本；
 *                          不同于持久化并发控制使用的 {@code version}
 * @param bizKey 关联业务记录的标识，例如订单号，来源于发布请求
 * @param idempotencyKey 发布去重键，默认使用 {@code actionName + ":" + bizKey}，也可由发布请求显式指定；
 *                       命中已有实例时不重复创建 Action
 * @param status Action 当前执行状态，例如新建、执行中、重试中、成功或补偿中；合法迁移由状态机约束
 * @param currentStepIndex 从 {@code 0} 开始的当前步骤指针，发布时为 {@code 0}；
 *                         正常顺序推进完成后可等于 {@code totalStepCount}，此时不再指向实际步骤
 * @param totalStepCount 发布时从定义复制的总步骤数，不是已完成步骤数
 * @param attributes 动作级公共输入属性，来源于发布请求；默认发布器将 {@code null} 转为空映射
 * @param lastErrorCode 最近一次状态迁移记录的错误码，无错误或迁移清除错误时可为 {@code null}
 * @param lastErrorMessage 最近一次状态迁移记录的错误原因摘要，无错误或迁移清除错误时可为 {@code null}
 * @param version 持久化乐观锁版本号，默认发布时为 {@code 0}，Repository 成功更新时递增，
 *                用于检测并发修改，避免旧快照覆盖新状态
 * @param createdAt 实例创建时间，默认发布器通过注入的 Clock 获取，后续状态迁移保留此值
 * @param updatedAt 实例最近更新时间，创建时与 {@code createdAt} 相同，状态迁移时更新；
 *                  恢复扫描可据此判断实例是否长时间未推进
 */
public record ActionInstance(
        String id,
        String actionName,
        int definitionVersion,
        String bizKey,
        String idempotencyKey,
        ActionStatus status,
        int currentStepIndex,
        int totalStepCount,
        Map<String, Object> attributes,
        String lastErrorCode,
        String lastErrorMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public ActionInstance(
            String id, String actionName, String bizKey, ActionStatus status, int currentStepIndex,
            int totalStepCount, Map<String, Object> attributes, String lastErrorCode, String lastErrorMessage,
            int version, Instant createdAt, Instant updatedAt
    ) {
        this(id, actionName, 1, bizKey, actionName + ":" + bizKey, status, currentStepIndex, totalStepCount,
                attributes, lastErrorCode, lastErrorMessage, version, createdAt, updatedAt);
    }
}
