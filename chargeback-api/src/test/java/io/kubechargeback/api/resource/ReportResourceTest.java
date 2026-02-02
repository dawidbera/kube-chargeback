package io.kubechargeback.api.resource;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
public class ReportResourceTest {

    @Inject
    AgroalDataSource dataSource;

    private static final String START = "2026-02-01T10:00:00Z";
    private static final String END = "2026-02-01T11:00:00Z";

    @BeforeEach
    void setup() throws Exception {
        // Czyścimy bazę przed każdym testem
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DELETE FROM allocation_snapshots");
            conn.createStatement().execute("DELETE FROM workload_inventory");

            // Wstawiamy dane testowe: Snapshot dla aplikacji 'payments' zespołu 'team-a'
            String appSnapId = START + "_APP_payments";
            insertSnapshot(conn, appSnapId, START, END, "APP", "payments", 1000, 2048, 1.0, 0.2, 1.2);
            insertInventory(conn, appSnapId, "test-ns", "Deployment", "payments", "{\"team\":\"team-a\",\"app\":\"payments\"}", 1000, 2048, "OK");

            // Wstawiamy dane testowe: Snapshot dla aplikacji 'auth' zespołu 'team-b'
            String appSnapId2 = START + "_APP_auth";
            insertSnapshot(conn, appSnapId2, START, END, "APP", "auth", 500, 1024, 0.5, 0.1, 0.6);
            insertInventory(conn, appSnapId2, "test-ns", "Deployment", "auth", "{\"team\":\"team-b\",\"app\":\"auth\"}", 500, 1024, "MISSING_LIMITS");
            
            // Snapshot typu TEAM
            insertSnapshot(conn, UUID.randomUUID().toString(), START, END, "TEAM", "team-a", 1000, 2048, 1.0, 0.2, 1.2);
        }
    }

    private void insertSnapshot(Connection conn, String id, String start, String end, String type, String key, long cpu, long mem, double cCost, double mCost, double tCost) throws Exception {
        String sql = "INSERT INTO allocation_snapshots (id, window_start, window_end, group_type, group_key, cpu_mcpu, mem_mib, cpu_cost_units, mem_cost_units, total_cost_units) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, start);
            ps.setString(3, end);
            ps.setString(4, type);
            ps.setString(5, key);
            ps.setLong(6, cpu);
            ps.setLong(7, mem);
            ps.setDouble(8, cCost);
            ps.setDouble(9, mCost);
            ps.setDouble(10, tCost);
            ps.executeUpdate();
        }
    }

    private void insertInventory(Connection conn, String snapId, String ns, String kind, String name, String labels, long cpu, long mem, String status) throws Exception {
        String sql = "INSERT INTO workload_inventory (snapshot_id, namespace, kind, name, labels_json, cpu_request_mcpu, mem_request_mib, compliance_status) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, snapId);
            ps.setString(2, ns);
            ps.setString(3, kind);
            ps.setString(4, name);
            ps.setString(5, labels);
            ps.setLong(6, cpu);
            ps.setLong(7, mem);
            ps.setString(8, status);
            ps.executeUpdate();
        }
    }

    @Test
    public void testAllocationsReport() {
        given()
          .queryParam("from", START)
          .queryParam("to", END)
          .queryParam("groupBy", "team")
          .when().get("/api/v1/reports/allocations")
          .then()
             .statusCode(200)
             .body("size()", is(1))
             .body("[0].groupKey", is("team-a"));
    }

    @Test
    public void testTopAppsReport_WithTeamFilter() {
        // Szukamy top apps dla team-a. Powinno zwrócić tylko 'payments'.
        given()
          .queryParam("from", START)
          .queryParam("to", END)
          .queryParam("team", "team-a")
          .when().get("/api/v1/reports/top-apps")
          .then()
             .statusCode(200)
             .body("size()", is(1))
             .body("[0].groupKey", is("payments"));
    }

    @Test
    public void testTopAppsReport_Global() {
        // Bez filtra team. Powinno zwrócić obie aplikacje.
        given()
          .queryParam("from", START)
          .queryParam("to", END)
          .when().get("/api/v1/reports/top-apps")
          .then()
             .statusCode(200)
             .body("size()", is(2));
    }

    @Test
    public void testComplianceReport() {
        given()
          .queryParam("from", START)
          .queryParam("to", END)
          .when().get("/api/v1/reports/compliance")
          .then()
             .statusCode(200)
             .body("summary.ok", is(1))
             .body("summary.missingLimits", is(1))
             .body("items", hasSize(2));
    }
}
