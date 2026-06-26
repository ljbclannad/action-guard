# Action Guard Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the current `message-guard` scaffold into an `action-guard` scaffold for configurable outbox-backed async actions with serial step orchestration.

**Architecture:** Rename the project and modules around `action-guard`, move the public model from message consumption to action publication and step execution, and keep the first slice thin: explicit `publish(...)`, YAML-backed action definitions, serial step model, and ops/demo placeholders. Use test-first changes in the core module to drive the new action runtime types and loader behavior.

**Tech Stack:** Java 17, Maven, Spring Boot 3.2.x, Spring Framework 6, SnakeYAML via Spring Boot YAML support, JUnit 5, AssertJ.

---

### Task 1: Rename repository metadata and module coordinates

**Files:**
- Modify: `/Users/lejinbo/LLM/message-guard/pom.xml`
- Modify: `/Users/lejinbo/LLM/message-guard/README.md`
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-*/pom.xml`

- [ ] **Step 1: Rename artifact and module coordinates**

Update parent `artifactId`, internal dependency coordinates, and displayed project naming from `message-guard` to `action-guard`.

- [ ] **Step 2: Keep directories stable for now**

Do not rename filesystem module directories in this slice. Change Maven coordinates and docs first so the project remains buildable during the domain migration.

### Task 2: Drive new core model with failing tests

**Files:**
- Create: `/Users/lejinbo/LLM/message-guard/message-guard-core/src/test/java/io/github/actionguard/core/model/ActionStatusTest.java`
- Create: `/Users/lejinbo/LLM/message-guard/message-guard-core/src/test/java/io/github/actionguard/core/runtime/ActionDefinitionLoaderTest.java`
- Create: `/Users/lejinbo/LLM/message-guard/message-guard-core/src/test/resources/actions/order-cancel.yml`

- [ ] **Step 1: Write the failing status test**

Add a test that expects `ActionStatus.SUCCESS` and `ActionStatus.IGNORED` to be terminal and `ActionStatus.DISPATCHING` to be non-terminal.

- [ ] **Step 2: Write the failing YAML definition loader test**

Add a test that loads `actions/order-cancel.yml` and expects an `ActionDefinition` named `order-cancel-flow` with two serial steps, including `SMS` as the second `stepType`.

- [ ] **Step 3: Run targeted tests to verify failure**

Run: `mvn -q -f /Users/lejinbo/LLM/message-guard/pom.xml -pl message-guard-core test`
Expected: FAIL because `ActionStatus`, `ActionDefinition`, and the loader classes do not exist yet.

### Task 3: Implement minimal action runtime model

**Files:**
- Create: `/Users/lejinbo/LLM/message-guard/message-guard-api/src/main/java/io/github/actionguard/api/...`
- Create: `/Users/lejinbo/LLM/message-guard/message-guard-core/src/main/java/io/github/actionguard/core/...`
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-core/pom.xml`

- [ ] **Step 1: Add the minimal public API**

Implement `ActionRequest`, `ActionPublisher`, `ActionDefinition`, `ActionStepDefinition`, `ActionType`, `StepType`, and action-facing SPI contracts.

- [ ] **Step 2: Add the minimal core runtime**

Implement `ActionStatus`, `ActionDefinitionLoader`, `YamlActionDefinitionLoader`, and any supporting records needed for the YAML test.

- [ ] **Step 3: Add YAML parsing dependency**

Use Spring Boot managed YAML support or SnakeYAML directly in `message-guard-core` so the loader can parse the test resource.

- [ ] **Step 4: Re-run the core tests**

Run: `mvn -q -f /Users/lejinbo/LLM/message-guard/pom.xml -pl message-guard-core test`
Expected: PASS

### Task 4: Repoint starter, ops, and demo to the action model

**Files:**
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-spring-boot-starter/src/main/java/**`
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-ops-api/src/main/java/**`
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-ops-web/src/main/java/**`
- Modify: `/Users/lejinbo/LLM/message-guard/message-guard-demo/src/main/java/**`

- [ ] **Step 1: Replace message-guard package names with action-guard**

Move sample classes and auto-configuration into `io.github.actionguard`.

- [ ] **Step 2: Replace consume-record placeholders with action-instance placeholders**

Update ops DTO/controller names and sample responses to action-centric terms.

- [ ] **Step 3: Update the demo to call the new API shape**

Add a sample component that illustrates explicit `publish(...)` for an action request.

### Task 5: Verify the upgraded scaffold

**Files:**
- Test: `/Users/lejinbo/LLM/message-guard/message-guard-core/src/test/java/**`
- Test: `/Users/lejinbo/LLM/message-guard/message-guard-ops-web/src/test/java/**`

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q -f /Users/lejinbo/LLM/message-guard/pom.xml test`
Expected: PASS

- [ ] **Step 2: Run the package build**

Run: `mvn -q -f /Users/lejinbo/LLM/message-guard/pom.xml -DskipTests package`
Expected: PASS

- [ ] **Step 3: Summarize next slice**

Identify the next implementation slice as outbox persistence, dispatcher polling, and YAML action registry wiring.
