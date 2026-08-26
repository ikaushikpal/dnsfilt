package com.dnsfilt.dnsresolver.proxy;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 * DnsClientInfo
 *
 * Carries the resolved client identity for a DNS request.
 *
 * When a PROXY Protocol v2 header is present:
 *   address = original source IP from PROXY header (real Internet client)
 *   port    = original source port from PROXY header
 *
 * When no PROXY header is present (raw DNS mode):
 *   address = DatagramPacket.getAddress()  (may be NAT/container address)
 *   port    = DatagramPacket.getPort()
 *
 * Using this object instead of DatagramPacket.getAddress()/getPort() directly
 * ensures the rest of the application always works with the real client identity,
 * regardless of Podman NAT or Nginx proxying.
 */
public final class DnsClientInfo {

    private final InetAddress address;
    private final int port;

    public DnsClientInfo(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    /**
     * Creates a DnsClientInfo from a plain DatagramPacket SocketAddress (non-PROXY path).
     */
    public static DnsClientInfo fromSocketAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress isa) {
            return new DnsClientInfo(isa.getAddress(), isa.getPort());
        }
        throw new IllegalArgumentException("Unsupported SocketAddress type: " + socketAddress.getClass());
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    /**
     * Returns the IP address as a clean string without leading slashes.
     * Example: "183.87.202.75" (not "/183.87.202.75")
     */
    public String getIpString() {
        return address.getHostAddress();
    }

    /**
     * Returns the SocketAddress equivalent for sending a DatagramPacket reply.
     */
    public SocketAddress toSocketAddress() {
        return new InetSocketAddress(address, port);
    }

    @Override
    public String toString() {
        return getIpString() + ":" + port;
    }
}
