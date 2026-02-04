package io.kubechargeback.api.lifecycle;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

@ApplicationScoped
public class DatabaseInitializer {

    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class);

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Checking database schema...");
        LOGGER.info("Starting schema initialization check...");
        try (Connection conn = dataSource.getConnection();
             InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/schema.sql")) {
            
            if (is == null) {
                LOGGER.error("Schema script not found: db/schema.sql");
                return;
            }

            String sql = new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));

            try (Statement stmt = conn.createStatement()) {
                // SQLite allows multiple statements separated by ; in one execute call
                // but some drivers might prefer splitting them. 
                // For simplicity, we try to execute the whole block.
                for (String command : sql.split(";")) {
                    if (!command.trim().isEmpty()) {
                        stmt.execute(command);
                    }
                }
                LOGGER.info("Database schema initialized successfully.");

                // Check if we need to seed dummy data
                var rs = stmt.executeQuery("SELECT * FROM allocation_snapshots LIMIT 5");
                int count = 0;
                while (rs.next()) {
                    count++;
                    LOGGER.infof("Snapshot: %s, Start: %s, End: %s", rs.getString("id"), rs.getString("window_start"), rs.getString("window_end"));
                }
                LOGGER.infof("Found %d snapshots", count);
                
                if (count == 0) {
                    LOGGER.info("Seeding dummy data...");
                    stmt.execute("INSERT INTO allocation_snapshots (id, window_start, window_end, group_type, group_key, cpu_mcpu, mem_mib, cpu_cost_units, mem_cost_units, total_cost_units) VALUES " +
                            "('seed1', datetime('now', '-1 day'), datetime('now'), 'NAMESPACE', 'default', 1000, 1024, 1.5, 0.5, 2.0)," +
                            "('seed2', datetime('now', '-1 day'), datetime('now'), 'NAMESPACE', 'kube-system', 500, 512, 0.75, 0.25, 1.0)");
                    stmt.execute("INSERT INTO workload_inventory (snapshot_id, namespace, kind, name, labels_json, cpu_request_mcpu, mem_request_mib, compliance_status) VALUES " +
                            "('seed1', 'default', 'Deployment', 'nginx', '{}', 1000, 1024, 'OK')");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize database schema", e);
        }
    }
}
