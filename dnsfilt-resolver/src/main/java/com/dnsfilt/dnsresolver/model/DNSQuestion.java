package com.dnsfilt.dnsresolver.model;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DNSQuestion {
    /*
     * a domain name represented as a sequence of labels, where each label consists of a length octet followed by that number of octets.
     * The domain name terminates with the zero length octet for the null label of the root.
     *   Note that this field may be an odd number of octets; no padding is used.
     */
    private String qname;

    /*
     * a two octet code which specifies the type of the query.
     *  The values for this field include all codes valid for TYPE field, together with some more general codes whic     can match more than one type of RR.
     */
    private TYPE qtype;

    /*
     * a two octet code that specifies the class of the query.
     * For example, the QCLASS field is IN for the Internet.
     */
    private CLASS qclass;

    // Constructor
    public DNSQuestion(String qname, TYPE qtype, CLASS qclass) {
        this.qname = qname;
        this.qtype = qtype;
        this.qclass = qclass;
    }

    // Getters
    public String getQname() {
        return qname;
    }

    public TYPE getQtype() {
        return qtype;
    }

    public CLASS getQclass() {
        return qclass;
    }

    // Setters
    public void setQname(String qname) {
        this.qname = qname;
    }

    public void setQtype(TYPE qtype) {
        this.qtype = qtype;
    }

    public void setQclass(CLASS qclass) {
        this.qclass = qclass;
    }

    // Method to convert domain to DNS format
    public static byte[] encodeDomainName(String domain) {
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String label : domain.split("\\.")) {
            baos.write(label.length());
            baos.write(label.getBytes(StandardCharsets.US_ASCII), 0, label.length());
        }
        baos.write(0); // terminating zero length
        return baos.toByteArray();
    }

    // Method to parse domain name from DNS format
    /**
     * Decodes a domain name from DNS format into a regular string
     * Example: [3]'w','w','w'[7]'e','x','a','m','p','l','e'[3]'c','o','m'[0]
     * becomes "www.example.com."
     */
    private static String decodeDomainName(ByteBuffer buffer) {
        // Will hold our final domain name (e.g., "www.example.com.")
        StringBuilder domain = new StringBuilder();

        // Length of the next label
        int labelLength;

        // Keep reading until we hit a zero-length label (end of domain name)
        // buffer.get() reads the next byte, & 0xFF converts signed byte to unsigned int
        while ((labelLength = buffer.get() & 0xFF) > 0) {

            // Check if this is a compressed name pointer (starts with bits 11)
            // 0xC0 = 11000000 in binary
            if ((labelLength & 0xC0) == 0xC0) {
                // Calculate pointer position:
                // Take bottom 6 bits of first byte (& 0x3F)
                // Shift left 8 bits (<< 8)
                // OR with next byte to form 14-bit pointer
                int pointer = ((labelLength & 0x3F) << 8) | (buffer.get() & 0xFF);
                // Note: In a full implementation, you would follow this pointer
                // and continue reading the name from that position
                break;
            }

            // Not a pointer - this is a normal label
            // Create a byte array to hold this label
            byte[] labelBytes = new byte[labelLength];

            // Read exactly labelLength bytes into our array
            // Example: if length is 3, read next 3 bytes
            buffer.get(labelBytes, 0, labelLength);

            // Convert the bytes to an ASCII string
            // Example: [119, 119, 119] becomes "www"
            String label = new String(labelBytes, StandardCharsets.US_ASCII);

            // Add the label and a dot to our domain name
            // Example: "www."
            domain.append(label).append(".");
        }

        // Return the complete domain name
        // Example: "www.example.com."
        return domain.toString();
    }

    // Static factory method to create DNSQuestion from byte array
    public static DNSQuestion fromByteArray(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // Read the QNAME
        String qname = decodeDomainName(buffer);

        // Read QTYPE (16 bits)
        int qtypeValue = buffer.getShort() & 0xFFFF; // using 0xFFFF will treat as unsigned short (0 to 65535), since
                                                     // Java's short is signed (-32768 to 32767).
        TYPE qtype = TYPE.fromValue(qtypeValue);

        // Read QCLASS (16 bits)
        int qclassValue = buffer.getShort() & 0xFFFF;
        CLASS qclass = CLASS.fromValue(qclassValue);

        return new DNSQuestion(qname, qtype, qclass);
    }

    // Method to convert DNSQuestion to byte array
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(512); // DNS messages are typically limited to 512 bytes

        // Write QNAME
        buffer.put(encodeDomainName(qname));

        // Write QTYPE
        buffer.putShort((short) qtype.getValue());

        // Write QCLASS
        buffer.putShort((short) qclass.getValue());

        // Create final byte array of exact size
        // buffer.position() returns the current position, which is essentially how many
        // bytes you've written
        byte[] result = new byte[buffer.position()];

        // This is a crucial operation when you want to switch from writing to reading
        // It does three things:
        // a. Sets the limit to the current position
        // b. Sets the position back to zero
        // c. Marks the buffer ready for reading
        // Reset position to 0, set limit to result.size()
        buffer.flip();

        // Read all result.size() bytes into result array
        buffer.get(result);

        return result;
    }

    @Override
    public String toString() {
        return "DNSQuestion [qname=" + qname + ", qtype=" + qtype + ", qclass=" + qclass + "]";
    }

    public static int getQuestionLength(byte[] data, int offset) {
        int index = offset;
        
        // Read domain name (length-prefixed labels)
        while (data[index] != 0) {  // Domain name ends with a null byte (0x00)
            int labelLength = data[index] & 0xFF; // Convert to unsigned byte
            
            if ((labelLength & 0xC0) == 0xC0) { 
                // Compression detected (pointer), jump out
                return (index + 2) - offset + 4; // Pointer is 2 bytes, add 4 for QTYPE and QCLASS
            }
    
            index += labelLength + 1; // Skip label (length byte + label characters)
        }
        
        // Add 1 byte for null terminator, 4 bytes for QTYPE and QCLASS
        return (index + 1 + 4) - offset;
    }
    
}
