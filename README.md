# SDCI General Controller

A MAPE-K (Monitor-Analyze-Plan-Execute-Knowledge) loop based controller for self-adaptive resource management in Kubernetes environments.

## Overview

The SDCI General Controller is designed to automatically manage resource limits (CPU and RAM) for Kubernetes deployments based on observed performance metrics. It follows the classic MAPE-K architecture to achieve autonomous behavior.

### Architecture (MAPE-K)

1.  **Monitor**: Collects 99th percentile latency metrics for target services from Prometheus.
2.  **Analyze**: Evaluates the collected metrics, calculates trends using linear regression, and identifies symptoms (e.g., high latency).
3.  **Plan**: Determines the necessary actions based on the analysis (e.g., scale up or scale down resources).
4.  **Execute**: Applies the planned actions by updating Kubernetes deployment resource limits via the Kubernetes API.
5.  **Knowledge**: Maintains a history of metrics and shared state using an embedded H2 database.

## Technologies Used

*   **Java 21**: Core programming language.
*   **Maven**: Build and dependency management.
*   **Kubernetes Java Client**: For interacting with the Kubernetes cluster.
*   **H2 Database**: Embedded database for storing monitoring data.
*   **Prometheus**: External source for performance metrics.
*   **Log4j2**: Logging framework.
*   **Docker & Docker Compose**: For containerized deployment.

## Prerequisites

*   Java 21 JDK
*   Maven 3.x
*   A running Kubernetes cluster with [Istio](https://istio.io/) installed (for latency metrics).
*   Prometheus accessible from the controller.
*   `kubectl` configured with access to the cluster (if running via Docker Compose).

## Configuration

The controller uses several configuration parameters:

*   **Prometheus URL**: Passed as a command-line argument.
*   **Database Path**: Can be configured via the `db.path` system property (default: `./src/main/resources/knowledge`).
*   **Kubernetes Config**: When running in Docker, it expects the kubeconfig at `/root/.kube/config`.

## Getting Started

### Building the Project

To build the executable JAR:

```bash
mvn clean package
```

The output will be `target/app.jar`.

### Running Locally

```bash
java -jar target/app.jar <PROMETHEUS_URL>
```

Example:
```bash
java -jar target/app.jar http://localhost:9090
```

### Running with Docker Compose

1.  Build the Docker image:
    ```bash
    docker-compose build
    ```

2.  Run the controller:
    ```bash
    docker-compose up
    ```

*Note: Ensure your `~/.kube/config` is accessible as it's mounted into the container.*

## Project Structure

*   `src/main/java/com/blyweertboukari/sdci/managers/`: Contains the MAPE-K loop components.
*   `src/main/java/com/blyweertboukari/sdci/utils/`: Utility classes for Kubernetes and Prometheus interaction.
*   `src/main/java/com/blyweertboukari/sdci/enums/`: Enums for targets and metrics.
*   `src/main/resources/`: Configuration files and database storage.

## License

This project is licensed under the terms of the license included in the [LICENSE](LICENSE) file.
