package br.com.unisenai.smartrent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartRentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartRentApplication.class, args);
    }
}
