package com.dnsfilt.dnsresolver.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DNSResourceRecord {

    /*
     * a domain name to which this resource record pertains.
     */
    private String name;

    /*
     * two octets containing one of the RR type codes.
     * This field specifies the meaning of the data in the RDATA field.
     */
    private TYPE type;

    /*
     * two octets which specify the class of the data in the RDATA field.
     */
    private CLASS recordClass;

    /*
     * a 32 bit unsigned integer that specifies the time interval (in seconds) that
     * the resource record may be cached before it should be discarded.
     * Zero values are interpreted to mean that the RR can only be used for the
     * transaction in progress, and should not be cached.
     */
    private int ttl;

    /*
     * an unsigned 16 bit integer that specifies the length in octets of the RDATA
     * field.
     */
    private int rdLength;

    /*
     * a variable length string of octets that describes the resource.
     * The format of this information varie according to the TYPE and CLASS of the
     * resource record For example, the if the TYPE is A and the CLASS is IN the
     * RDATA field is a 4 octet ARPA Internet address.
     */
    private byte[] rdata; // Record data (format depends on TYPE)

    public DNSResourceRecord(String name, TYPE type, CLASS recordClass,
            int ttl, byte[] rdata) {
        this.name = name;
        this.type = type;
        this.recordClass = recordClass;
        this.ttl = ttl;
        this.rdata = rdata;
        this.rdLength = rdata.length;
    }

    /**
     * Parse a DNS Resource Record from a ByteBuffer
     * Format:
     * NAME - Variable length domain name
     * TYPE - 2 bytes
     * CLASS - 2 bytes
     * TTL - 4 bytes
     * RDLENGTH - 2 bytes
     * RDATA - RDLENGTH bytes
     */
    public static DNSResourceRecord fromByteArray(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Read the NAME field using the same method as DNSQuestion
        String name = readDomainName(buffer);

        // 2. Read TYPE (2 bytes, unsigned)
        int typeValue = buffer.getShort() & 0xFFFF;
        TYPE type = TYPE.fromValue(typeValue);

        // 3. Read CLASS (2 bytes, unsigned)
        int classValue = buffer.getShort() & 0xFFFF;
        CLASS recordClass = CLASS.fromValue(classValue);

        // 4. Read TTL (4 bytes)
        // Use getLong to handle unsigned int properly
        int ttl = (int) (buffer.getInt() & 0xFFFFFFFFL);

        // 5. Read RDLENGTH (2 bytes, unsigned)
        int rdLength = buffer.getShort() & 0xFFFF;

        // 6. Read RDATA (rdLength bytes)
        byte[] rdata = new byte[rdLength];
        buffer.get(rdata);

        return new DNSResourceRecord(name, type, recordClass, ttl, rdata);
    }

    /**
     * Convert the resource record to byte array format
     */
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
    
        // 1. Write NAME (Assuming already encoded in rdata, so skipping encoding)
        writeDomainName(buffer, name);   // Assuming `name` is already in correct format
    
        // 2. Write TYPE (2 bytes)
        buffer.putShort((short) type.getValue());
    
        // 3. Write CLASS (2 bytes)
        buffer.putShort((short) recordClass.getValue());
    
        // 4. Write TTL (4 bytes, unsigned)
        buffer.putInt(ttl);
    
        // 5. Write RDLENGTH (2 bytes, unsigned)
        buffer.putShort((short) rdata.length);  // Directly using encoded rdata length
    
        // 6. Write RDATA (Pre-encoded, should not be modified)
        buffer.put(rdata);
    
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
    
    private void writeDomainName(ByteBuffer buffer, String domain) {
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes());
        }
        buffer.put((byte) 0); // Null terminator
    }

    /**
     * Read a domain name from the buffer
     */
    private static String readDomainName(ByteBuffer buffer) {
        StringBuilder domain = new StringBuilder();
        int length;
    
        // Read each label until we hit a zero-length label
        while ((length = buffer.get() & 0xFF) > 0) {
            // Check for name compression (pointer)
            if ((length & 0xC0) == 0xC0) {
                // This is a pointer - read the offset
                int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
    
                // Save the current buffer position
                int currentPosition = buffer.position();
    
                // Jump to the pointer location
                buffer.position(pointer);
    
                // Read the domain name from the pointer location
                String compressedName = readDomainName(buffer);
    
                // Restore the original buffer position
                buffer.position(currentPosition);
    
                // Append the compressed name and return
                domain.append(compressedName);
                return domain.toString();
            }
    
            // Regular label - read 'length' bytes
            byte[] label = new byte[length];
            buffer.get(label);
            domain.append(new String(label, StandardCharsets.US_ASCII)).append(".");
        }
    
        // Remove the trailing dot if present
        if (domain.length() > 0 && domain.charAt(domain.length() - 1) == '.') {
            domain.setLength(domain.length() - 1);
        }
    
        return domain.toString();
    }


    // Getters
    public String getName() {
        return name;
    }

    public TYPE getType() {
        return type;
    }

    public CLASS getRecordClass() {
        return recordClass;
    }

    public int getTtl() {
        return ttl;
    }

    public int getRdLength() {
        return rdLength;
    }

    public byte[] getRdata() {
        return rdata.clone();
    }

    // Setters

    public void setName(String name) {
        this.name = name;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public void setRecordClass(CLASS recordClass) {
        this.recordClass = recordClass;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public void setRdLength(int rdLength) {
        this.rdLength = rdLength;
    }

    public void setRdata(byte[] rdata) {
        this.rdata = rdata;
    }

    @Override
    public String toString() {
        return "DNSResourceRecord [name=" + name + ", type=" + type + ", recordClass=" + recordClass + ", ttl=" + ttl
                + ", rdLength=" + rdLength + ", rdata=" + Arrays.toString(rdata) + "]";
    }

}
