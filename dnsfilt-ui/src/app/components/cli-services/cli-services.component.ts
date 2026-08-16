import { Component, EventEmitter, Output, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';

export type OperatingSystem = 'mac' | 'linux' | 'win-ps' | 'win-cmd';

export interface ResolverCommand {
  name: string;
  recordType: string;
  description: string;
  macLinuxCommand: string;
  winPsCommand: string;
  winCmdCommand: string;
  category: string;
  expectedResult: string;
}

@Component({
  selector: 'app-cli-services',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cli-services.component.html',
  styleUrl: './cli-services.component.css'
})
export class CliServicesComponent {
  @Output() copy = new EventEmitter<string>();

  selectedOs = signal<OperatingSystem>('mac');
  searchQuery = signal('');

  resolverCommands = signal<ResolverCommand[]>([
    {
      name: 'IPv4 Address',
      recordType: 'A',
      description: 'Resolves standard IPv4 host address for a domain name.',
      macLinuxCommand: 'dig google.com @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name google.com -Server dnsfilt.mooo.com -Type A',
      winCmdCommand: 'nslookup -type=A google.com dnsfilt.mooo.com',
      category: 'Standard Resolution',
      expectedResult: '142.250.xxx.xxx (IPv4 Address)'
    },
    {
      name: 'IPv6 Address',
      recordType: 'AAAA',
      description: 'Resolves 128-bit IPv6 host address for a domain name.',
      macLinuxCommand: 'dig google.com AAAA @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name google.com -Server dnsfilt.mooo.com -Type AAAA',
      winCmdCommand: 'nslookup -type=AAAA google.com dnsfilt.mooo.com',
      category: 'IPv6 Resolution',
      expectedResult: '2607:f8b0:4004:835::200e (IPv6 Address)'
    },
    {
      name: 'Canonical Name',
      recordType: 'CNAME',
      description: 'Queries domain name alias mapping to another target canonical domain name.',
      macLinuxCommand: 'dig www.github.com CNAME @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name www.github.com -Server dnsfilt.mooo.com -Type CNAME',
      winCmdCommand: 'nslookup -type=CNAME www.github.com dnsfilt.mooo.com',
      category: 'Alias Resolution',
      expectedResult: 'github.com (Target Domain)'
    },
    {
      name: 'Mail Exchange',
      recordType: 'MX',
      description: 'Queries mail server hostnames and priority ratings for email delivery.',
      macLinuxCommand: 'dig gmail.com MX @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name gmail.com -Server dnsfilt.mooo.com -Type MX',
      winCmdCommand: 'nslookup -type=MX gmail.com dnsfilt.mooo.com',
      category: 'Email Routing',
      expectedResult: '5 gmail-smtp-in.l.google.com.'
    },
    {
      name: 'Text & Security SPF',
      recordType: 'TXT',
      description: 'Queries text records used for SPF, DKIM verification, and domain ownership validation.',
      macLinuxCommand: 'dig google.com TXT @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name google.com -Server dnsfilt.mooo.com -Type TXT',
      winCmdCommand: 'nslookup -type=TXT google.com dnsfilt.mooo.com',
      category: 'Security & Verification',
      expectedResult: '"v=spf1 include:_spf.google.com ~all"'
    },
    {
      name: 'Name Server',
      recordType: 'NS',
      description: 'Queries authoritative name servers delegated to answer queries for the domain.',
      macLinuxCommand: 'dig cloudflare.com NS @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name cloudflare.com -Server dnsfilt.mooo.com -Type NS',
      winCmdCommand: 'nslookup -type=NS cloudflare.com dnsfilt.mooo.com',
      category: 'Authoritative Delegation',
      expectedResult: 'ns3.cloudflare.com, ns4.cloudflare.com'
    },
    {
      name: 'Start of Authority',
      recordType: 'SOA',
      description: 'Queries primary name server, domain administrator email, and zone serial timers.',
      macLinuxCommand: 'dig wikipedia.org SOA @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name wikipedia.org -Server dnsfilt.mooo.com -Type SOA',
      winCmdCommand: 'nslookup -type=SOA wikipedia.org dnsfilt.mooo.com',
      category: 'Zone Metadata',
      expectedResult: 'ns0.wikimedia.org. hostmaster.wikimedia.org.'
    },
    {
      name: 'Reverse DNS Pointer',
      recordType: 'PTR',
      description: 'Resolves IP address back to its associated hostname (Reverse DNS).',
      macLinuxCommand: 'dig -x 8.8.8.8 @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name 8.8.8.8 -Server dnsfilt.mooo.com -Type PTR',
      winCmdCommand: 'nslookup -type=PTR 8.8.8.8 dnsfilt.mooo.com',
      category: 'Reverse Lookup',
      expectedResult: 'dns.google.'
    },
    {
      name: 'Service Locator',
      recordType: 'SRV',
      description: 'Queries hostname and port details for specific network services (SIP, LDAP, XMPP).',
      macLinuxCommand: 'dig _sip._tcp.example.com SRV @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name _sip._tcp.example.com -Server dnsfilt.mooo.com -Type SRV',
      winCmdCommand: 'nslookup -type=SRV _sip._tcp.example.com dnsfilt.mooo.com',
      category: 'Service Discovery',
      expectedResult: '0 5 5060 sip.example.com.'
    },
    {
      name: 'Security Blocklist Filter',
      recordType: 'BLOCK',
      description: 'Queries domain blocklist in Aiven Valkey Redis. Blocked domains return NOERROR with 0 answer records.',
      macLinuxCommand: 'dig malware-test.com @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name malware-test.com -Server dnsfilt.mooo.com -Type A',
      winCmdCommand: 'nslookup malware-test.com dnsfilt.mooo.com',
      category: 'Threat Protection',
      expectedResult: 'NOERROR, 0 answer records (BLOCKED event logged to Kafka)'
    },
    {
      name: 'L1 Caffeine Cache Benchmark',
      recordType: 'CACHE',
      description: 'Run this query twice in succession. The second lookup hits JVM L1 Caffeine Cache instantly in 0ms.',
      macLinuxCommand: 'dig github.com @dnsfilt.mooo.com',
      winPsCommand: 'Resolve-DnsName -Name github.com -Server dnsfilt.mooo.com',
      winCmdCommand: 'nslookup github.com dnsfilt.mooo.com',
      category: 'Cache Performance',
      expectedResult: 'Query time: 0 msec (Sub-millisecond L1 Hit)'
    }
  ]);

  filteredCommands = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return this.resolverCommands();
    return this.resolverCommands().filter(c =>
      c.name.toLowerCase().includes(q) ||
      c.recordType.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q) ||
      c.macLinuxCommand.toLowerCase().includes(q) ||
      c.category.toLowerCase().includes(q)
    );
  });

  getCommandForOs(c: ResolverCommand): string {
    const os = this.selectedOs();
    if (os === 'mac' || os === 'linux') return c.macLinuxCommand;
    if (os === 'win-ps') return c.winPsCommand;
    return c.winCmdCommand;
  }

  onCopyCommand(c: ResolverCommand): void {
    const cmd = this.getCommandForOs(c);
    this.copy.emit(cmd);
  }
}
