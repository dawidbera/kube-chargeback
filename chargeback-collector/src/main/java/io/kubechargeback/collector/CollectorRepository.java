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

    /**
     * Saves an allocation snapshot to the database.
     *
     * @param s the snapshot to save
     */
    public void saveSnapshot(AllocationSnapshot s) {
        // Upsert or Ignore? Spec says: "If a snapshot row already exists... update is not needed; treat as success."
        // We can use INSERT OR IGNORE (SQLite specific) or INSERT ... ON CONFLICT DO NOTHING.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO allocation_snapshots (id, window_start, window_end, group_type, group_key, " +
                             "cpu_mcpu, mem_mib, cpu_cost_units, mem_cost_units, total_cost_units) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                             "ON CONFLICT(window_start, window_end, group_type, group_key) DO NOTHING")) {
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
