package com.keyloop.docviewer.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DocumentController}.
 *
 * <p>Boots the full Spring context against an H2 database (test profile)
 * and a dynamically-ported WireMock server that stubs both external APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("DocumentController Integration")
class DocumentControllerIntegrationTest {

    /* static WireMockServer wireMock = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort()); */
    
    static WireMockServer wireMock;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    /* @BeforeAll
    static void startWireMock() {
        wireMock.start();
    } */

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureExternalUrls(DynamicPropertyRegistry registry) {
        registry.add("external.sales-api.base-url",
                () -> "http://localhost:" + wireMock.port() + "/sales");
        registry.add("external.service-api.base-url",
                () -> "http://localhost:" + wireMock.port() + "/service");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static final String VIN = "1HGCM82633A123456";

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void stubSalesSuccess() {
        wireMock.stubFor(get(urlPathEqualTo("/sales/api/documents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "documents": [
                                {"id":"s1","title":"Purchase Agreement","documentType":"CONTRACT",
                                 "date":"2024-01-15T10:30:00Z","downloadUrl":"https://sales.example.com/s1"}
                              ]
                            }
                        """)));
    }

    private void stubServiceSuccess() {
        wireMock.stubFor(get(urlPathEqualTo("/service/api/documents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "records": [
                                {"recordId":"sv1","description":"Oil Change","category":"MAINTENANCE",
                                 "serviceDate":"2024-06-20T09:00:00Z","documentUrl":"https://service.example.com/sv1"}
                              ]
                            }
                        """)));
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/documents returns 200 with aggregated documents")
    void shouldReturn200WithAggregatedDocuments() {
        stubSalesSuccess();
        stubServiceSuccess();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/v1/documents?vin=" + VIN), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("documents");
        assertThat(response.getBody().get("totalCount")).isEqualTo(2);
        assertThat(response.getBody().get("vin")).isEqualTo(VIN);
    }

    @Test
    @DisplayName("GET /api/v1/documents returns 400 for invalid VIN")
    void shouldReturn400ForInvalidVin() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/v1/documents?vin=INVALID"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/documents returns 400 when VIN is missing")
    void shouldReturn400WhenVinIsMissing() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            url("/api/v1/documents"), Map.class);

        System.out.println("Actual HTTP status: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/documents returns 200 partial result when Sales API fails")
    void shouldReturn200WithPartialResultWhenSalesApiFails() {
        wireMock.stubFor(get(urlPathEqualTo("/sales/api/documents"))
                .willReturn(aResponse().withStatus(500)));
        stubServiceSuccess();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/v1/documents?vin=" + VIN), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalCount")).isEqualTo(1);

        var sources = (Map<?, ?>) response.getBody().get("sources");
        assertThat(((Map<?, ?>) sources.get("SALES")).get("status")).isEqualTo("ERROR");
        assertThat(((Map<?, ?>) sources.get("SERVICE")).get("status")).isEqualTo("OK");
    }

    @Test
    @DisplayName("GET /api/v1/documents returns 200 partial result when Service API fails")
    void shouldReturn200WithPartialResultWhenServiceApiFails() {
        stubSalesSuccess();
        wireMock.stubFor(get(urlPathEqualTo("/service/api/documents"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/v1/documents?vin=" + VIN), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalCount")).isEqualTo(1);

        var sources = (Map<?, ?>) response.getBody().get("sources");
        assertThat(((Map<?, ?>) sources.get("SALES")).get("status")).isEqualTo("OK");
        assertThat(((Map<?, ?>) sources.get("SERVICE")).get("status")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("GET /api/v1/documents returns 200 with empty list when both APIs fail")
    void shouldReturn200EmptyWhenBothFail() {
        wireMock.stubFor(get(urlPathEqualTo("/sales/api/documents"))
                .willReturn(aResponse().withStatus(500)));
        wireMock.stubFor(get(urlPathEqualTo("/service/api/documents"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/v1/documents?vin=" + VIN), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("Swagger UI is accessible at /swagger-ui/index.html")
    void swaggerUiShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/swagger-ui/index.html"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

