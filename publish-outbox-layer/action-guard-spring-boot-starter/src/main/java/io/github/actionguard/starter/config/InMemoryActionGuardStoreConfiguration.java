package io.github.actionguard.starter.config;

import io.github.actionguard.core.repository.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内存仓储仅在接入方明确选择时启用。
 *
 * <p>{@link ConditionalOnProperty} 根据配置决定是否启用整组仓储：
 * <ul>
 * <li>{@code prefix} 与 {@code name} 拼接为 {@code action.guard.store.type}，
 * {@code havingValue = "memory"} 表示仅在显式选择内存存储时匹配。</li>
 * <li>{@code matchIfMissing} 默认是 {@code false}，未配置时不会启用本配置类；
 * 不应改成 {@code true}，以免缺少存储配置时静默回退到内存。</li>
 * <li>一般使用时，若省略 {@code havingValue}，默认判断是属性存在且值不为
 * {@code false}，并非严格等于 {@code true}；布尔开关建议显式指定期望值。</li>
 * </ul>
 *
 * <p>{@link ConditionalOnMissingBean} 根据已有 Bean 决定是否补齐各个默认仓储：
 * <ul>
 * <li>标在 {@link Bean} 方法上且未指定类型时，按方法声明的返回类型判断，
 * 而不是按方法名或方法内部创建的具体实现类判断；因此返回仓储接口可让自定义实现生效。</li>
 * <li>接入方已提供同一仓储接口的 Bean 时，对应默认 Bean 不注册，其他缺失的仓储仍会补齐。
 * 这不是覆盖已有 Bean，也不是像 {@code @Primary} 那样从多个候选中选择一个。</li>
 * <li>条件只能看到评估时已处理的 Bean 定义。业务配置通常先于自动配置处理；
 * 若多个自动配置提供同一接口，应明确安排自动配置顺序，不要依赖偶然的加载顺序。</li>
 * </ul>
 *
 * <p>两层条件共同生效：只有“配置选择 memory”且“对应仓储接口尚无 Bean”时，
 * 才注册该默认内存仓储。自定义仓储仍需保持整组仓储的数据与事务语义一致，
 * 不应利用单个 Bean 的替换混用不兼容的存储实现。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "action.guard.store", name = "type", havingValue = "memory")
public class InMemoryActionGuardStoreConfiguration {

    // 等价于 @ConditionalOnMissingBean(ActionInstanceRepository.class)；下方仓储方法同理。
    @Bean
    @ConditionalOnMissingBean
    public ActionInstanceRepository actionInstanceRepository() {
        return new InMemoryActionInstanceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionStepInstanceRepository actionStepInstanceRepository() {
        return new InMemoryActionStepInstanceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionOutboxRepository actionOutboxRepository() {
        return new InMemoryActionOutboxRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionAlertOutboxRepository actionAlertOutboxRepository() {
        return new InMemoryActionAlertOutboxRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionConsumeLogRepository actionConsumeLogRepository() {
        return new InMemoryActionConsumeLogRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionGovernancePolicyRepository actionGovernancePolicyRepository() {
        return new InMemoryActionGovernancePolicyRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionCompensationLogRepository actionCompensationLogRepository() {
        return new InMemoryActionCompensationLogRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionTransitionLogRepository actionTransitionLogRepository() {
        return new InMemoryActionTransitionLogRepository();
    }
}
