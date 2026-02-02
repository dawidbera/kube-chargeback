# KubeChargeback

Lightweight Kubernetes showback/chargeback tool. It consists of a **CronJob collector** that aggregates CPU/RAM requests into cost units and a **REST API** that serves allocation and compliance reports.

## Architecture

- **chargeback-api**: Quarkus REST service for budget management and reporting.
- **chargeback-collector**: Quarkus batch application (CronJob) that scans Kubernetes workloads.
- **chargeback-common**: Shared models and utilities.
- **Persistence**: Shared SQLite database stored on a PersistentVolumeClaim (PVC).

## Features

- **Resource Footprint**: Based strictly on Kubernetes `resources.requests`.
- **Budgeting**: Supports `TEAM` and `NAMESPACE` budgets with configurable alert thresholds.
- **Reporting**:
  - Global and team-based application cost reports.
  - Compliance reports identifying workloads with missing resource specifications.
- **Alerting**: HTTP webhook notifications when budget thresholds are exceeded.

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+ (or use the provided `./mvnw`)
- A Kubernetes cluster (minikube, kind, OpenShift Sandbox)

### Build

To build the entire project:
```bash
./mvnw clean package
```

### Run Tests

To run all unit and integration tests:
```bash
./mvnw test -Dquarkus.http.test-port=0
```

### Deployment

The project is ready for deployment using Kustomize:
```bash
kubectl apply -k manifests/base
```

For OpenShift environments, use the OpenShift overlay:
```bash
kubectl apply -k manifests/openshift
```

## Configuration

Configuration is managed via the `kubechargeback-config` ConfigMap. Key properties include:
- `rate.cpu_mcpu_hour`: Cost per 1000m CPU per hour.
- `rate.mem_mib_hour`: Cost per 1 MiB memory per hour.
- `label.team`: Label key used to identify teams (default: `team`).
- `namespace.allowlist`: CSV list of namespaces to monitor (empty means current namespace only).

## API Documentation

The API is available at `/api/v1` and includes endpoints for:
- `/budgets`: CRUD operations for resource budgets.
- `/reports/allocations`: Aggregated resource consumption data.
- `/reports/top-apps`: Most expensive applications (global or team-filtered).
- `/reports/compliance`: Inventory of workloads with resource specification issues.