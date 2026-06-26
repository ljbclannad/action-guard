package io.github.actionguard.ops.api;

import io.github.actionguard.core.model.ActionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/actions")
public class ActionInstanceController {

    @GetMapping
    public List<ActionInstanceView> list() {
        return List.of(new ActionInstanceView("order-cancel-flow", "order:1", ActionStatus.SUCCESS, 2));
    }
}
