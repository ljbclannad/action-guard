package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于内存的动作定义注册表。
 *
 * <p>它处在“定义加载完成 -> 运行时按名称查找定义”的边界上：启动阶段接收已经解析好的
 * {@link ActionDefinition} 列表，先做统一校验和去重，再固化为只读索引，供发布链路和执行链路按
 * actionName 查询。
 *
 * <p>因为定义在当前实现里是启动时一次性加载、运行期只读，所以这里选择简单的内存结构，
 * 用更低的查找成本换取运行期稳定性和可预测性。
 */
public class InMemoryActionDefinitionRegistry implements ActionDefinitionRegistry {

    private final Map<String, ActionDefinition> definitionsByName;

    public InMemoryActionDefinitionRegistry(List<ActionDefinition> definitions, ActionDefinitionValidator validator) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        Objects.requireNonNull(validator, "validator must not be null");

        Map<String, ActionDefinition> indexedDefinitions = new LinkedHashMap<>();
        for (ActionDefinition definition : definitions) {
            validator.validate(definition);
            ActionDefinition previous = indexedDefinitions.putIfAbsent(definition.name(), definition);
            if (previous != null) {
                throw new IllegalStateException("duplicate action definition name: " + definition.name());
            }
        }
        this.definitionsByName = Map.copyOf(indexedDefinitions);
    }

    @Override
    public Optional<ActionDefinition> find(String actionName) {
        return Optional.ofNullable(definitionsByName.get(actionName));
    }

    @Override
    public ActionDefinition getRequired(String actionName) {
        return find(actionName)
                .orElseThrow(() -> new IllegalArgumentException("No ActionDefinition registered for actionName: " + actionName));
    }

    @Override
    public Collection<ActionDefinition> getAll() {
        return definitionsByName.values();
    }
}
