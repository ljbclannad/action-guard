# Contributing

## Goal

欢迎一起完善 `action-guard`。

这个项目当前重点是把“可靠发布 + 串行步骤编排 + 治理可见性”打磨成更稳定、更容易接入的开源组件，所以提 issue 或 PR 时，优先围绕：

- 接入体验
- 状态一致性
- 扩展点稳定性
- 文档可读性
- 测试覆盖

## Before You Start

建议先阅读：

1. [README.md](/Users/lejinbo/LLM/action-guard/README.md)
2. [Architecture](/Users/lejinbo/LLM/action-guard/docs/architecture.md)
3. [Quick Start](/Users/lejinbo/LLM/action-guard/docs/quick-start.md)
4. [Observability](/Users/lejinbo/LLM/action-guard/docs/observability.md)
5. [Compatibility And Versioning](/Users/lejinbo/LLM/action-guard/docs/compatibility-and-versioning.md)
6. [Public Release Readiness](/Users/lejinbo/LLM/action-guard/docs/public-release-readiness.md)
7. [Release Discipline](/Users/lejinbo/LLM/action-guard/docs/release-discipline.md)

## Development Principles

提交改动前，请尽量遵守这些原则：

- 优先保持最小接入路径简单
- 不要打穿能力层 / Publish-Outbox 层 / 消息执行层 / 治理层边界
- 对外 API、SPI 和 YAML 字段要谨慎改动
- 新功能尽量带上最小测试和文档
- 默认演示路径优先保证 H2 + RabbitMQ 可运行

## Typical Workflow

1. Fork 仓库并创建功能分支
2. 补充或修改代码
3. 添加或更新测试
4. 更新相关文档
5. 运行本地验证
6. 提交 PR，并说明变更动机、行为变化和验证方式

## Suggested Local Checks

最少建议运行：

```bash
mvn test
```

如果改动涉及 demo 或文档默认链路，建议再运行：

```bash
bash scripts/run-demo-smoke.sh
```

如果改动涉及恢复、并发或稳定性路径，建议再运行：

```bash
bash scripts/run-demo-stability.sh
```

## Pull Request Checklist

- [ ] 变更目标清晰
- [ ] 没有引入与任务无关的重构
- [ ] 新增或修改行为有测试覆盖
- [ ] 相关文档已同步
- [ ] 如果有默认行为变化，已明确写出影响
- [ ] 如果有兼容性风险，已写出迁移建议

## Issues And Design Discussion

如果你准备提交较大的改动，建议先说明：

- 想解决的问题
- 预期使用场景
- 是否涉及 API / SPI / schema / definition 兼容性
- 是否影响默认演示路径

这样更容易在早期就对齐方向，避免后面返工。

如果问题涉及潜在安全风险，请优先参考：

- [SECURITY.md](/Users/lejinbo/LLM/action-guard/SECURITY.md)
