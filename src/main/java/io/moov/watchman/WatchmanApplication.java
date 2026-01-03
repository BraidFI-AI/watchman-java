package io.moov.watchman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WatchmanApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchmanApplication.class, args);
    }
}
