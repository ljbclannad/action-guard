package io.github.actionguard.starter.config;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.api.spi.ActionCompensator;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.repository.InMemoryActionCompensationLogRepository;
import io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository;
import io.github.actionguard.core.repository.InMemoryActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationService;
import io.github.actionguard.core.runtime.compensation.ActionCompensatorRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionLoader;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionValidator;
import io.github.actionguard.core.runtime.definition.InMemoryActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.YamlActionDefinitionLoader;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.DefaultActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.publish.DefaultActionPublisher;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.recovery.ActionStuckDetectionService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.retry.FixedAttemptActionRetryPolicy;
import io.github.actionguard.starter.metrics.InMemoryActionMetricsRecorder;
import io.github.actionguard.starter.properties.ActionGuardProperties;
import io.github.actionguard.starter.publisher.TransactionalActionPublisher;
import io.github.actionguard.starter.scheduler.ActionOutboxRecoveryScheduler;

/**
 * Action Guard starter 的核心自动配置入口。
 *
 * <p>
 * 整体装配流程如下：
 * <ol>
 * <li>业务应用（例如 action-guard-demo）在 pom 中依赖
 * {@code action-guard-spring-boot-starter}。</li>
 * <li>Spring Boot 启动时，通过 {@code @SpringBootApplication} 启用自动配置。</li>
 * <li>Spring Boot 从
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 读取到当前类，并将其导入应用上下文。</li>
 * <li>当前类通过 {@link EnableConfigurationProperties} 触发
 * {@link ActionGuardProperties} 绑定，将 {@code action.guard.*} 配置读入内存。</li>
 * <li>随后创建 Action Guard 运行时所需的核心 Bean，例如 definition registry、
 * publisher、execution callback、recovery service 以及默认的内存仓储实现。</li>
 * <li>demo 或业务应用可以继续声明自己的 {@code @Configuration} / {@code @Bean}，
 * 补充 MQ 拓扑、具体适配器 Bean，或者用自定义实现覆盖这里标注了
 * {@code @ConditionalOnMissingBean} 的默认 Bean。</li>
 * </ol>
 *
 * <p>
 * 因此，这个类本身不需要在 demo 中显式 {@code @Import}；只要 starter 在 classpath 上，
 * Spring Boot 就会自动完成发现、配置绑定和 Bean 装配。
 */
@AutoConfiguration
@EnableConfigurationProperties(ActionGuardProperties.class)
public class ActionGuardAutoConfiguration {

    @Bean
    public ActionPublisher actionPublisher(
            ActionDefinitionRegistry definitionRegistry,
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Clock clock,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            ActionGuardProperties properties) {
        // 对外暴露的是带事务语义的发布器；真正的落库动作仍由 core 的 DefaultActionPublisher 完成。
        return new TransactionalActionPublisher(new DefaultActionPublisher(
                definitionRegistry,
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionOutboxRepository,
                clock), actionInstanceRepository, actionOutboxRepository, actionExecutionMessageProducer,
                properties.getPublishRetryMaxAttempts(), actionObservabilityService);
    }

    @Bean
    public ActionDefinitionLoader actionDefinitionLoader() {
        // starter 默认按 YAML 定义文件加载 action，先覆盖最小可运行路径，后续如有需要可由业务方替换 loader。
        return new YamlActionDefinitionLoader();
    }

    @Bean
    public ActionDefinitionValidator actionDefinitionValidator() {
        // 定义校验器与加载器拆开，便于未来扩展不同来源的定义，同时复用同一套结构校验规则。
        return new ActionDefinitionValidator();
    }

    @Bean
    public ActionDefinitionRegistry actionDefinitionRegistry(
            ActionDefinitionLoader loader,
            ActionDefinitionValidator validator,
            ActionGuardProperties properties) {
        // definition 在启动时一次性加载进内存，运行期只读，简化执行链路中的查找与校验成本。
        return new InMemoryActionDefinitionRegistry(loadDefinitions(loader, properties), validator);
    }

    @Bean
    public StepHandlerRegistry stepHandlerRegistry(List<ActionStepHandler> handlers) {
        // 所有 ActionStepHandler 都通过 Spring 收集后统一建索引，把业务能力模块与 runtime 编排解耦开。
        return new StepHandlerRegistry(handlers);
    }

    @Bean
    public ActionCompensatorRegistry actionCompensatorRegistry(List<ActionCompensator> compensators) {
        // 补偿执行器也走统一注册表，保持正向执行与补偿执行在装配模型上的一致性。
        return new ActionCompensatorRegistry(compensators);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionRetryPolicy actionRetryPolicy() {
        // 默认重试策略放在 starter 层兜底，保证未显式配置策略时运行时也能得到可预测的失败恢复行为。
        return new FixedAttemptActionRetryPolicy(3);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionMetricsRecorder actionMetricsRecorder(ActionGuardProperties properties) {
        // 没有接入外部 metrics 时默认提供一个内存实现，既方便测试，也避免运行期空指针判断散落各处。
        return properties.isMetricsEnabled() ? new InMemoryActionMetricsRecorder() : (metricName, tags) -> {
        };
    }

    @Bean
    public ActionObservabilityService actionObservabilityService(
            Optional<ActionAlertPublisher> actionAlertPublisher,
            Optional<ActionMetricsRecorder> actionMetricsRecorder,
            Clock clock) {
        // 告警与指标都按 Optional 注入，表示可观测性是统一出口，但具体通道由外部能力模块按需补齐。
        return new ActionObservabilityService(actionAlertPublisher, actionMetricsRecorder, clock);
    }

    @Bean
    public ActionOutboxRecoveryService actionOutboxRecoveryService(
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock) {
        // recovery service 负责兜住“事务已提交，但消息未及时成功投递”的窗口，把 outbox 从即时发布失败带回可恢复轨道。
        return new ActionOutboxRecoveryService(
                actionOutboxRepository,
                actionExecutionMessageProducer,
                actionObservabilityService,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionOutboxRecoveryScheduler actionOutboxRecoveryScheduler(
            ActionOutboxRecoveryService actionOutboxRecoveryService,
            Optional<ActionCompensationService> actionCompensationService,
            Optional<ActionStuckDetectionService> actionStuckDetectionService,
            ActionGuardProperties properties) {
        // scheduler 只负责周期性触发恢复、补偿与卡住检测，不承载业务决策本身，避免把调度器做成另一套 runtime。
        return new ActionOutboxRecoveryScheduler(
                actionOutboxRecoveryService,
                actionCompensationService,
                actionStuckDetectionService,
                properties.getRecovery());
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionCompensationService actionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            ActionObservabilityService actionObservabilityService,
            Clock clock) {
        // 补偿服务单独成 Bean，是为了让“正向执行失败后的治理动作”保持独立边界，而不是塞进执行回调里耦成一团。
        return new ActionCompensationService(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                actionGovernancePolicyRepository,
                actionCompensationLogRepository,
                actionTransitionLogRepository,
                actionCompensatorRegistry,
                actionObservabilityService,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionStuckDetectionService actionStuckDetectionService(
            ActionInstanceRepository actionInstanceRepository,
            ActionObservabilityService actionObservabilityService,
            Clock clock) {
        // stuck detection 与 recovery 并列存在，专门识别长期未推进的 action，避免“消息补发正常但状态仍卡住”被静默忽略。
        return new ActionStuckDetectionService(actionInstanceRepository, actionObservabilityService, clock);
    }

    @Bean
    public ActionExecutionCallback actionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock) {
        // 执行回调是 runtime 的核心协调点：消费 MQ 消息后，最终都会落到这里推进 step 状态机。
        return new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                actionTransitionLogRepository,
                actionExecutionMessageProducer,
                actionObservabilityService,
                clock);
    }

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

    @Bean
    @ConditionalOnMissingBean
    public Clock actionGuardClock() {
        return Clock.systemUTC();
    }

    private List<ActionDefinition> loadDefinitions(ActionDefinitionLoader loader, ActionGuardProperties properties) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<ActionDefinition> definitions = new ArrayList<>();
        for (String locationPattern : properties.getDefinitionLocations()) {
            try {
                // 支持多个 location pattern，是为了让框架定义和业务定义可以并存，而不是只能从单一路径加载。
                Resource[] resources = resolver.getResources(locationPattern);
                for (Resource resource : resources) {
                    definitions.add(loader.load(resource.getURL().toString()));
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to resolve action definition locations: " + locationPattern,
                        ex);
            }
        }
        return definitions;
    }
}
