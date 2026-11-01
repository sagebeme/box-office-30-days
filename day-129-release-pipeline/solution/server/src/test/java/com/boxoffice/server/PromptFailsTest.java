package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.server.mpesa.Daraja;
import com.boxoffice.server.mpesa.DarajaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** M-Pesa can't be reached: the fan must be told, not left waiting for a prompt that never comes. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PromptFailsTest {

    // No apostrophes: Thymeleaf writes ' as &#39; in the page.
    static final String DOWN = "M-Pesa is not answering. Try again in a minute";

    @TestConfiguration
    static class SafaricomIsDown {
        @Bean
        @Primary
        Daraja safaricomIsDown() {
            return request -> {
                throw new DarajaException(DOWN);
            };
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void empty() {
        ServerTestSupport.emptyTheServer(db);
    }

    @Test
    void withoutJavaScriptTheProblemSurvivesTheRedirect() {
        MvcResult posted = mvc.post().uri("/events/kilele-2026/orders")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "1").param("buyer", "Wanjiku").param("phone", "0712 345 678")
                .exchange().getMvcResult();
        assertThat(posted.getFlashMap().get("payProblem")).isEqualTo(DOWN);

        assertThat(mvc.get().uri(posted.getResponse().getRedirectedUrl()).flashAttrs(posted.getFlashMap()))
                .bodyText()
                .contains(DOWN)
                .doesNotContain("Check your phone");
    }

    @Test
    void withHtmxTheProblemIsInTheAnswer() {
        assertThat(mvc.post().uri("/events/kilele-2026/orders").header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "1").param("buyer", "Wanjiku").param("phone", "0712 345 678"))
                .bodyText()
                .contains(DOWN)
                .doesNotContain("Check your phone");
    }
}
