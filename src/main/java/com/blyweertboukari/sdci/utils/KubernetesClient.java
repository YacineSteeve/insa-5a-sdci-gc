package com.blyweertboukari.sdci.utils;

import com.blyweertboukari.sdci.enums.Target;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.PatchUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class KubernetesClient {
    private static final KubernetesClient instance = new KubernetesClient();
    private static final Logger logger = LogManager.getLogger(KubernetesClient.class);
    private static final AppsV1Api api;
    private static final String NAMESPACE = "default";

    static {
        try {
            api = new AppsV1Api(ClientBuilder.standard().build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public enum Resource {
        CPU("cpu"),
        RAM("memory");

        public final String label;

        Resource(String label) {
            this.label = label;
        }

        public String formatValue(double value) {
            return switch (this) {
                case CPU -> String.valueOf(value);
                case RAM -> value + "Mi";
            };
        }
    }

    public static KubernetesClient getInstance() {
        return instance;
    }

    public void updateResourceLimits(Target target, Map<Resource, Double> resourcesUpdateDeltas) {
        V1Container container;
        int containerIndex;

        try {
            V1Deployment deployment = api.readNamespacedDeployment(target.deploymentName, NAMESPACE).execute();

            if (deployment.getSpec() == null || deployment.getSpec().getTemplate().getSpec() == null ||
                deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()
            ) {
                logger.error("Deployment {} has no containers", target.deploymentName);
                return;
            }

            Optional<V1Container> containerOpt = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                    .filter(c -> c.getName().equals(target.containerName))
                    .findFirst();

            if (containerOpt.isEmpty()) {
                logger.error("Deployment {} has no container named {}", target.deploymentName, target.containerName);
                return;
            }

            container = containerOpt.get();
            containerIndex = deployment.getSpec().getTemplate().getSpec().getContainers().indexOf(container);
        } catch (ApiException e) {
            logger.error("Error reading deployment {}:", target.deploymentName, e);
            return;
        }

        double newValue;

        if (container.getResources() == null || container.getResources().getLimits() == null ||
                container.getResources().getLimits().get(resource.label) == null
        ) {
            if (valueDelta > 0) {
                newValue = valueDelta;
                logger.warn("Container {} in deployment {} has no resource limits set. Setting initial limit.", target.containerName, target.deploymentName);
            } else {
                logger.error("Cannot decrease resource limits for container {} in deployment {} with no existing limits", target.containerName, target.deploymentName);
                return;
            }
        } else {
            double currentValue = container.getResources().getLimits().get(resource.label).getNumber().doubleValue();
            newValue = currentValue + valueDelta;
        }

        String newValueStr = resource.formatValue(newValue);

        try {
            PatchUtils.patch(
                    V1Deployment.class,
                    () -> api.patchNamespacedDeployment(
                            target.deploymentName,
                            NAMESPACE,
                            new V1Patch(buildJsonPatchString(containerIndex, resource, newValueStr))
                    ).buildCall(null),
                    V1Patch.PATCH_FORMAT_JSON_PATCH,
                    api.getApiClient()
            );
        } catch (ApiException e) {
            logger.error("Error updating deployment {}:", target.deploymentName, e);
        }

        logger.info("Updated {} to {} for deployment {}", resource.label, newValueStr, target.deploymentName);
    }

    private String buildJsonPatchString(int containerIndex, Resource resource, String value) {
        return String.format(
                "[{\"op\":\"replace\",\"path\":\"/spec/template/spec/containers/%d/resources/limits/%s\",\"value\":\"%s\"}]",
                containerIndex, resource.label, value
        );
    }
}
