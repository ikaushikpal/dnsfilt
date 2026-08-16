package com.dnsfilt.dnsresolver.model;

/*
     * a two octet code which specifies the type of the query.
     *  The values for this field include all codes valid for TYPE field, together with some more general codes whic     can match more than one type of RR.
     */
    public enum TYPE {
        A(1),         // Host address
        NS(2),        // Authoritative name server
        // MD(3),     // Mail destination (obsolete)
        // MF(4),     // Mail forwarder (obsolete)
        CNAME(5),     // Canonical name for alias
        SOA(6),       // Start of zone authority
        // MB(7),     // Mailbox domain name (experimental)
        // MG(8),     // Mail group member (experimental)
        // MR(9),     // Mail rename domain name (experimental)
        // NULL(10),  // Null resource record (experimental)
        // WKS(11),   // Well-known service description (obsolete)
        PTR(12),      // Domain name pointer
        // HINFO(13), // Host information
        // MINFO(14), // Mailbox or mail list information
        MX(15),       // Mail exchange
        TXT(16),      // Text strings
        // RP(17),    // Responsible person
        // AFSDB(18), // AFS Data Base location
        // X25(19),   // X.25 address
        // ISDN(20),  // ISDN address
        // RT(21),    // Route Through
        // NSAP(22),  // NSAP address
        // NSAP_PTR(23), // Reverse NSAP lookup (deprecated)
        // SIG(24),   // Security signature
        // KEY(25),   // Security key
        // PX(26),    // X.400 mail mapping information
        // GPOS(27),  // Geographical position (obsolete)
        AAAA(28),     // IPv6 host address
        // LOC(29),   // Location information
        // NXT(30),   // Next domain (obsolete, replaced by NSEC)
        // EID(31),   // Endpoint identifier
        // NIMLOC(32),// Nimrod Locator
        SRV(33),      // Service locator
        // ATMA(34),  // ATM address
        NAPTR(35),    // Naming authority pointer
        // KX(36),    // Key exchanger
        // CERT(37),  // Certification record
        // A6(38),    // IPv6 address (deprecated)
        // DNAME(39), // Non-terminal name redirection
        // SINK(40),  // SINK record (experimental)
        // OPT(41),   // Option (pseudo record for EDNS)
        // APL(42),   // Address prefix list
        DS(43),       // Delegation Signer
        // SSHFP(44), // SSH fingerprint
        // IPSECKEY(45), // IPsec key
        RRSIG(46),    // DNSSEC signature
        // NSEC(47),  // Next Secure record
        DNSKEY(48),   // DNSSEC key
        // DHCID(49), // DHCP identifier
        // NSEC3(50), // NSEC3 record
        // NSEC3PARAM(51), // NSEC3 parameter
        // TLSA(52),  // TLSA certificate association
        // SMIMEA(53),// S/MIME cert association
        // HIP(55),   // Host Identity Protocol
        // NINFO(56), // NINFO record (obsolete)
        // RKEY(57),  // RKEY record (obsolete)
        // TALINK(58),// Trust Anchor LINK
        // CDS(59),   // Child DS
        // CDNSKEY(60), // DNSKEY for Child Zone
        // OPENPGPKEY(61), // OpenPGP public key
        // CSYNC(62), // DNSSEC Child-To-Parent Synchronization
        // ZONEMD(63),// Message Digest for DNS Zone
        // SVCB(64),  // Service Binding
        // HTTPS(65), // HTTP Service Binding
        // SPF(99),   // Sender Policy Framework
        // UINFO(100),// User Information
        // UID(101),  // User Identifier
        // GID(102),  // Group Identifier
        // UNSPEC(103),// Unspecified
        // NID(104),  // Node Identifier
        // L32(105),  // 32-bit Locator
        // L64(106),  // 64-bit Locator
        // LP(107),   // Locator Pointer
        // EUI48(108),// MAC address (48-bit)
        // EUI64(109),// MAC address (64-bit)
        // TKEY(249), // Transaction key
        // TSIG(250), // Transaction signature
        // IXFR(251), // Incremental zone transfer
        // AXFR(252), // Transfer of an entire zone
        // MAILB(253),// Mailbox-related RRs (experimental)
        // MAILA(254),// Mail agent RRs (experimental)
        ANY(255);     // All cached records
    
        private final int value;
    
        TYPE(int value) {
            this.value = value;
        }
    
        public int getValue() {
            return value;
        }
    
        public static TYPE fromValue(int value) {
            for (TYPE type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown TYPE value: " + value);
        }
    }
    