package io.github.actionguard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@EnableRabbit
@SpringBootApplication(scanBasePackages = "io.github.actionguard")
public class ActionGuardDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActionGuardDemoApplication.class, args);
    }
}
