package io.github.actionguard.demo.controller;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.ActionStepRequest;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.demo.dto.DemoActionStatusResponse;
import io.github.actionguard.demo.dto.DemoPublishRequest;
import io.github.actionguard.demo.dto.DemoPublishResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoPublishController {

    private final ActionPublisher actionPublisher;
    private final ActionInstanceRepository actionInstanceRepository;

    public DemoPublishController(
            ActionPublisher actionPublisher,
            ActionInstanceRepository actionInstanceRepository
    ) {
        this.actionPublisher = actionPublisher;
        this.actionInstanceRepository = actionInstanceRepository;
    }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.OK)
    public DemoPublishResponse publish(@RequestBody DemoPublishRequest request) {
        String actionName = StringUtils.hasText(request.actionName()) ? request.actionName() : "demo-notify-success";
        String bizKey = StringUtils.hasText(request.bizKey()) ? request.bizKey() : "order:curl-demo";
        String phoneNumber = StringUtils.hasText(request.phoneNumber()) ? request.phoneNumber() : "13800000000";

        actionPublisher.publish(new ActionRequest(
                actionName,
                bizKey,
                Map.of("operator", "curl-demo"),
                List.of(new ActionStepRequest(
                        "send-user-sms",
                        "NOTIFY_SMS_SEND",
                        "mock-sms",
                        Map.of(
                                "phoneNumbers", List.of(phoneNumber),
                                "templateId", "demo-notify",
                                "variables", Map.of("bizKey", bizKey)
                        )
                ))
        ));

        ActionInstance actionInstance = actionInstanceRepository.findByActionNameAndBizKey(actionName, bizKey)
                .orElseThrow(() -> new IllegalStateException("Published action instance not found"));
        return new DemoPublishResponse(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                actionInstance.status().name()
        );
    }

    @GetMapping("/actions/{actionInstanceId}")
    public DemoActionStatusResponse getAction(@PathVariable String actionInstanceId) {
        ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action instance not found: " + actionInstanceId));
        return new DemoActionStatusResponse(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                actionInstance.status().name()
        );
    }
}
