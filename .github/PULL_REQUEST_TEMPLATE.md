## Summary

请简要说明这次变更做了什么，以及为什么要做。

## Change Type

- [ ] Bug fix
- [ ] Feature
- [ ] Refactor
- [ ] Documentation
- [ ] Test only
- [ ] Build / release / dependency

## Scope

- [ ] Publish / Outbox
- [ ] RabbitMQ / message execution
- [ ] Governance API
- [ ] Notify / IM / Webhook capability
- [ ] Docs / examples
- [ ] Other

## Behavior Change

请说明这次改动对使用方的实际影响：

- 默认行为是否变化
- 是否影响 API / SPI / YAML / schema
- 是否影响 demo 或推荐主链路

## Verification

请写出你实际运行过的验证命令，例如：

```bash
mvn test
```

或：

```bash
bash scripts/run-demo-smoke.sh
```

## Checklist

- [ ] 变更目标清晰，且没有引入无关重构
- [ ] 测试或验证方式已补充
- [ ] 相关文档已同步
- [ ] 如果有兼容性风险，已在描述中写清
- [ ] 如果影响默认路径，已说明迁移或使用建议
