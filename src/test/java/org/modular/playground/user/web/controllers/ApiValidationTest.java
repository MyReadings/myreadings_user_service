package org.modular.playground.user.web.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import static io.restassured.RestAssured.given;

// Explicit disposable database only: test schema is recreated.
@QuarkusTest
@TestProfile(ApiValidationTest.DatabaseProfile.class)
@TestSecurity(user = "demo", roles = {"user", "admin"})
@EnabledIfEnvironmentVariable(named = "DEMO_TEST_DB_URL", matches = ".+")
public class ApiValidationTest {
    public static class DatabaseProfile implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                Map.entry("quarkus.devservices.enabled", "false"),
                Map.entry("quarkus.oidc.enabled", "false"),
                Map.entry("quarkus.otel.sdk.disabled", "true"),
                Map.entry("quarkus.http.test-port", "0"),
                Map.entry("mp.messaging.incoming.registrations.enabled", "false"),
                Map.entry("mp.messaging.outgoing.user-profile-created.enabled", "false"),
                Map.entry("quarkus.datasource.\"users-db\".username", "postgres"),
                Map.entry("quarkus.datasource.\"users-db\".password", "demo-test-only"),
                Map.entry("quarkus.datasource.\"users-db\".jdbc.url", System.getenv().getOrDefault("DEMO_TEST_DB_URL", "jdbc:postgresql://localhost:1/demo_test")),
                Map.entry("quarkus.hibernate-orm.\"users-db\".database.generation", "drop-and-create"));
        }
    }
    @Test
    void nullElementsAndOversizedBatchesAreRejected() {
        String oversized = "[" + String.join(",", java.util.Collections.nCopies(501, "\"00000000-0000-0000-0000-000000000001\"")) + "]";
        for (String path : new String[] {"/api/internal/users/batch"}) {
            for (String payload : new String[] {"null", "[null]", oversized}) {
                given().contentType("application/json").body(payload).post(path).then().statusCode(400).contentType("application/json");
            }
        }
    }    @Test
    @TestSecurity
    void internalReadsRejectAnonymousRequests() {
        given().contentType("application/json").body("[]").post("/api/internal/users/batch").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "outsider", roles = "unrelated")
    void internalReadsRejectUnrelatedRoles() {
        given().contentType("application/json").body("[]").post("/api/internal/users/batch").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "demo", roles = "user")
    void internalReadsAcceptExistingApplicationRole() {
        given().contentType("application/json").body("[]").post("/api/internal/users/batch").then().statusCode(200);
    }

}
