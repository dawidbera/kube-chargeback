package io.kubechargeback.collector;

import io.agroal.api.AgroalDataSource;
import io.kubechargeback.common.model.AllocationSnapshot;
import io.kubechargeback.common.model.Budget;
import io.kubechargeback.common.model.WorkloadInventory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CollectorRepository {

    @Inject
    AgroalDataSource dataSource;

    public void initDb() {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (is == null) return;
            String schema = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String sql : schema.split(";")) {
                    if (!sql.trim().isEmpty()) {
                        stmt.execute(sql);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init DB", e);
        }
    }

    /**
     * Saves an allocation snapshot to the database.
     *
     * @param s the snapshot to save
     */
    public void saveSnapshot(AllocationSnapshot s) {
        // Use INSERT OR REPLACE to handle potential duplicate IDs or window overlaps
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO allocation_snapshots (id, window_start, window_end, group_type, group_key, " +
                             "cpu_mcpu, mem_mib, cpu_cost_units, mem_cost_units, total_cost_units) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getWindowStart().toString());
            ps.setString(3, s.getWindowEnd().toString());
            ps.setString(4, s.getGroupType());
            ps.setString(5, s.getGroupKey());
            ps.setLong(6, s.getCpuMcpu());
            ps.setLong(7, s.getMemMib());
            ps.setDouble(8, s.getCpuCostUnits());
            ps.setDouble(9, s.getMemCostUnits());
            ps.setDouble(10, s.getTotalCostUnits());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves a workload inventory record to the database.
     *
     * @param w the workload inventory to save
     */
    public void saveInventory(WorkloadInventory w) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO workload_inventory (snapshot_id, namespace, kind, name, labels_json, " +
                             "cpu_request_mcpu, mem_request_mib, compliance_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, w.getSnapshotId());
            ps.setString(2, w.getNamespace());
            ps.setString(3, w.getKind());
            ps.setString(4, w.getName());
            ps.setString(5, w.getLabelsJson());
            ps.setLong(6, w.getCpuRequestMcpu());
            ps.setLong(7, w.getMemRequestMib());
            ps.setString(8, w.getComplianceStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves all enabled budgets from the database.
     *
     * @return a list of enabled budgets
     */
    public List<Budget> findAllEnabledBudgets() {
        List<Budget> budgets = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM budgets WHERE enabled = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    budgets.add(mapBudget(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return budgets;
    }

    /**
     * Calculates the usage for a specific budget within a time range.
     *
     * @param b     the budget
     * @param start the start time
     * @param end   the end time
     * @return an AllocationSnapshot containing the aggregated usage
     */
    public AllocationSnapshot getUsageForBudget(Budget b, Instant start, Instant end) {
        // Sum snapshots for the budget's selector
        // SelectorType is TEAM or NAMESPACE.
        // GroupType in snapshots matches SelectorType.
        // GroupKey matches SelectorValue.
        
        String sql = "SELECT SUM(cpu_mcpu) as cpu, SUM(mem_mib) as mem, SUM(total_cost_units) as cost " +
                     "FROM allocation_snapshots " +
                     "WHERE group_type = ? AND group_key = ? AND window_start >= ? AND window_end <= ?";
                     
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.getSelectorType()); // TEAM or NAMESPACE
            ps.setString(2, b.getSelectorValue());
            ps.setString(3, start.toString());
            ps.setString(4, end.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                AllocationSnapshot res = new AllocationSnapshot();
                if (rs.next()) {
                    res.setCpuMcpu(rs.getLong("cpu"));
                    res.setMemMib(rs.getLong("mem"));
                    res.setTotalCostUnits(rs.getDouble("cost"));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Finds the applications with the highest cost within a given budget's scope and time range.
     *
     * @param b     the budget to analyze
     * @param start the start of the period
     * @param end   the end of the period
     * @param limit the maximum number of results to return
     * @return a list of top application snapshots
     */
    public List<AllocationSnapshot> getTopOffenders(Budget b, Instant start, Instant end, int limit) {
        // This is a bit complex as we need to find APP snapshots that belong to this budget's selector (TEAM or NAMESPACE)
        // For simplicity in MVP, if it's a NAMESPACE budget, we look for APPs in that namespace.
        // If it's a TEAM budget, we look for APPs with that team label.
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.group_key, SUM(s.cpu_mcpu) as cpu, SUM(s.mem_mib) as mem, SUM(s.total_cost_units) as cost ");
        sql.append("FROM allocation_snapshots s ");
        
        if ("TEAM".equals(b.getSelectorType())) {
            sql.append("JOIN (SELECT DISTINCT snapshot_id FROM workload_inventory WHERE json_extract(labels_json, '$.team') = ?) i ON s.id = i.snapshot_id ");
        }
        
        sql.append("WHERE s.group_type = 'APP' AND s.window_start >= ? AND s.window_end <= ? ");
        
        if ("NAMESPACE".equals(b.getSelectorType())) {
            // For NAMESPACE budgets, we need to filter apps by namespace in inventory
            sql.append("AND s.id IN (SELECT snapshot_id FROM workload_inventory WHERE namespace = ?) ");
        }
        
        sql.append("GROUP BY s.group_key ORDER BY cost DESC LIMIT ?");

        List<AllocationSnapshot> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if ("TEAM".equals(b.getSelectorType())) ps.setString(idx++, b.getSelectorValue());
            ps.setString(idx++, start.toString());
            ps.setString(idx++, end.toString());
            if ("NAMESPACE".equals(b.getSelectorType())) ps.setString(idx++, b.getSelectorValue());
            ps.setInt(idx++, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AllocationSnapshot snap = new AllocationSnapshot();
                    snap.setGroupKey(rs.getString("group_key"));
                    snap.setCpuMcpu(rs.getLong("cpu"));
                    snap.setMemMib(rs.getLong("mem"));
                    snap.setTotalCostUnits(rs.getDouble("cost"));
                    results.add(snap);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    /**
     * Saves an alert record to the database.
     *
     * @param id          the alert UUID
     * @param severity    the severity (WARN|CRITICAL)
     * @param budgetName  the name of the budget
     * @param message     the alert message
     * @param detailsJson the full alert details as JSON
     */
    public void saveAlert(String id, String severity, String budgetName, String message, String detailsJson) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO alerts (id, timestamp, severity, budget_name, message, details_json) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, severity);
            ps.setString(4, budgetName);
            ps.setString(5, message);
            ps.setString(6, detailsJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Maps a database result set row to a Budget object.
     *
     * @param rs the result set
     * @return the mapped Budget object
     * @throws SQLException if a database access error occurs
     */
    private Budget mapBudget(ResultSet rs) throws SQLException {
        Budget b = new Budget();
        b.setId(rs.getString("id"));
        b.setName(rs.getString("name"));
        b.setSelectorType(rs.getString("selector_type"));
        b.setSelectorKey(rs.getString("selector_key"));
        b.setSelectorValue(rs.getString("selector_value"));
        b.setPeriod(rs.getString("period"));
        b.setCpuMcpuLimit(rs.getLong("cpu_mcpu_limit"));
        b.setMemMibLimit(rs.getLong("mem_mib_limit"));
        b.setWarnPercent(rs.getInt("warn_percent"));
        b.setWebhookSecretName(rs.getString("webhook_secret_name"));
        return b;
    }
}
