package com.processing.apitests;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;

abstract class ApiTestSupport {
    protected static final RequestSpecification GATEWAY = jsonSpec("GATEWAY_URL", "http://localhost:8080");
    protected static final RequestSpecification SWITCH_SERVICE = jsonSpec("SWITCH_URL", "http://localhost:8082");
    protected static final RequestSpecification AUTH_SERVICE = jsonSpec("AUTH_URL", "http://localhost:8083");
    protected static final RequestSpecification LOGGER_SERVICE = jsonSpec("LOGGER_URL", "http://localhost:8088");
    protected static final RequestSpecification MERCHANT_SERVICE = jsonSpec("MERCHANT_URL", "http://localhost:8084");

    private static RequestSpecification jsonSpec(String key, String defaultUri) {
        String uri = System.getProperty(key, System.getenv().getOrDefault(key, defaultUri));
        return new RequestSpecBuilder()
                .setBaseUri(uri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    protected static TestCard createCard() {
        return createCard(new BigDecimal("100000000"), new BigDecimal("15000000"),
                new BigDecimal("300000000"));
    }

    protected static TestCard createCard(BigDecimal balance, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("bin", "400000");
        request.put("cardholderName", "API TEST");
        request.put("currencyCode", "643");
        request.put("dailyLimit", dailyLimit);
        request.put("monthlyLimit", monthlyLimit);
        request.put("initialBalance", balance);

        var response = given()
                .spec(GATEWAY)
                .body(request)
                .when()
                .post("/api/cards")
                .then()
                .statusCode(201)
                .extract()
                .response();

        return new TestCard(response.path("pan"));
    }

    protected static Map<String, Object> authorizationRequest(String pan, BigDecimal amount) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("mti", "0100");
        request.put("stan", stan());
        request.put("pan", pan);
        request.put("processingCode", "000000");
        request.put("amount", amount);
        request.put("currencyCode", "643");
        request.put("transmissionDateTime", Instant.now().toString());
        request.put("terminalId", "TERM0001");
        request.put("terminalType", "POS");
        request.put("merchantId", "MERCH0000000001");
        request.put("mcc", "5411");
        request.put("acquirerId", "ACQ001");
        return request;
    }

    protected static Map<String, Object> internalAuthorizationRequest(String pan, BigDecimal amount) {
        Map<String, Object> request = authorizationRequest(pan, amount);
        request.put("issuerId", "ISS001");
        return request;
    }

    protected static Map<String, Object> loggerRequest(String pan, UUID id, String stan, BigDecimal amount) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", id.toString());
        request.put("mti", "0100");
        request.put("stan", stan);
        request.put("rrn", rrn());
        request.put("pan", pan);
        request.put("processingCode", "000000");
        request.put("amount", amount);
        request.put("currencyCode", "643");
        request.put("terminalId", "TERM0001");
        request.put("terminalType", "POS");
        request.put("merchantId", "MERCH0000000001");
        request.put("mcc", "5411");
        request.put("acquirerId", "ACQ001");
        request.put("issuerId", "ISS001");
        request.put("acquiringFee", BigDecimal.ZERO);
        request.put("status", "APPROVED");
        request.put("declineReason", null);
        request.put("authCode", "ABC123");
        request.put("processingTimeMs", 15);
        request.put("transmissionDateTime", Instant.now().toString());
        request.put("createdAt", Instant.now().toString());
        return request;
    }

    protected static String stan() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    protected static String rrn() {
        return String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    protected static String unknownPan() {
        return String.format("400000%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    protected static String unknownBinPan() {
        return String.format("499999%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    protected static void awaitTransaction(String pan) {
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> given()
                        .spec(GATEWAY)
                        .queryParam("pan", pan)
                        .get("/api/transactions/search")
                        .then()
                        .statusCode(200)
                        .body("transactions", hasSize(1)));
    }

    protected static void awaitNoTransaction(String pan) {
        await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> given()
                        .spec(GATEWAY)
                        .queryParam("pan", pan)
                        .get("/api/transactions/search")
                        .then()
                        .statusCode(200)
                        .body("transactions", hasSize(0)));
    }

    protected record TestCard(String pan) {
    }
}
