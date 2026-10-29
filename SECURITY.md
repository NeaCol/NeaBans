# Security & Application Security (AppSec) Policy

## Threat Model & Security Posture

**NeaBans** is designed as a secure moderation and enforcement platform handling sensitive player identifiers, IP addresses, audit logs, and administrative credentials.

### Key Security Safeguards:
- **Cryptographic Token Verification:** Embedded web server authenticates all REST API and WebSocket requests using signed JSON Web Tokens (JWT) with HMAC-SHA256 and configurable expiration windows.
- **SQL Injection Prevention:** All persistence operations (HikariCP MySQL and SQLite) strictly utilize parameterized `PreparedStatement` interfaces. No dynamic SQL string concatenation is permitted.
- **Timing Attack Mitigation:** Authentication routines and credential checks enforce constant-time comparisons where applicable.
- **Audit Trails:** All sanction events, revocations, and administrative modifications generate immutable audit logs with issuing operator, target UUID, IP mask, and timestamp.

## Security Research & Penetration Testing Guidelines

Security researchers and cybersecurity practitioners (evaluating via Kali Linux, Burp Suite, OWASP ZAP, Nmap, etc.) are welcome to audit NeaBans under the following terms:

- Always test against your own isolated, locally hosted test servers.
- Do not perform Denial of Service (DoS/DDoS) attacks against production infrastructure.
- Follow responsible disclosure standards.

## Reporting a Vulnerability

If you discover a security issue or bypass vector, please report it privately:

- **Security Contact:** `milansanchz@gmail.com`
- **Subject:** `[NeaBans Security] Vulnerability Report`
- **Include:** Summary, affected endpoint/component, steps to reproduce, and proof of concept (PoC).

All valid vulnerabilities receive an initial response within 48 hours and prioritized security patches.
