package io.github.actionguard.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * 某个 Action 执行实例中的单个步骤记录，保存步骤配置、输入载荷和执行结果，对应 action_step_instance 持久化记录。
 *
 * <p>发布时从 ActionStepDefinition 复制步骤配置，并从对应位置的 ActionStepRequest 提取载荷；
 * record 构造方法本身不执行校验、业务处理或持久化。
 *
 * @param id 步骤实例唯一标识，默认发布器生成 UUID
 * @param actionInstanceId 所属 Action 实例的唯一标识，用于关联整个流程
 * @param stepIndex 从 {@code 0} 开始的步骤索引，按发布时定义中的列表顺序生成，用于串行执行和定位步骤
 * @param stepName 发布时从定义复制的步骤名称，用于标识流程中的业务步骤
 * @param stepType 发布时从定义复制的步骤类型，运行时据此查找对应的 ActionStepHandler
 * @param target 发布时从定义复制的执行目标，传入步骤执行上下文，由具体 Handler 解释和使用
 * @param status 步骤当前状态，默认创建为 {@code PENDING}；执行结果落库时更新为 {@code SUCCESS} 或
 *               {@code FAILED}，不等同于整个 Action 的状态
 * @param attemptCount 已记录的步骤执行尝试次数，默认创建为 {@code 0}；当前实现每次保存执行成功或失败结果时
 *                     加一，包含首次执行，不是纯重试次数，也不是 Outbox 消息投递次数
 * @param payload 当前步骤的输入载荷，默认按步骤索引从发布请求中提取；缺少对应请求或载荷为 {@code null}
 *                时使用空映射，与 Action 级公共属性 {@code attributes} 分开传入执行上下文
 * @param lastErrorCode 最近一次失败结果的错误码，初始为 {@code null}，成功结果落库时清除
 * @param lastErrorMessage 最近一次失败结果的错误原因摘要，初始为 {@code null}，成功结果落库时清除
 * @param version 持久化乐观锁版本号，默认创建为 {@code 0}，Repository 成功更新时递增，
 *                用于检测并发修改，不是步骤索引或执行次数
 * @param createdAt 步骤实例创建时间，默认发布时与所属 Action 使用同一个时间点，后续结果更新保留此值
 * @param updatedAt 步骤实例最近更新时间，创建时与 {@code createdAt} 相同，执行结果落库时使用结果发生时间更新
 */
public record ActionStepInstance(
        String id,
        String actionInstanceId,
        int stepIndex,
        String stepName,
        String stepType,
        String target,
        ActionStepStatus status,
        int attemptCount,
        Map<String, Object> payload,
        String lastErrorCode,
        String lastErrorMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
