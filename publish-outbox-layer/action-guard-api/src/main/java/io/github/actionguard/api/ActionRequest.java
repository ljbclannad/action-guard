package io.github.actionguard.api;

import java.util.List;
import java.util.Map;

/**
 * Action 发布请求，提供业务关联信息、动作级属性和步骤载荷。
 *
 * <p>以下默认值与校验规则由默认发布器在发布时处理，record 构造方法本身不执行校验。
 *
 * @param actionName 已注册的 Action 定义名称，发布时不能为空或空白；用于查找执行定义
 * @param bizKey 关联业务记录的标识，例如订单号，发布时不能为空或空白；同时参与默认幂等键生成
 * @param attributes 动作级公共属性，保存到 Action 实例；为 {@code null} 时按空映射处理，
 *                   非空映射中的键和值不能为 {@code null}
 * @param steps 按 Action 定义中的步骤顺序提供请求载荷；当前默认发布器只读取各项的
 *              {@link ActionStepRequest#payload()}，不使用请求中的步骤名称、类型和目标覆盖定义。
 *              列表为 {@code null}、为空或缺少对应项时，相关步骤使用空载荷；多余项不生成额外步骤。
 *              列表元素不能为 {@code null}，载荷为 {@code null} 时按空映射处理，
 *              非空载荷中的键和值不能为 {@code null}
 * @param idempotencyKey 发布去重键；为 {@code null} 或空白时使用 {@code actionName + ":" + bizKey}。
 *                       显式指定时直接使用原值，不自动附加 Action 名称；命中已有实例时返回该实例，
 *                       不重复创建 Action
 */
public record ActionRequest(
        String actionName,
        String bizKey,
        Map<String, Object> attributes,
        List<ActionStepRequest> steps,
        String idempotencyKey
) {

    public ActionRequest(String actionName, String bizKey, Map<String, Object> attributes, List<ActionStepRequest> steps) {
        this(actionName, bizKey, attributes, steps, null);
    }
}
