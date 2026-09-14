package io.github.actionguard.starter.config;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.spi.*;
import io.github.actionguard.core.repository.*;
import io.github.actionguard.core.runtime.compensation.ActionCompensationService;
import io.github.actionguard.core.runtime.compensation.ActionCompensatorRegistry;
import io.github.actionguard.core.runtime.definition.*;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.DefaultActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionAlertOutboxRecorder;
import io.github.actionguard.core.runtime.observability.ActionAlertOutboxRecoveryService;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.publish.DefaultActionPublisher;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.recovery.ActionStuckDetectionService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.retry.FixedAttemptActionRetryPolicy;
import io.github.actionguard.starter.metrics.InMemoryActionMetricsRecorder;
import io.github.actionguard.starter.properties.ActionGuardProperties;
import io.github.actionguard.starter.publisher.TransactionalActionPublisher;
import io.github.actionguard.starter.scheduler.ActionAlertOutboxRecoveryScheduler;
import io.github.actionguard.starter.scheduler.ActionOutboxRecoveryScheduler;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * publisher、execution callback、recovery service；仓储通过 store.type 显式选择。</li>
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
@Import(InMemoryActionGuardStoreConfiguration.class)
public class ActionGuardAutoConfiguration {

    /** 在运行时 Bean 实例化前检查选择条件，避免缺少数据库能力时回退到内存。 */
    @Bean
    public static BeanFactoryPostProcessor actionGuardStoreSelectionValidator(Environment environment) {
        return beanFactory -> {
            String type = environment.getProperty("action.guard.store.type");
            if (!"memory".equalsIgnoreCase(type) && !"mysql".equalsIgnoreCase(type)) {
                throw new IllegalStateException("必须显式配置 action.guard.store.type，支持 memory 或 mysql");
            }
            if ("mysql".equalsIgnoreCase(type)) {
                if (!ClassUtils.isPresent("io.github.actionguard.store.mysql.MysqlActionGuardStoreAutoConfiguration",
                        beanFactory.getBeanClassLoader())) {
                    throw new IllegalStateException("action.guard.store.type=mysql 需要引入 action-guard-store-mysql 模块");
                }
                if (beanFactory.getBeanNamesForType(DataSource.class, false, false).length == 0) {
                    throw new IllegalStateException("action.guard.store.type=mysql 需要配置 DataSource 和数据库驱动");
                }
            }
        };
    }

    /**
     * 仅检查类型和 Bean 定义，不提前实例化连接工厂或访问消息服务器。
     */
    @Bean
    public static BeanFactoryPostProcessor actionGuardExecutionTransportSelectionValidator(Environment environment) {
        return beanFactory -> {
            String transport = environment.getProperty("action.guard.execution.transport");
            if (transport == null) {
                return;
            }
            if (!"rabbitmq".equalsIgnoreCase(transport) && !"rocketmq".equalsIgnoreCase(transport)) {
                throw new IllegalStateException("action.guard.execution.transport='" + transport
                        + "'，仅支持 rabbitmq 或 rocketmq，不能配置空值或空白");
            }
            ClassLoader classLoader = beanFactory.getBeanClassLoader();
            boolean rabbitMq = "rabbitmq".equalsIgnoreCase(transport);
            String adapterClass = rabbitMq
                    ? "io.github.actionguard.adapter.rabbitmq.config.RabbitMqActionExecutionAutoConfiguration"
                    : "io.github.actionguard.adapter.rocketmq.config.RocketMqActionExecutionAutoConfiguration";
            String clientClass = rabbitMq
                    ? "org.springframework.amqp.rabbit.core.RabbitTemplate"
                    : "org.apache.rocketmq.client.producer.DefaultMQProducer";
            String transportName = rabbitMq ? "RabbitTemplate" : "RocketMQ 客户端";
            if (!ClassUtils.isPresent(adapterClass, classLoader)) {
                throw new IllegalStateException("action.guard.execution.transport=" + transport
                        + " 需要引入 action-guard-adapter-" + transport.toLowerCase() + " 模块");
            }
            if (!ClassUtils.isPresent(clientClass, classLoader)) {
                throw new IllegalStateException("action.guard.execution.transport=" + transport + " 需要 " + transportName);
            }
            if (rabbitMq && beanFactory.getBeanNamesForType(ClassUtils.resolveClassName(clientClass, classLoader),
                    true, false).length == 0) {
                throw new IllegalStateException("action.guard.execution.transport=rabbitmq 需要配置 RabbitTemplate");
            }
            if (beanFactory.getBeanNamesForType(ClassUtils.resolveClassName(adapterClass, classLoader),
                    true, false).length == 0) {
                throw new IllegalStateException("action.guard.execution.transport=" + transport
                        + " 需要启用对应的执行适配器自动配置");
            }
        };
    }

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
                clock), actionOutboxRepository, actionExecutionMessageProducer,
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
    public ActionAlertOutboxRecorder actionAlertOutboxRecorder(
            ActionAlertOutboxRepository actionAlertOutboxRepository,
            Clock clock
    ) {
        return new ActionAlertOutboxRecorder(actionAlertOutboxRepository, clock);
    }

    @Bean
    public ActionObservabilityService actionObservabilityService(
            ActionAlertOutboxRecorder actionAlertOutboxRecorder,
            Optional<ActionMetricsRecorder> actionMetricsRecorder,
            Clock clock) {
        // 告警先在业务事务内落入独立 Outbox；指标仍是可选的提交后观测通道。
        return new ActionObservabilityService(actionAlertOutboxRecorder, actionMetricsRecorder, clock);
    }

    @Bean
    public ActionAlertOutboxRecoveryService actionAlertOutboxRecoveryService(
            ActionAlertOutboxRepository actionAlertOutboxRepository,
            Optional<ActionAlertSender> actionAlertSender,
            Clock clock,
            ActionGuardProperties properties
    ) {
        return new ActionAlertOutboxRecoveryService(
                actionAlertOutboxRepository,
                actionAlertSender,
                clock,
                properties.getAlertOutbox().getMaxDeliveryAttempts(),
                properties.getAlertOutbox().getRetryBackoff()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionAlertOutboxRecoveryScheduler actionAlertOutboxRecoveryScheduler(
            ActionAlertOutboxRecoveryService actionAlertOutboxRecoveryService,
            ActionGuardProperties properties
    ) {
        return new ActionAlertOutboxRecoveryScheduler(actionAlertOutboxRecoveryService, properties.getAlertOutbox());
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
            ActionObservabilityService actionObservabilityService,
            ActionGuardProperties properties) {
        // scheduler 只负责周期性触发恢复、补偿与卡住检测，不承载业务决策本身，避免把调度器做成另一套 runtime。
        return new ActionOutboxRecoveryScheduler(
                actionOutboxRecoveryService,
                actionCompensationService,
                actionStuckDetectionService,
                properties.getRecovery(),
                actionObservabilityService);
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
            Clock clock,
            Optional<PlatformTransactionManager> transactionManager) {
        if (transactionManager.isEmpty()
                && !(actionInstanceRepository instanceof InMemoryActionInstanceRepository
                        && actionStepInstanceRepository instanceof InMemoryActionStepInstanceRepository
                        && actionOutboxRepository instanceof InMemoryActionOutboxRepository
                        && actionTransitionLogRepository instanceof InMemoryActionTransitionLogRepository)) {
            throw new IllegalStateException("数据库执行结果落库需要配置 PlatformTransactionManager，并与全部结果仓储使用同一数据源");
        }
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
                clock,
                transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock actionGuardClock() {
        return Clock.systemUTC();
    }

    /**
     * 扫描配置的资源路径，并委托加载器将每个资源转换为一个 Action 定义。
     *
     * <p>例如，路径模式 {@code classpath*:actions/*.yml} 匹配到
     * {@code actions/order-paid.yml}，文件内容为：
     * <pre>{@code
     * name: order-paid
     * version: 1
     * description: 订单支付后发送通知
     * compensationEnabled: false
     * steps:
     *   - name: send-notification
     *     stepType: NOTIFY
     *     target: order-paid-notification
     *     maxRetryCount: 3
     *     retryBackoffMillis: 1000
     *     timeoutMillis: 5000
     * }</pre>
     * <p>使用默认的 YAML 加载器时，{@code loader.load(resource.getURL().toString())}
     * 返回的对象等价于：
     * <pre>{@code
     * new ActionDefinition(
     *         "order-paid", 1, "订单支付后发送通知", false,
     *         List.of(new ActionStepDefinition(
     *                 "send-notification", "NOTIFY", "order-paid-notification",
     *                 3, 1000L, 5000L
     *         ))
     * )
     * }</pre>
     * <p>YAML 顶层字段映射到 ActionDefinition，steps 中的每一项按顺序映射到
     * ActionStepDefinition；本方法将各文件的转换结果汇总到 definitions 列表中。
     */
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
