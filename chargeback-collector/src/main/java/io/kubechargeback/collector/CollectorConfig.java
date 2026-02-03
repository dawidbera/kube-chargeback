package io.kubechargeback.collector;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;

@ApplicationScoped
public class CollectorConfig {
    
    @ConfigProperty(name = "rate.cpu_mcpu_hour")
    double rateCpu;
    
    @ConfigProperty(name = "rate.mem_mib_hour")
    double rateMem;

    @ConfigProperty(name = "label.team", defaultValue = "team")
    String labelTeam;

    @ConfigProperty(name = "label.app", defaultValue = "app")
    String labelApp;

    @ConfigProperty(name = "window.hours", defaultValue = "1")
    int windowHours;

    @ConfigProperty(name = "namespace.allowlist", defaultValue = "")
    Optional<String> allowlist;

    @ConfigProperty(name = "dashboard.url", defaultValue = "")
    String dashboardUrl;

    /**
     * Gets the rate for CPU millicores per hour.
     * @return the CPU rate
     */
    public double getRateCpu() { return rateCpu; }

    /**
     * Gets the rate for memory MiB per hour.
     * @return the memory rate
     */
    public double getRateMem() { return rateMem; }

    /**
     * Gets the label used for team identification.
     * @return the team label
     */
    public String getLabelTeam() { return labelTeam; }

    /**
     * Gets the label used for application identification.
     * @return the application label
     */
    public String getLabelApp() { return labelApp; }

    /**
     * Gets the collection window in hours.
     * @return the window hours
     */
    public int getWindowHours() { return windowHours; }

    /**
     * Gets the allowlist of namespaces.
     * @return an optional containing the allowlist string
     */
    public Optional<String> getAllowlist() { return allowlist; }

    /**
     * Gets the dashboard URL for inclusion in alerts.
     * @return the dashboard URL
     */
    public String getDashboardUrl() { return dashboardUrl; }
}
