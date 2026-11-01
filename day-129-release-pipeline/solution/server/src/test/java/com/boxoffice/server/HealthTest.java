package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The health checks, with the details shown as on your laptop. */
@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HealthTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthSaysUpAndWhatsInside() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("UP"))
                .hasPathSatisfying("$.components.db.status", status -> assertThat(status).isEqualTo("UP"))
                .hasPathSatisfying("$.components.payments.details.mode", mode -> assertThat(mode).isEqualTo("stub"));
    }

    @Test
    void livenessAndReadinessAreSeparate() {
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatusOk()
                .bodyJson().hasPathSatisfying("$.components.db.status", status -> assertThat(status).isEqualTo("UP"));
    }

    @Test
    void infoSaysWhichVersionIsRunning() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatusOk()
                .bodyJson().extractingPath("$.build.version").isEqualTo(System.getProperty("boxoffice.version"));
    }

    @Test
    void nothingElseUnderActuatorIsOpen() {
        // /actuator/env would show every setting, secrets too.
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/actuator/beans")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
