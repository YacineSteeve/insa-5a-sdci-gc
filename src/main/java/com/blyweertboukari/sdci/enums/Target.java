package com.blyweertboukari.sdci.enums;

public enum Target {
    GATEWAY("sdci-gateway-i", "sdci-gateway-i-container"),
    SERVER("sdci-server", "sdci-server-container");

    public final String deploymentName;
    public final String containerName;

    Target(String deploymentName, String containerName) {
        this.deploymentName = deploymentName;
        this.containerName = containerName;
    }
}
