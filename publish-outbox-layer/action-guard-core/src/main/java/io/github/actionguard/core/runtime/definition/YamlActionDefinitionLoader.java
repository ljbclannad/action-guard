package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * 基于 YAML 的动作定义加载器。
 *
 * <p>它位于“配置资源 -> 运行时定义对象”的转换边界上，负责把
 * {@code actions/*.yml(yaml)} 文件解析为 {@link ActionDefinition} /
 * {@link ActionStepDefinition}。starter 在启动时扫描 definition locations 后，
 * 会逐个调用这个类完成加载。
 *
 * <p>它的输出已经是运行时可直接消费的强类型定义模型，后续 registry、publisher、
 * execution callback 都不会再接触原始 YAML 结构，从而把解析细节收敛在加载阶段。
 */
public class YamlActionDefinitionLoader implements ActionDefinitionLoader {

    @Override
    @SuppressWarnings("unchecked")
    public ActionDefinition load(String location) {
        Yaml yaml = new Yaml();
        try (InputStream stream = openStream(location)) {
            if (stream == null) {
                throw new IllegalArgumentException("Action definition not found: " + location);
            }
            Map<String, Object> raw = yaml.load(stream);
            List<Map<String, Object>> steps = (List<Map<String, Object>>) raw.get("steps");
            // 这里不直接暴露原始 YAML 结构，而是在加载阶段就转换成强约束的定义模型，
            // 后续 runtime 读取到的都是统一对象，避免把解析细节散到执行链路里。
            List<ActionStepDefinition> definitions = steps.stream()
                    .map(step -> new ActionStepDefinition(
                            String.valueOf(step.get("name")),
                            String.valueOf(step.get("stepType")),
                            String.valueOf(step.get("target")),
                            integerValue(step.get("maxRetryCount")),
                            longValue(step.get("retryBackoffMillis")),
                            longValue(step.get("timeoutMillis"))
                    ))
                    .toList();
            return new ActionDefinition(
                    String.valueOf(raw.get("name")),
                    String.valueOf(raw.get("description")),
                    raw.get("compensationEnabled") instanceof Boolean enabled ? enabled : false,
                    definitions
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load action definition: " + location, ex);
        }
    }

    private InputStream openStream(String location) throws Exception {
        // 先按 classpath 取资源，兼容 starter 扫描到的内部定义；取不到时再退回 URL 方式。
        InputStream classpathStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        if (classpathStream != null) {
            return classpathStream;
        }
        return new URL(location).openStream();
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
