package io.github.actionguard.api.definition;

import java.util.List;

/**
 * Action 流程定义，描述业务流程的版本、补偿开关和有序步骤，当前默认由 YAML 加载器创建。
 *
 * <p>定义与单次发布请求、执行实例相互独立；record 构造方法本身不执行定义校验。
 *
 * @param name Action 定义名称，定义校验时不能为空或空白；与发布请求的 {@code actionName} 对应，
 *             用于查找执行定义
 * @param version 定义版本号，发布时保存到 Action 实例中，不是数据库乐观锁版本；
 *                不含版本参数的构造方法使用 {@code 1}，YAML 未配置版本时也默认使用 {@code 1}
 * @param description 流程的业务用途说明，不参与步骤执行编排
 * @param compensationEnabled 是否默认允许发起补偿，YAML 未配置时为 {@code false}；
 *                            数据库中按 Action 名称配置的非 {@code null} 治理策略值优先于此值。
 *                            开启仅表示允许补偿，不表示步骤失败后自动补偿，发起时仍须满足状态机约束
 * @param steps 有序的步骤定义，决定步骤名称、类型、目标及重试等配置，发布时按列表顺序创建步骤实例；
 *              定义校验时列表不能为 {@code null} 或为空，元素不能为 {@code null}，步骤名称不能重复
 */
public record ActionDefinition(
        String name,
        int version,
        String description,
        boolean compensationEnabled,
        List<ActionStepDefinition> steps
) {

    public ActionDefinition(String name, String description, boolean compensationEnabled, List<ActionStepDefinition> steps) {
        this(name, 1, description, compensationEnabled, steps);
    }
}
