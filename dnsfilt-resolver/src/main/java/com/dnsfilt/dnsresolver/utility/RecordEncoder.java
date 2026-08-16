package com.dnsfilt.dnsresolver.utility;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class RecordEncoder {

    public static byte[] encodeRData(String data, int qType) {
        try {
            switch (qType) {
                case 1: // A Record (IPv4 Address)
                    return encodeARecord(data);
                case 28: // AAAA Record (IPv6 Address)
                    return encodeAAAARecord(data);
                case 5: // CNAME Record
                case 2: // NS Record
                case 12: // PTR Record
                    return encodeDomainName(data);
                case 15: // MX Record
                    return encodeMXRecord(data);
                case 6: // SOA Record
                    return encodeSOARecord(data);
                case 33: // SRV Record
                    return encodeSRVRecord(data);
                case 16: // TXT Record
                    return encodeTXTRecord(data);
                default:
                    throw new IllegalArgumentException("Unsupported QTYPE: " + qType);
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding error: " + e.getMessage(), e);
        }
    }

    // Encoding IPv4 Address (A Record)
    private static byte[] encodeARecord(String ip) throws UnknownHostException {
        if (ip == null || ip.trim().isEmpty()) {
            return new byte[4];
        }
        String singleIp = ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
        return InetAddress.getByName(singleIp).getAddress(); // 4 bytes
    }

    // Encoding IPv6 Address (AAAA Record)
    private static byte[] encodeAAAARecord(String ip) throws UnknownHostException {
        if (ip == null || ip.trim().isEmpty()) {
            return new byte[16];
        }
        String singleIp = ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
        return InetAddress.getByName(singleIp).getAddress(); // 16 bytes
    }

    // Encoding Domain Name (CNAME, NS, PTR)
    private static byte[] encodeDomainName(String domain) {
        String[] labels = domain.split("\\.");
        ByteBuffer buffer = ByteBuffer.allocate(256); // Max 255 bytes for domain name
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes(StandardCharsets.US_ASCII));
        }
        buffer.put((byte) 0); // Null terminator
        return buffer.array();
    }

    // Encoding MX Record (Priority + Domain Name)
    private static byte[] encodeMXRecord(String data) {
        String[] parts = data.split(" ", 2);
        int priority = Integer.parseInt(parts[0]); // Extract priority
        byte[] domain = encodeDomainName(parts[1]);
        ByteBuffer buffer = ByteBuffer.allocate(2 + domain.length);
        buffer.putShort((short) priority); // 2-byte priority
        buffer.put(domain);
        return buffer.array();
    }

    // Encoding SOA Record
    private static byte[] encodeSOARecord(String data) {
        String[] parts = data.split(" ");
        byte[] mName = encodeDomainName(parts[0]); // Primary NS
        byte[] rName = encodeDomainName(parts[1]); // Admin email (replace @ with .)
        int serial = Integer.parseInt(parts[2]);
        int refresh = Integer.parseInt(parts[3]);
        int retry = Integer.parseInt(parts[4]);
        int expire = Integer.parseInt(parts[5]);
        int minimum = Integer.parseInt(parts[6]);

        ByteBuffer buffer = ByteBuffer.allocate(mName.length + rName.length + 20);
        buffer.put(mName);
        buffer.put(rName);
        buffer.putInt(serial);
        buffer.putInt(refresh);
        buffer.putInt(retry);
        buffer.putInt(expire);
        buffer.putInt(minimum);
        return buffer.array();
    }

    // Encoding SRV Record (Priority, Weight, Port, Target)
    private static byte[] encodeSRVRecord(String data) {
        String[] parts = data.split(" ", 4);
        int priority = Integer.parseInt(parts[0]);
        int weight = Integer.parseInt(parts[1]);
        int port = Integer.parseInt(parts[2]);
        byte[] target = encodeDomainName(parts[3]);

        ByteBuffer buffer = ByteBuffer.allocate(6 + target.length);
        buffer.putShort((short) priority);
        buffer.putShort((short) weight);
        buffer.putShort((short) port);
        buffer.put(target);
        return buffer.array();
    }

    // Encoding TXT Record
    private static byte[] encodeTXTRecord(String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(1 + textBytes.length);
        buffer.put((byte) textBytes.length); // Length prefix
        buffer.put(textBytes);
        return buffer.array();
    }

    // Convert byte[] to Hex String for better visibility
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("\\x%02X", b));
        }
        return sb.toString();
    }
}

