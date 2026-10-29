# NeaBans

<p align="center">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Paper-1.21.x-1F232A?style=for-the-badge&logo=paper&logoColor=white" alt="Paper 1.21" />
  <img src="https://img.shields.io/badge/Kali_Linux-Audited-557C94?style=for-the-badge&logo=kalilinux&logoColor=white" alt="Kali Linux Audited" />
  <img src="https://img.shields.io/badge/Security-JWT%20%26%20HikariCP-005571?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="Security JWT" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License MIT" />
</p>

<p align="center">
  <a href="https://skillicons.dev">
    <img src="https://skillicons.dev/icons?i=java,mysql,sqlite,linux,bash,docker,git" alt="Tech Stack" />
  </a>
</p>

<p align="center">
  <b>High-throughput distributed moderation & punishment infrastructure for Minecraft networks.</b><br>
  <i>Multi-tier disciplinary matrices, proxy synchronization (Velocity & BungeeCord), embedded REST API with JWT security, and real-time Discord telemetry.</i>
</p>

---

## Architecture Overview

NeaBans is an enterprise-grade punishment management platform engineered for multi-instance Minecraft server clusters and standalone Paper/Purpur instances. Designed to replace legacy solutions with zero main-thread blocking, NeaBans decouples database I/O via HikariCP connection pooling and asynchronous worker threads.

An integrated lightweight HTTP/RESTful daemon provides real-time web administration capabilities secured through JSON Web Token (JWT) stateless authentication, enabling security operations and moderation dashboards outside the game client.

```
+-------------------------------------------------------------------------+
|                           Minecraft Clients                             |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-----------------------+-----------------------+-------------------------+
|  Spigot / Paper Node  |  Velocity Proxy Node  |  BungeeCord Proxy Node  |
+-----------------------+-----------------------+-------------------------+
            |                       |                        |
            +-----------+-----------+------------+-----------+
                        |                        |
                        v                        v
            +-----------------------+  +-------------------+
            |  HikariCP / SQLite    |  |  Plugin Messaging |
            |  Data Persistence     |  |  Channel ('sync') |
            +-----------------------+  +-------------------+
                        ^
                        |
            +-----------------------+
            |  Embedded HTTP Engine | <--- JWT REST API / Web Dashboard
            +-----------------------+
```

---

## Key Features

- **Asynchronous Execution:** All database queries (punishment checks, IP association history, log archiving) run asynchronously without holding the Bukkit main tick thread.
- **Categorized Punishment GUI:** Dynamic 54-slot visual penalty matrix categorizing offenses into escalating tiers (e.g., Exploits, Unfair Advantage, Chat Toxicity, Duplication, Doxing).
- **Embedded REST API & Web Server:** Built-in HTTP service exposing secure REST endpoints with cryptographically signed JWT tokens for external integrations.
- **Cross-Proxy Synchronization:** Native support for Paper, Velocity, and BungeeCord with synchronization channels (`neabans:sync`) to enforce network-wide immediate player disconnects.
- **Discord Webhook Engine:** Asynchronous, non-blocking webhook dispatcher supporting distinct severity colors, player heads, staff attribution, and expiration details.
- **Dual Persistence Engines:** High-performance local SQLite for single-server setups and HikariCP connection-pooled MySQL/MariaDB for enterprise network topologies.
- **AppSec Hardened:** Input sanitization against SQL injection, strict parameter type validation, and authenticated administrative routes designed and verified with Kali Linux security toolsets.

---

## REST API Specification

NeaBans provides an optional embedded HTTP daemon (configured in `config.yml`) exposing JSON endpoints:

| Endpoint | Method | Authentication | Description |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/login` | `POST` | Basic / Credentials | Generates a signed JWT session bearer token |
| `/api/v1/punishments` | `GET` | Bearer JWT | Retrieves paginated active and historical punishments |
| `/api/v1/punishments` | `POST` | Bearer JWT | Dispatches a new punishment across the network |
| `/api/v1/lookup/:player` | `GET` | Bearer JWT | Returns linked IP history, active infractions, and warnings |
| `/api/v1/punishments/:id` | `DELETE` | Bearer JWT | Revokes (unbans/unmutes) a specified punishment record |

---

## Command Matrix & Permissions

| Command | Syntax | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/neabans` | `/neabans <player>` | `neabans.use` | Opens the visual interactive penalty matrix GUI |
| `/neabans reload` | `/neabans reload` | `neabans.reload` | Reloads configurations and language catalogs in runtime |
| `/ban` | `/ban <player> [reason]` | `neabans.ban` | Issues an indefinite network-wide player ban |
| `/tempban` | `/tempban <player> <duration> [reason]` | `neabans.tempban` | Issues a timed player suspension (e.g., `7d`, `12h`) |
| `/unban` | `/unban <player>` | `neabans.unban` | Revokes an active ban record |
| `/mute` | `/mute <player> [reason]` | `neabans.mute` | Blocks the target player from sending public/private messages |
| `/tempmute` | `/tempmute <player> <duration> [reason]` | `neabans.tempmute` | Blocks player communications for a set duration |
| `/unmute` | `/unmute <player>` | `neabans.unmute` | Restores player communication privileges |
| `/ipban` | `/ipban <player\|ip> [reason]` | `neabans.ipban` | Permanently prevents connections originating from target IP |
| `/iptempban` | `/iptempban <player\|ip> <duration>` | `neabans.iptempban` | Temporarily suspends access for target IP address |
| `/warn` | `/warn <player> [reason]` | `neabans.warn` | Issues an infraction warning, triggering automatic actions at threshold |
| `/kick` | `/kick <player> [reason]` | `neabans.kick` | Forcefully disconnects target player from the instance |
| `/lookup` | `/lookup <player>` | `neabans.lookup` | Displays account details, past IP addresses, and sanctions |
| `/history` | `/history <player> [page]` | `neabans.history` | Paginated chronological audit log of player offenses |

### Immunity & Bypass Nodes
- `neabans.bypass.ban` — Prevents player from being banned.
- `neabans.bypass.mute` — Prevents player from being muted.
- `neabans.bypass.kick` — Prevents player from being kicked.
- `neabans.staff.notify` — Receives real-time broadcast alerts of all staff moderation actions.

---

## Configuration Reference

Standard configuration fragment (`config.yml`):

```yaml
# ==============================================================================
# NeaBans - Punishment & Player Moderation System
# Configuration Specification & Infrastructure Definitions
# ==============================================================================

settings:
  server-name: "Hub-01"
  language: "en" # Options: en, es
  broadcast-punishments: true
  staff-only-broadcast: false
  max-warns: 5
  max-warns-action: "neabans tempban {player} 7d Accumulated maximum infraction threshold"

database:
  type: mysql # Options: sqlite, mysql
  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "neabans"
    username: "neabans_usr"
    password: "${DB_PASSWORD}"
    pool-size: 10
    connection-timeout: 30000

web:
  enabled: true
  port: 8080
  bind-host: "0.0.0.0"
  jwt-expiration-hours: 24

discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  bot-name: "Security Telemetry"
```

---

## Cybersecurity & Auditing Guidelines

NeaBans is structured with defensive application security practices:
1. **Parameterized Queries:** Prepared statements are strictly enforced across both SQLite and MySQL layers to eliminate SQL Injection (SQLi) vectors.
2. **Stateless Web Security:** Embedded endpoints do not persist vulnerable session cookies; all administrative operations require short-lived, signed JWT tokens.
3. **Audit Trail Immutability:** Punishment logs retain timestamp, issuing staff UUID/IP, target UUID/IP, and reason history for compliance auditing.

For instructions on responsible disclosure and penetration testing guidelines using tools such as Kali Linux (`curl`, `nmap`, `burpsuite`), review our [SECURITY.md](SECURITY.md).

---

## Building from Source

Requires **Java 21 (JDK)** and **Apache Maven 3.8+**:

```bash
# Clone the repository
git clone https://github.com/NeaCol/NeaBans.git
cd NeaBans

# Compile and package binaries
mvn clean package
```

The compiled artifact will be located in `target/NeaBans-1.0-SNAPSHOT.jar`.

---

## Spanish Documentation / Documentación en Español

<details>
<summary><b>Haz clic aquí para ver la documentación en Español</b></summary>

### Descripción General
**NeaBans** es una solución integral y de nivel corporativo para la gestión disciplinaria y de moderación en redes de servidores de Minecraft. Diseñado para sustituir herramientas tradicionales como LiteBans o AdvancedBan, ofrece una plataforma unificada con soporte nativo para servidores Spigot/Paper y proxies (BungeeCord y Velocity), además de contar con un **servidor web embebido con panel de administración y API REST**.

### Características Destacadas
- **Sanciones Progresivas:** Menú visual GUI inteligente con más de 18 categorías de infracción y sanciones automáticas escalonadas.
- **API REST & Dashboard:** Autenticación por JWT, WebSockets y gestión remota de sanciones.
- **Sincronización de Red:** Canales de mensajería integrados para expulsar y bloquear infractores inmediatamente en toda la red de proxies.
- **Persistencia Flexible:** Conexión mediante SQLite para servidores independientes o MySQL con pool HikariCP para entornos distribuidos.
- **Seguridad Aplicada:** Validaciones de entrada preparadas, protección contra inyecciones y auditoría exhaustiva de acciones de moderación.

</details>

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
