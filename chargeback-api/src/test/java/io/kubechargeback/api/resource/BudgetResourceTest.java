package io.kubechargeback.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class BudgetResourceTest {

    @Test
    public void testCreateBudget_Valid() {
        String budgetJson = """
                {
                  "name": "team-a-daily",
                  "selectorType": "TEAM",
                  "selectorValue": "team-a",
                  "period": "DAILY",
                  "cpuMcpuLimit": 10000,
                  "memMibLimit": 20480
                }
                """;

        given()
          .contentType(ContentType.JSON)
          .body(budgetJson)
          .when().post("/api/v1/budgets")
          .then()
             .statusCode(201)
             .body("id", notNullValue())
             .body("name", is("team-a-daily"));
    }

    @Test
    public void testCreateBudget_InvalidType() {
        String budgetJson = """
                {
                  "name": "label-budget",
                  "selectorType": "LABEL",
                  "selectorValue": "some-label",
                  "period": "DAILY",
                  "cpuMcpuLimit": 10000,
                  "memMibLimit": 20480
                }
                """;

        given()
          .contentType(ContentType.JSON)
          .body(budgetJson)
          .when().post("/api/v1/budgets")
          .then()
             .statusCode(400)
             .body("error", is("selectorType LABEL is not supported in MVP"));
    }

    @Test
    public void testCreateBudget_MissingFields() {
        String budgetJson = "{\"name\": \"incomplete\"}";

        given()
          .contentType(ContentType.JSON)
          .body(budgetJson)
          .when().post("/api/v1/budgets")
          .then()
             .statusCode(400)
             .body("error", is("Missing required fields"));
    }

    @Test
    public void testListBudgets() {
        given()
          .when().get("/api/v1/budgets")
          .then()
             .statusCode(200)
             .body("size()", is(notNullValue()));
    }
}
