package com.boxoffice.server;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The sync server. Spring Boot finds every @Component, @Service and
 * @RestController in this package, creates them, and hands each one what
 * its constructor asks for.
 *
 * <pre>
 * BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar server/target/boxoffice-server.jar
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
public class SyncServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncServerApplication.class, args);
    }

    /** The real clock, for anything that asks for a Clock. Tests could swap in a fixed one. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
