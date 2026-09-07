package io.github.actionguard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;

@EnableRabbit
@SpringBootApplication
@ComponentScan(basePackages = "io.github.actionguard", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.github\\.actionguard\\.ops\\.api\\..*")
})
public class ActionGuardDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActionGuardDemoApplication.class, args);
    }
}
