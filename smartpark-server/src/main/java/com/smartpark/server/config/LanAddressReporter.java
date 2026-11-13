package com.smartpark.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prints, once the server is listening, the addresses the Entry and Exit PCs should put in
 * {@code smartpark-client.properties}, e.g.
 * <pre>
 *   SmartPark server is listening on 0.0.0.0:8080
 *   LAN clients: smartpark.server.host=192.168.1.10   smartpark.server.port=8080
 * </pre>
 * Only active network adapters with a private (site-local) IPv4 address are listed; loopback
 * and virtual adapters (VirtualBox, VMware, Hyper-V, WSL, Docker) are skipped because other PCs
 * cannot reach them.
 */
@Component
public class LanAddressReporter implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LanAddressReporter.class);

    private final String bindAddress;

    public LanAddressReporter(@Value("${server.address:0.0.0.0}") String bindAddress) {
        this.bindAddress = bindAddress;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return; // management or child contexts
        }
        int port = event.getWebServer().getPort();
        log.info("SmartPark server is listening on {}:{}", bindAddress, port);
        List<String> addresses = isWildcard(bindAddress) ? lanAddresses() : List.of(bindAddress);
        if (addresses.isEmpty()) {
            log.warn("No LAN (private IPv4) address found. Connect this PC to the LAN/Wi-Fi network; "
                    + "until then only clients on this PC can connect (smartpark.server.host=localhost).");
        }
        for (String a : addresses) {
            log.info("LAN clients: smartpark.server.host={}   smartpark.server.port={}   "
                    + "(REST http://{}:{}/api, WebSocket ws://{}:{}/ws)", a, port, a, port, a, port);
        }
    }

    static boolean isWildcard(String address) {
        return address == null || address.isBlank() || "0.0.0.0".equals(address) || "::".equals(address);
    }

    /** VirtualBox, VMware, Hyper-V, WSL and Docker adapters are not reachable from other PCs. */
    static boolean isVirtualAdapter(String displayName) {
        if (displayName == null) {
            return false;
        }
        String n = displayName.toLowerCase(java.util.Locale.ROOT);
        return n.contains("virtualbox") || n.contains("vmware") || n.contains("hyper-v") || n.contains("vethernet")
                || n.contains("wsl") || n.contains("docker") || n.contains("loopback") || n.contains("tap-");
    }

    /** Private IPv4 addresses of active, non-virtual, non-loopback network adapters. */
    static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || isVirtualAdapter(nic.getDisplayName())) {
                    continue;
                }
                for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        result.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("Cannot list network interfaces", e);
        }
        return result;
    }
}
