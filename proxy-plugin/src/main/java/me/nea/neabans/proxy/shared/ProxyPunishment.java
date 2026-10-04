package me.nea.neabans.proxy.shared;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * POJO que representa una sanción activa leída desde la base de datos MySQL compartida.
 *
 * <p>Este objeto es completamente independiente de la API de Paper/BungeeCord/Velocity;
 * solo depende del JDK y se usa tanto en el listener de BungeeCord como en el de Velocity.</p>
 *
 * <h2>Tipos soportados</h2>
 * <ul>
 *   <li>{@code BAN}         – ban permanente por UUID</li>
 *   <li>{@code TEMP_BAN}    – ban temporal por UUID</li>
 *   <li>{@code IP_BAN}      – IP ban permanente</li>
 *   <li>{@code TEMP_IP_BAN} – IP ban temporal</li>
 * </ul>
 *
 * <h2>Permanencia vs temporalidad</h2>
 * <ul>
 *   <li>{@code endTime == -1} → sanción permanente.</li>
 *   <li>{@code endTime > 0}   → sanción temporal; expira en ese epoch millis.</li>
 * </ul>
 */
public class ProxyPunishment {

    // =========================================================================
    // Campos
    // =========================================================================

    private final UUID   playerUuid;
    private final String playerName;

    /** Tipo de sanción en mayúsculas: BAN, TEMP_BAN, IP_BAN, TEMP_IP_BAN. */
    private final String type;

    private final String reason;
    private final String staffName;
    private final String server;
    private final long   startTime;

    /**
     * Epoch millis de expiración. Valor especial {@code -1} = permanente.
     */
    private final long endTime;

    private boolean active;

    /**
     * Dirección IP del jugador asociada a este castigo.
     * Solo relevante para IP_BAN / TEMP_IP_BAN. Puede ser {@code null}
     * si la columna {@code ip} aún no existe en la tabla (antes de la migración).
     */
    private final String ip;

    // =========================================================================
    // Formato de fechas
    // =========================================================================

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                             .withZone(ZoneId.systemDefault());

    // =========================================================================
    // Constructor
    // =========================================================================

    public ProxyPunishment(
            UUID   playerUuid,
            String playerName,
            String type,
            String reason,
            String staffName,
            String server,
            long   startTime,
            long   endTime,
            boolean active,
            String ip
    ) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type       = type;
        this.reason     = reason;
        this.staffName  = staffName;
        this.server     = server;
        this.startTime  = startTime;
        this.endTime    = endTime;
        this.active     = active;
        this.ip         = ip;
    }

    // =========================================================================
    // Lógica de estado
    // =========================================================================

    /**
     * @return {@code true} si {@code endTime == -1} (sin fecha de expiración).
     */
    public boolean isPermanent() {
        return endTime == -1L;
    }

    /**
     * @return {@code true} si la sanción ya pasó su fecha límite.
     *         Las sanciones permanentes nunca expiran.
     */
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= endTime;
    }

    /**
     * Milisegundos restantes hasta que la sanción expire.
     *
     * @return {@code Long.MAX_VALUE} si es permanente, {@code 0} si ya expiró,
     *         de lo contrario la diferencia en millis.
     */
    public long getRemainingMillis() {
        if (isPermanent()) return Long.MAX_VALUE;
        return Math.max(0L, endTime - System.currentTimeMillis());
    }

    // =========================================================================
    // Formateo del ban screen
    // =========================================================================

    /**
     * Construye el mensaje de pantalla de ban con colores {@code &} listos para
     * convertir mediante {@code ChatColor.translateAlternateColorCodes} (BungeeCord)
     * o {@code LegacyComponentSerializer.legacyAmpersand()} (Velocity).
     *
     * <p>Selecciona automáticamente la plantilla interna adecuada según el tipo
     * de sanción ({@code IP_BAN}, {@code TEMP_BAN}, o ban permanente).</p>
     *
     * @param appealLink URL de apelación mostrada al final del mensaje.
     * @return Texto formateado con códigos {@code &} y saltos de línea {@code \n}.
     */
    public String formatBanScreen(String appealLink) {
        if (type.equals("IP_BAN") || type.equals("TEMP_IP_BAN")) {
            return buildIpBanScreen(appealLink);
        }
        if (isPermanent()) {
            return buildPermanentBanScreen(appealLink);
        }
        return buildTempBanScreen(appealLink);
    }

    /**
     * Aplica una plantilla personalizada (leída del config.yml) reemplazando
     * los marcadores de posición por los valores reales de esta sanción.
     *
     * <p>Marcadores soportados: {@code {reason}}, {@code {staff}}, {@code {date}},
     * {@code {expire}}, {@code {remaining}}, {@code {appeal}}.</p>
     *
     * @param template   Plantilla con marcadores {@code {placeholder}}.
     * @param appealLink URL de apelación.
     * @return Plantilla con todos los marcadores reemplazados.
     */
    public String formatBanScreenFromTemplate(String template, String appealLink) {
        return template
                .replace("{reason}",    reason)
                .replace("{staff}",     staffName)
                .replace("{date}",      DATE_FMT.format(Instant.ofEpochMilli(startTime)))
                .replace("{expire}",    isPermanent() ? "Permanente"
                                                     : DATE_FMT.format(Instant.ofEpochMilli(endTime)))
                .replace("{remaining}", formatRemaining())
                .replace("{appeal}",   appealLink);
    }

    // ── Plantillas internas ──────────────────────────────────────────────────

    private String buildPermanentBanScreen(String appealLink) {
        return "&c&l⛔ CUENTA SANCIONADA ⛔\n" +
               "&8&m                              \n" +
               "&7Razón:    &f" + reason + "\n" +
               "&7Staff:    &f" + staffName + "\n" +
               "&7Fecha:    &f" + DATE_FMT.format(Instant.ofEpochMilli(startTime)) + "\n" +
               "&7Duración: &cPermanente\n" +
               "&8&m                              \n" +
               "&eApela en: &f" + appealLink;
    }

    private String buildTempBanScreen(String appealLink) {
        return "&6&l⏳ CUENTA SUSPENDIDA ⏳\n" +
               "&8&m                              \n" +
               "&7Razón:    &f" + reason + "\n" +
               "&7Staff:    &f" + staffName + "\n" +
               "&7Expira:   &e" + DATE_FMT.format(Instant.ofEpochMilli(endTime)) + "\n" +
               "&7Restante: &e" + formatRemaining() + "\n" +
               "&8&m                              \n" +
               "&eApela en: &f" + appealLink;
    }

    private String buildIpBanScreen(String appealLink) {
        return "&c&l\uD83D\uDEAB IP BANEADA \uD83D\uDEAB\n" +
               "&8&m                              \n" +
               "&7Razón:  &f" + reason + "\n" +
               "&7Staff:  &f" + staffName + "\n" +
               "&8&m                              \n" +
               "&eApela en: &f" + appealLink;
    }

    // ── Utilidades de tiempo ─────────────────────────────────────────────────

    /**
     * Formatea el tiempo restante de la sanción en un formato legible.
     * Ejemplo: {@code "2d 3h 15m"} o {@code "45m 30s"}.
     *
     * @return Cadena con el tiempo restante o {@code "Expirado"} si ya pasó.
     */
    private String formatRemaining() {
        if (isPermanent()) return "Permanente";
        long millis = endTime - System.currentTimeMillis();
        if (millis <= 0) return "Expirado";

        long seconds = millis / 1_000L;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;

        if (days > 0)    return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        if (hours > 0)   return hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public UUID    getPlayerUuid() { return playerUuid; }
    public String  getPlayerName() { return playerName; }
    public String  getType()       { return type; }
    public String  getReason()     { return reason; }
    public String  getStaffName()  { return staffName; }
    public String  getServer()     { return server; }
    public long    getStartTime()  { return startTime; }
    public long    getEndTime()    { return endTime; }
    public boolean isActive()      { return active; }
    public String  getIp()         { return ip; }

    // Setter únicamente para marcar como inactiva en memoria si se decide revocar
    public void setActive(boolean active) { this.active = active; }

    // =========================================================================
    // toString
    // =========================================================================

    @Override
    public String toString() {
        return "ProxyPunishment{" +
               "player='" + playerName + "'(" + playerUuid + ")" +
               ", type='" + type + '\'' +
               ", reason='" + reason + '\'' +
               ", staff='" + staffName + '\'' +
               ", endTime=" + endTime +
               ", active=" + active +
               ", ip='" + ip + '\'' +
               '}';
    }
}
