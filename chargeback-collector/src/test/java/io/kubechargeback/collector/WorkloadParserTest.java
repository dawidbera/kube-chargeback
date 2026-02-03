package io.kubechargeback.collector;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadParserTest {

    private final WorkloadParser parser = new WorkloadParser();

    /**
     * Tests parsing a valid Deployment with CPU and Memory requests and limits.
     * Verifies that the total resource requirements are calculated correctly based on replicas.
     */
    @Test
    void testFromDeployment_Valid() {
        Deployment d = new DeploymentBuilder()
                .withNewMetadata().withName("test-dep").withNamespace("test-ns").addToLabels("team", "team-a").endMetadata()
                .withNewSpec()
                .withReplicas(2)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToRequests("cpu", new Quantity("100m"))
                .addToRequests("memory", new Quantity("128Mi"))
                .addToLimits("cpu", new Quantity("200m"))
                .addToLimits("memory", new Quantity("256Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        WorkloadData w = parser.fromDeployment(d);
        assertEquals("test-dep", w.name);
        assertEquals("test-ns", w.namespace);
        assertEquals(200, w.cpuReq); // 100m * 2
        assertEquals(256, w.memReq); // 128Mi * 2
        assertEquals("OK", w.complianceStatus);
    }

    /**
     * Tests parsing a Deployment that is missing resource requests.
     * Verifies that the compliance status reflects the missing requests.
     */
    @Test
    void testFromDeployment_MissingRequests() {
        Deployment d = new DeploymentBuilder()
                .withNewMetadata().withName("test-dep").endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToLimits("cpu", new Quantity("200m"))
                .addToLimits("memory", new Quantity("256Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        WorkloadData w = parser.fromDeployment(d);
        assertEquals(0, w.cpuReq);
        assertEquals("MISSING_REQUESTS", w.complianceStatus);
    }

    /**
     * Tests parsing a Deployment that is missing resource limits.
     */
    @Test
    void testFromDeployment_MissingLimits() {
        Deployment d = new DeploymentBuilder()
                .withNewMetadata().withName("test-dep").endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToRequests("cpu", new Quantity("200m"))
                .addToRequests("memory", new Quantity("256Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        WorkloadData w = parser.fromDeployment(d);
        assertEquals("MISSING_LIMITS", w.complianceStatus);
    }

    /**
     * Tests parsing a Deployment that is missing both requests and limits.
     */
    @Test
    void testFromDeployment_BothMissing() {
        Deployment d = new DeploymentBuilder()
                .withNewMetadata().withName("test-dep").endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        WorkloadData w = parser.fromDeployment(d);
        assertEquals("BOTH_MISSING", w.complianceStatus);
    }

    /**
     * Tests parsing a StatefulSet.
     */
    @Test
    void testFromStatefulSet() {
        io.fabric8.kubernetes.api.model.apps.StatefulSet s = new io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder()
                .withNewMetadata().withName("test-sts").withNamespace("test-ns").endMetadata()
                .withNewSpec()
                .withReplicas(3)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToRequests("cpu", new Quantity("100m"))
                .addToRequests("memory", new Quantity("128Mi"))
                .addToLimits("cpu", new Quantity("200m"))
                .addToLimits("memory", new Quantity("256Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        WorkloadData w = parser.fromStatefulSet(s);
        assertEquals("test-sts", w.name);
        assertEquals("StatefulSet", w.kind);
        assertEquals(300, w.cpuReq); // 100m * 3
        assertEquals(384, w.memReq); // 128Mi * 3
        assertEquals("OK", w.complianceStatus);
    }

    /**
     * Tests parsing a DaemonSet.
     */
    @Test
    void testFromDaemonSet() {
        io.fabric8.kubernetes.api.model.apps.DaemonSet ds = new io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder()
                .withNewMetadata().withName("test-ds").withNamespace("kube-system").endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToRequests("cpu", new Quantity("50m"))
                .addToRequests("memory", new Quantity("64Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .addToLimits("memory", new Quantity("128Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .withNewStatus()
                .withDesiredNumberScheduled(5)
                .endStatus()
                .build();

        WorkloadData w = parser.fromDaemonSet(ds);
        assertEquals("test-ds", w.name);
        assertEquals("DaemonSet", w.kind);
        assertEquals(250, w.cpuReq); // 50m * 5
        assertEquals(320, w.memReq); // 64Mi * 5
        assertEquals("OK", w.complianceStatus);
    }

    /**
     * Tests parsing a Job and duration calculation.
     */
    @Test
    void testFromJob() {
        Instant now = Instant.now();
        Instant start = now.minus(30, ChronoUnit.MINUTES);
        Instant windowStart = now.minus(1, ChronoUnit.HOURS);
        Instant windowEnd = now;

        Job j = new JobBuilder()
                .withNewMetadata().withName("test-job").withNamespace("test-ns").endMetadata()
                .withNewSpec()
                .withParallelism(2)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withNewResources()
                .addToRequests("cpu", new Quantity("100m"))
                .addToRequests("memory", new Quantity("128Mi"))
                .endResources()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .withNewStatus()
                .withStartTime(start.toString())
                .endStatus()
                .build();

        WorkloadData w = parser.fromJob(j, windowStart, windowEnd);
        assertEquals("test-job", w.name);
        assertEquals("Job", w.kind);
        assertEquals(200, w.cpuReq); // 100m * 2
        assertEquals(256, w.memReq); // 128Mi * 2
        assertEquals(0.5, w.durationHours, 0.01);
    }
}
