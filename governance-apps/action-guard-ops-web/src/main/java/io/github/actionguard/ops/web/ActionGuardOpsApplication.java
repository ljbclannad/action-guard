package io.github.actionguard.ops.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.actionguard")
public class ActionGuardOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActionGuardOpsApplication.class, args);
    }
}
