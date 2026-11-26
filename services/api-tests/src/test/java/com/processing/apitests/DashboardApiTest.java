package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class DashboardApiTest extends ApiTestSupport {

    @Test
    void getStats_returnsAggregateContract() {
        given()
                .spec(GATEWAY)
                .when()
                .get("/api/dashboard/stats")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("totalTransactions", greaterThanOrEqualTo(0))
                .body("approvedCount", greaterThanOrEqualTo(0))
                .body("declinedCount", greaterThanOrEqualTo(0))
                .body("approvalRate", greaterThanOrEqualTo(0.0f))
                .body("approvalRate", lessThanOrEqualTo(1.0f))
                .body("totalAmount", notNullValue())
                .body("averageAmount", notNullValue());
    }

    @Test
    void getRecent_returnsAtMostRequestedLimit() {
        given()
                .spec(GATEWAY)
                .queryParam("limit", 5)
                .when()
                .get("/api/dashboard/recent")
                .then()
                .statusCode(200)
                .body("size()", lessThanOrEqualTo(5));
    }

    @Test
    void getRecent_invalidLimit_returns400() {
        given()
                .spec(GATEWAY)
                .queryParam("limit", 501)
                .when()
                .get("/api/dashboard/recent")
                .then()
                .statusCode(400)
                .body("message", org.hamcrest.Matchers.containsString("limit"));
    }

    @Test
    void getCharts_acceptsHourAndDayGranularity() {
        given()
                .spec(GATEWAY)
                .queryParam("granularity", "hour")
                .when()
                .get("/api/dashboard/charts")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));

        given()
                .spec(GATEWAY)
                .queryParam("granularity", "day")
                .when()
                .get("/api/dashboard/charts")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    void getCharts_invalidGranularity_returns400() {
        given()
                .spec(GATEWAY)
                .queryParam("granularity", "minute")
                .when()
                .get("/api/dashboard/charts")
                .then()
                .statusCode(400)
                .body("message", org.hamcrest.Matchers.containsString("granularity"))
                .body("serviceName", equalTo("transaction-logger"));
    }
}
