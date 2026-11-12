package com.icaroerasmo.properties;

import lombok.Data;

@Data
public class CameraProperties {
    private String name;
    private String url;
    private TransportProtocol protocol = TransportProtocol.TCP;

    /**
     * Whether this camera is processed at all. A disabled camera is skipped
     * before any frame grab or detection worker starts, so a broken or unused
     * stream costs no CPU.
     */
    private boolean enabled = true;

    public enum TransportProtocol {
        TCP,
        UDP;
    }
}
