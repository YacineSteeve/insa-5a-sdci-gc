package com.blyweertboukari.sdci.utils;

import com.blyweertboukari.sdci.enums.Target;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.PatchUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class KubernetesClient {
    private static final KubernetesClient instance = new KubernetesClient();
    private static final Logger logger = LogManager.getLogger(KubernetesClient.class);
    private static final AppsV1Api api;

    static {
        try {
            api = new AppsV1Api(ClientBuilder.standard().build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public enum Resource {
        CPU("cpu", 50),
        RAM("memory", 128);

        public final String label;
        public final int minValue;

        Resource(String label, int minValue) {
            this.label = label;
            this.minValue = minValue;
        }

        public String formatValue(int value) {
            return switch (this) {
                case CPU -> value + "m";
                case RAM -> value + "Mi";
            };
        }
    }

    public static KubernetesClient getInstance() {
        return instance;
    }

    public void updateResourceLimits(Target target, Map<Resource, Integer> resourcesUpdateDeltas) {
        V1Container container;

        try {
            V1Deployment deployment = api.readNamespacedDeployment(target.deploymentName, target.namespace).execute();

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
        } catch (ApiException e) {
            logger.error("Error reading deployment {}:", target.deploymentName, e);
            return;
        }

        Map<String, String> newLimitsMap = new HashMap<>();

        for (Map.Entry<Resource, Integer> entry : resourcesUpdateDeltas.entrySet()) {
            Resource resource = entry.getKey();
            int valueDelta = entry.getValue();

            int newValue;

            if (container.getResources() == null || container.getResources().getLimits() == null ||
                    container.getResources().getLimits().get(resource.label) == null
            ) {
                if (valueDelta > 0) {
                    newValue = valueDelta;
                    logger.warn("Container {} in deployment {} has no resource limits set. Setting initial limit.", target.containerName, target.deploymentName);
                } else {
                    logger.error("Cannot decrease resource limits for container {} in deployment {} with no existing limits", target.containerName, target.deploymentName);
                    continue;
                }
            } else {
                int currentValue = container.getResources().getLimits().get(resource.label).getNumber().intValue();
                newValue = currentValue + valueDelta;
            }

            if (newValue < resource.minValue) {
                logger.warn("New value {} for resource {} is below minimum {}. Setting to minimum.", newValue, resource.label, resource.minValue);
                newValue = resource.minValue;
            }

            newLimitsMap.put(resource.label, resource.formatValue(newValue));
        }

        if (newLimitsMap.isEmpty()) {
            logger.warn("No resource limits to update for deployment {}", target.deploymentName);
            return;
        }

        Map<String, Object> patchBody =
                Collections.singletonMap("spec",
                        Collections.singletonMap("template",
                                Collections.singletonMap("spec",
                                        Collections.singletonMap("containers",
                                                Collections.singletonList(
                                                        Map.of(
                                                                "name", target.containerName,
                                                                "resources", Collections.singletonMap("limits",
                                                                        newLimitsMap
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                );

        try {
            PatchUtils.patch(
                    V1Deployment.class,
                    () -> api.patchNamespacedDeployment(
                            target.deploymentName,
                            target.namespace,
                            new V1Patch(JSON.serialize(patchBody))
                    ).buildCall(null),
                    V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH,
                    api.getApiClient()
            );

            logger.info("Updated {} for deployment {}", resourcesUpdateDeltas.keySet(), target.deploymentName);
        } catch (ApiException e) {
            logger.error("Error updating deployment {}:", target.deploymentName, e);
        }
    }
}
