package com.blyweertboukari.sdci.enums;

public enum Target {
    GATEWAY("gateway-deployment", "gateway-container"),
    SERVER("server-deployment", "server-container");

    public final String deploymentName;
    public final String containerName;

    Target(String deploymentName, String containerName) {
        this.deploymentName = deploymentName;
        this.containerName = containerName;
    }
}
