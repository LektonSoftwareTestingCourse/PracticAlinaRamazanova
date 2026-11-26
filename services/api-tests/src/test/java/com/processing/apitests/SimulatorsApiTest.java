package com.processing.apitests;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class SimulatorsApiTest extends ApiTestSupport {

    @Test
    void runTerminalSimulator_returnsTransactionSummary() {
        createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of("count", 1, "scenario", "normal", "tps", 1))
                .when()
                .post("/api/simulator/terminal/run")
                .then()
                .statusCode(200)
                .body("totalSubmitted", equalTo(1))
                .body("transactions", hasSize(1))
                .body("approved", greaterThanOrEqualTo(0))
                .body("declined", greaterThanOrEqualTo(0));
    }

    @Test
    void runTerminalSimulator_invalidCount_returns400() {
        given()
                .spec(GATEWAY)
                .body(Map.of("count", 0, "scenario", "normal", "tps", 1))
                .when()
                .post("/api/simulator/terminal/run")
                .then()
                .statusCode(400)
                .body("message", notNullValue())
                .body("serviceName", equalTo("terminal-simulator"));
    }

    @Test
    void terminalContinuousMode_canStartAndStop() {
        createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of("tps", 1, "transactionType", "NORMAL"))
                .when()
                .post("/api/simulator/terminal/start-continuous")
                .then()
                .statusCode(200);

        given()
                .spec(GATEWAY)
                .when()
                .post("/api/simulator/terminal/stop")
                .then()
                .statusCode(200);
    }

    @Test
    void runMerchantSimulator_returnsTransactionSummary() {
        createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of(
                        "count", 1,
                        "mccCodes", List.of("5411"),
                        "scenario", "grocery",
                        "tps", 1
                ))
                .when()
                .post("/api/simulator/merchant/run")
                .then()
                .statusCode(200)
                .body("totalSubmitted", equalTo(1))
                .body("transactions", hasSize(1))
                .body("approved", greaterThanOrEqualTo(0))
                .body("declined", greaterThanOrEqualTo(0));
    }

    @Test
    void getMerchants_returnsSeededMerchants() {
        given()
                .spec(MERCHANT_SERVICE)
                .when()
                .get("/api/simulator/merchants")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].id", notNullValue());
    }

    @Test
    void getMerchantFee_invalidRequest_returns400() {
        given()
                .spec(GATEWAY)
                .body(Map.of(
                        "transmissionDateTime", Instant.now().toString(),
                        "pan", "",
                        "stan", stan(),
                        "terminalId", "TERM0001"
                ))
                .when()
                .post("/api/simulator/merchant/fee")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid request"));
    }

    @Test
    void getMerchantFee_unknownTransaction_returns404() {
        TestCard card = createCard();

        given()
                .spec(GATEWAY)
                .body(Map.of(
                        "transmissionDateTime", Instant.now().toString(),
                        "pan", card.pan(),
                        "stan", stan(),
                        "terminalId", "TERM0001",
                        "amount", new BigDecimal("100")
                ))
                .when()
                .post("/api/simulator/merchant/fee")
                .then()
                .statusCode(404)
                .body("error", equalTo("Resource not found"));
    }
}
