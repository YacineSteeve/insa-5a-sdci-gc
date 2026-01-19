package com.blyweertboukari.sdci.enums;

public enum Target {
    GATEWAY("default", "sdci-gateway-i", "sdci-gateway-i-container"),
    SERVER("default", "sdci-server", "sdci-server-container");

    public final String namespace;
    public final String deploymentName;
    public final String containerName;

    Target(String namespace, String deploymentName, String containerName) {
        this.namespace = namespace;
        this.deploymentName = deploymentName;
        this.containerName = containerName;
    }
}
