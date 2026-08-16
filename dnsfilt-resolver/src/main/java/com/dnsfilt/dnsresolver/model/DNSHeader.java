package com.dnsfilt.dnsresolver.model;

import java.nio.ByteBuffer;

/*
 * Typical DNS Header
 */
public class DNSHeader {
    /*
     * A 16 bit identifier assigned by the program that
     * generates any kind of query. This identifier is copied
     * the corresponding reply and can be used by the requester
     * to match up replies to outstanding queries.
     */
    private int id;

    /*
     * A one bit field that specifies whether this message is a
     query (0), or a response (1).
     */
    private QR qr;

    /*
     * A four bit field that specifies kind of query in this message.  
     * This value is set by the originator of a query and copied into the response.
     */
    private Opcode opcode;

    /*
     * Authoritative Answer - this bit is valid in responses, and specifies that the responding name server is an authority for the domain name in question section.
     */
    private boolean aa;

    /*
     * TrunCation - specifies that this message was truncated due to length greater than that permitted on the transmission channel.
     */
    private boolean tc;

    /*
     * Recursion Desired - this bit may be set in a query and is copied into the response.
     * If RD is set, it directs the name server to pursue the query recursively.
     * Recursive query support is optional.
     */
    private boolean rd;

    /* 
     * Recursion Available - this be is set or cleared in a response, and denotes whether recursive query support is available in the name server.
    */
    private boolean ra;

    /*
     * Reserved for future use.  Must be zero in all queries and responses.

     */
    private Z z;

    /*
     * Response code - this 4 bit field is set as part of responses
     */
    private RCODE rcode;

    /*
     * an unsigned 16 bit integer specifying the number of entries in the question section.
     */
    private int qdcount;

    /*
     * an unsigned 16 bit integer specifying the number of resource records in the answer section.

     */
    private int ancount;

    /*
     * an unsigned 16 bit integer specifying the number of resource records in the answer section.
     */
    private int nscount; 

    /*
     * an unsigned 16 bit integer specifying the number of resource records in the additional records section.
     */
    private int arcount; // Number of additional records

    // Constructor
    public DNSHeader(int id, QR qr, Opcode opcode, boolean aa, boolean tc, boolean rd, boolean ra, Z z, RCODE rcode,
            int qdcount, int ancount, int nscount, int arcount) {
        this.id = id;
        this.qr = qr;
        this.opcode = opcode;
        this.aa = aa;
        this.tc = tc;
        this.rd = rd;
        this.ra = ra;
        this.z = z;
        this.rcode = rcode;
        this.qdcount = qdcount;
        this.ancount = ancount;
        this.nscount = nscount;
        this.arcount = arcount;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public QR getQr() {
        return qr;
    }

    public void setQr(QR qr) {
        this.qr = qr;
    }

    public Opcode getOpcode() {
        return opcode;
    }

    public void setOpcode(Opcode opcode) {
        this.opcode = opcode;
    }

    public boolean isAa() {
        return aa;
    }

    public void setAa(boolean aa) {
        this.aa = aa;
    }

    public boolean isTc() {
        return tc;
    }

    public void setTc(boolean tc) {
        this.tc = tc;
    }

    public boolean isRd() {
        return rd;
    }

    public void setRd(boolean rd) {
        this.rd = rd;
    }

    public boolean isRa() {
        return ra;
    }

    public void setRa(boolean ra) {
        this.ra = ra;
    }

    public Z getZ() {
        return z;
    }

    public void setZ(Z z) {
        this.z = z;
    }

    public RCODE getRcode() {
        return rcode;
    }

    public void setRcode(RCODE rcode) {
        this.rcode = rcode;
    }

    public int getQdcount() {
        return qdcount;
    }

    public void setQdcount(int qdcount) {
        this.qdcount = qdcount;
    }

    public int getAncount() {
        return ancount;
    }

    public void setAncount(int ancount) {
        this.ancount = ancount;
    }

    public int getNscount() {
        return nscount;
    }

    public void setNscount(int nscount) {
        this.nscount = nscount;
    }

    public int getArcount() {
        return arcount;
    }

    public void setArcount(int arcount) {
        this.arcount = arcount;
    }

    // Convert header to byte array
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(12); // DNS header is always 12 bytes
        buffer.putShort((short) id);

        // Flags (16 bits)
        int flags = 0;
        flags |= (qr.getValue() << 15); // QR (1 bit)
        flags |= (opcode.getValue() << 11); // Opcode (4 bits)
        flags |= (aa ? 1 << 10 : 0); // AA (1 bit)
        flags |= (tc ? 1 << 9 : 0); // TC (1 bit)
        flags |= (rd ? 1 << 8 : 0); // RD (1 bit)
        flags |= (ra ? 1 << 7 : 0); // RA (1 bit)
        flags |= (z.getValue() << 4); // Z (3 bits)
        flags |= (rcode.getValue()); // RCODE (4 bits)
        buffer.putShort((short) flags);

        // Counts
        buffer.putShort((short) qdcount);
        buffer.putShort((short) ancount);
        buffer.putShort((short) nscount);
        buffer.putShort((short) arcount);

        return buffer.array();
    }

    // Parse byte array to DNSHeader
    public static DNSHeader fromByteArray(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int id = buffer.getShort() & 0xFFFF;

        int flags = buffer.getShort() & 0xFFFF;
        QR qr = (flags >> 15) == 1 ? QR.RESPONSE : QR.QUERY;
        Opcode opcode = Opcode.values()[(flags >> 11) & 0xF];
        boolean aa = ((flags >> 10) & 1) == 1;
        boolean tc = ((flags >> 9) & 1) == 1;
        boolean rd = ((flags >> 8) & 1) == 1;
        boolean ra = ((flags >> 7) & 1) == 1;
        Z z = Z.ZERO; // Reserved (must be 0)
        RCODE rcode = RCODE.values()[flags & 0xF];

        int qdcount = buffer.getShort() & 0xFFFF;
        int ancount = buffer.getShort() & 0xFFFF;
        int nscount = buffer.getShort() & 0xFFFF;
        int arcount = buffer.getShort() & 0xFFFF;

        return new DNSHeader(id, qr, opcode, aa, tc, rd, ra, z, rcode, qdcount, ancount, nscount, arcount);
    }

    @Override
    public String toString() {
        return "DNSHeader{" +
                "id=" + id +
                ", qr=" + qr +
                ", opcode=" + opcode +
                ", aa=" + aa +
                ", tc=" + tc +
                ", rd=" + rd +
                ", ra=" + ra +
                ", z=" + z +
                ", rcode=" + rcode +
                ", qdcount=" + qdcount +
                ", ancount=" + ancount +
                ", nscount=" + nscount +
                ", arcount=" + arcount +
                '}';
    }
}
