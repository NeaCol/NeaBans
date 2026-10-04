# 🛡️ NeaBans

<p align="center">
  <b>Enterprise Moderation & Punishment Suite for Minecraft Networks</b><br>
  <i>Proxy Synchronization (Bungee/Velocity) • In-Game GUIs • Embedded Web Dashboard • Discord Webhooks</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen.svg" alt="Minecraft Version" />
  <img src="https://img.shields.io/badge/Platforms-Paper%20%7C%20Spigot%20%7C%20Bungee%20%7C%20Velocity-blue.svg" alt="Platforms" />
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Database-MySQL%20%7C%20SQLite-yellow.svg" alt="Databases" />
</p>

---

## 📌 Descripción General

**NeaBans** es una solución integral y de nivel corporativo para la gestión disciplinaria y de moderación en redes de servidores de Minecraft. Creado para sustituir herramientas tradicionales como LiteBans o AdvancedBan, ofrece una plataforma unificada con soporte nativo para servidores Spigot/Paper y proxies (BungeeCord y Velocity), además de contar con un **servidor web embebido con panel de administración**.

Con NeaBans, el equipo de Staff dispone de interfaces gráficas interactivas (GUIs) que categorizan las sanciones de manera progresiva, garantizando que las reglas del servidor se apliquen de forma justa, rápida y uniforme.

---

## ✨ Características Principales

- **🔨 Sanciones Multi-Tipo:** Soporte completo para `ban`, `tempban`, `ipban`, `iptempban`, `mute`, `tempmute`, `kick`, `warn`, `unban`, `unmute` y `ipunban`.
- **🖥️ Matriz Visual de Infracciones (GUI):** Menú visual inteligente con más de 18 categorías configurables (X-Ray, Hacks/Cheats, Toxicidad, Flood, Spam, Evasión de Sanciones, Exploits, Doxing, Griefing, Dupe, etc.) con sanciones escalonadas (1ª leve, 2ª temporal, 3ª permanente).
- **🌐 Dashboard y API Web Integrada:** Servidor HTTP interno con arquitectura RESTful, autenticación basada en tokens JWT y WebSockets en tiempo real para gestionar expedientes desde el navegador.
- **💬 Integración con Discord (Webhooks):** Notificaciones enriquecidas inmediatas con avatares de los jugadores, motivos, duración, staff responsable y códigos de color según la severidad.
- **⚡ Sincronización Global de Red:** Módulos de proxy dedicados para BungeeCord y Velocity que expulsan y deniegan el acceso a usuarios sancionados a través de canales de mensajería (`neabans:sync`).
- **💾 Motor de Base de Datos Dual:** Soporte para SQLite local (servidores únicos) y MySQL con pool de conexiones HikariCP (redes multidominio).
- **🌐 Soporte Multilingüe:** Archivos de traducción personalizables (`langs/es.yml`, `langs/en.yml`).

---

## 📋 Comandos y Permisos

### Comandos Principales
| Comando | Descripción | Permiso |
| :--- | :--- | :--- |
| `/neabans <jugador>` | Abre la GUI interactiva de sanciones | `neabans.use` |
| `/neabans reload` | Recarga las configuraciones y mensajes | `neabans.reload` |
| `/neabans clearhistory <jugador>` | Borra el historial de un jugador | `neabans.clearhistory` |
| `/ban <jugador> [razón]` | Baneo permanente | `neabans.ban` |
| `/tempban <jugador> <duración> [razón]` | Baneo temporal (ej. `7d`, `12h`) | `neabans.tempban` |
| `/unban <jugador>` | Desbanea a un usuario | `neabans.unban` |
| `/mute <jugador> [razón]` | Muteo permanente de chat | `neabans.mute` |
| `/tempmute <jugador> <duración> [razón]` | Muteo temporal de chat | `neabans.tempmute` |
| `/unmute <jugador>` | Desmutea a un usuario | `neabans.unmute` |
| `/kick <jugador> [razón]` | Expulsa a un usuario de la modalidad/red | `neabans.kick` |
| `/warn <jugador> [razón]` | Advierte a un usuario (acumulable) | `neabans.warn` |
| `/ipban <jugador\|ip> [razón]` | Banea una dirección IP permanente | `neabans.ipban` |
| `/iptempban <jugador\|ip> <duración>` | Baneo temporal de IP | `neabans.iptempban` |
| `/ipunban <ip>` | Elimina una sanción de IP | `neabans.ipunban` |
| `/history <jugador> [página]` | Historial detallado de infracciones | `neabans.history` |
| `/lookup <jugador>` | Información rápida, IPs vinculadas y estado | `neabans.lookup` |

### Permisos Especiales de Bypass
- `neabans.bypass.ban` — Inmune a baneos.
- `neabans.bypass.mute` — Inmune a silenciamientos.
- `neabans.bypass.kick` — Inmune a expulsiones.
- `neabans.staff.notify` — Recibe avisos en tiempo real de sanciones aplicadas.

---

## ⚙️ Configuración

Fragmento de configuración principal (`config.yml`):
```yaml
settings:
  server-name: "Survival"
  language: "es"
  broadcast-punishments: true
  staff-only-broadcast: false
  max-warns: 5
  max-warns-action: "neabans tempban {player} 7d Máximo de advertencias alcanzado"

discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  bot-name: "NeaBans Logger"
  notify-ban: true
  notify-tempban: true
  notify-mute: true

database:
  type: mysql # Opciones: sqlite, mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "neabans"
    username: "minecraft"
    password: "secret_password"
    pool-size: 10

web:
  enabled: true
  port: 8080
  bind-host: "0.0.0.0"
  jwt-expiration-hours: 24
```

---

## 🛠️ Instalación y Compilación

### Instalación
1. Descarga o compila el archivo JAR de NeaBans.
2. Coloca `NeaBans.jar` en la carpeta `/plugins/` de tus servidores Spigot/Paper.
3. Si utilizas BungeeCord o Velocity, coloca el módulo proxy en la carpeta de plugins correspondiente.
4. Reinicia el servidor y ajusta tus credenciales de base de datos en `config.yml`.

### Compilación desde el Código
Requiere **Java 21** y **Maven**:
```bash
git clone https://github.com/NeaCol/NeaBans.git
cd NeaBans
mvn clean package
```

---

<p align="center">
  Desarrollado con dedicación por <b>Nea</b> • Soluciones para Redes Profesionales
</p>
