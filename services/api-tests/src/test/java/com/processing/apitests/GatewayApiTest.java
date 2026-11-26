package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class GatewayApiTest extends ApiTestSupport {

    @Test
    void health_returnsGatewayAndDownstreamStatuses() {
        given()
                .spec(GATEWAY)
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("gateway"))
                .body("version", equalTo("1.0.0"))
                .body("services.switch", equalTo("ok"))
                .body("services.authorization", equalTo("ok"))
                .body("services.cardManagement", equalTo("ok"))
                .body("services.logger", equalTo("ok"));
    }
}
