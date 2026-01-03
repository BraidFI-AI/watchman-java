package io.moov.watchman;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic Spring Boot application context tests.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Watchman Application Context Tests")
class WatchmanApplicationTests {

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        // Context loading is verified by Spring Boot test framework
        assertThat(true).isTrue();
    }
}
