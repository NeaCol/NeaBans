package me.nea.neabans.model;

import java.util.UUID;

/**
 * Representa una sanción aplicada a un jugador en NeaBans.
 *
 * <p>Esta clase es el núcleo del sistema: cada ban, mute, kick o advertencia
 * se almacena como una instancia de {@code Punishment} y se persiste en la
 * base de datos a través de la capa {@code Database}.</p>
 *
 * <h2>Ciclo de vida</h2>
 * <ol>
 *   <li>Se crea con el constructor "nueva sanción" → {@code active = true}.</li>
 *   <li>Se inserta en la DB via {@code Database#addPunishment}.</li>
 *   <li>Cuando se remueve manualmente, {@code active} pasa a {@code false}
 *       y se registran {@code removedBy} y {@code removedAt}.</li>
 *   <li>Si es temporal, expira automáticamente cuando
 *       {@code System.currentTimeMillis() >= endTime}.</li>
 * </ol>
 *
 * <h2>Permanencia vs. temporalidad</h2>
 * <ul>
 *   <li>{@code endTime == -1}  → sanción permanente.</li>
 *   <li>{@code endTime > 0}    → sanción temporal; expira en ese epoch millis.</li>
 * </ul>
 */
public class Punishment {

    // =========================================================================
    // Campos
    // =========================================================================

    /** ID autoincremental de la DB. -1 mientras no se haya persistido. */
    private int id;

    /** UUID del jugador sancionado. */
    private final UUID playerUuid;

    /** Nombre del jugador en el momento de la sanción. */
    private final String playerName;

    /** Tipo de sanción (ban, mute, kick, warn…). */
    private final PunishmentType type;

    /** Razón visible que se muestra al jugador al intentar conectarse/chatear. */
    private final String reason;

    /**
     * Categoría de infracción (e.g. "hacking", "chat_abuse").
     * Puede ser {@code null} para sanciones manuales sin categoría asignada.
     */
    private final String category;

    /**
     * Nivel dentro de la categoría (para sistemas de sanciones escalonadas).
     * {@code 0} indica que fue aplicada manualmente, sin seguir una escalada.
     */
    private final int level;

    /**
     * UUID del miembro del staff que aplicó la sanción.
     * {@code null} cuando la acción proviene de la consola.
     */
    private final UUID staffUuid;

    /** Nombre del staff (o "CONSOLE") que ejecutó la sanción. */
    private final String staffName;

    /**
     * Nombre del servidor en el que aplica la sanción.
     * {@code "global"} significa que rige en todos los servidores de la red.
     */
    private final String server;

    /** Epoch millis cuando se aplicó la sanción. */
    private final long startTime;

    /**
     * Epoch millis cuando expira la sanción.
     * El valor {@code -1} indica permanencia indefinida.
     */
    private final long endTime;

    /**
     * Estado de activación almacenado en la DB.
     * Para saber si la sanción está actualmente en vigor,
     * usar {@link #isActive()} que también evalúa expiración.
     */
    private boolean active;

    /**
     * Nombre del staff (o "CONSOLE") que removió la sanción.
     * {@code null} si no ha sido removida manualmente.
     */
    private String removedBy;

    /**
     * Epoch millis cuando fue removida la sanción.
     * {@code 0} si no ha sido removida.
     */
    private long removedAt;

    /**
     * Dirección IP asociada a la sanción.
     * Solo aplica para {@link PunishmentType#IP_BAN} y {@link PunishmentType#TEMP_IP_BAN}.
     * {@code null} para todos los demás tipos.
     */
    private String ip;

    // =========================================================================
    // Constructor 1 — Nueva sanción (desde código)
    // =========================================================================

    /**
     * Crea una nueva sanción lista para persistir en la base de datos.
     *
     * <p>El {@code id} se establece en {@code -1} (será asignado por la DB),
     * {@code startTime} se fija al momento actual, {@code active} a {@code true},
     * y los campos de remoción quedan vacíos.</p>
     *
     * @param playerUuid UUID del jugador sancionado.
     * @param playerName Nombre del jugador sancionado.
     * @param type       Tipo de sanción.
     * @param reason     Razón de la sanción.
     * @param category   Categoría de infracción, puede ser {@code null}.
     * @param level      Nivel dentro de la categoría; {@code 0} = manual.
     * @param staffUuid  UUID del staff que aplica la sanción; {@code null} para consola.
     * @param staffName  Nombre del staff o {@code "CONSOLE"}.
     * @param server     Servidor donde aplica; {@code "global"} para toda la red.
     * @param endTime    Epoch millis de expiración; {@code -1} para permanente.
     */
    public Punishment(
        UUID playerUuid,
        String playerName,
        PunishmentType type,
        String reason,
        String category,
        int level,
        UUID staffUuid,
        String staffName,
        String server,
        long endTime
    ) {
        this.id = -1;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type = type;
        this.reason = reason;
        this.category = category;
        this.level = level;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.server = server;
        this.startTime = System.currentTimeMillis();
        this.endTime = endTime;
        this.active = true;
        this.removedBy = null;
        this.removedAt = 0L;
        this.ip = null;
    }

    // =========================================================================
    // Constructor 2 — Carga desde la base de datos (todos los campos)
    // =========================================================================

    /**
     * Reconstruye una sanción a partir de los datos almacenados en la base de datos.
     *
     * <p>Este constructor es utilizado exclusivamente por la capa de persistencia
     * ({@code AbstractDatabase#mapPunishment}) para hidratar objetos desde ResultSets.</p>
     *
     * @param id         ID de la sanción en la DB.
     * @param playerUuid UUID del jugador.
     * @param playerName Nombre del jugador en el momento de la sanción.
     * @param type       Tipo de sanción.
     * @param reason     Razón registrada.
     * @param category   Categoría de infracción (puede ser {@code null}).
     * @param level      Nivel de escalada (0 = manual).
     * @param staffUuid  UUID del staff ({@code null} si fue la consola).
     * @param staffName  Nombre del staff o {@code "CONSOLE"}.
     * @param server     Servidor donde aplica.
     * @param startTime  Epoch millis de inicio.
     * @param endTime    Epoch millis de fin; {@code -1} = permanente.
     * @param active     Si la sanción está marcada como activa en la DB.
     * @param removedBy  Quien la removió ({@code null} si no fue removida).
     * @param removedAt  Epoch millis de remoción ({@code 0} si no fue removida).
     */
    public Punishment(
        int id,
        UUID playerUuid,
        String playerName,
        PunishmentType type,
        String reason,
        String category,
        int level,
        UUID staffUuid,
        String staffName,
        String server,
        long startTime,
        long endTime,
        boolean active,
        String removedBy,
        long removedAt
    ) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type = type;
        this.reason = reason;
        this.category = category;
        this.level = level;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.server = server;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = active;
        this.removedBy = removedBy;
        this.removedAt = removedAt;
        this.ip = null;
    }

    // =========================================================================
    // Métodos de estado / lógica de negocio
    // =========================================================================

    /**
     * Determina si esta sanción es permanente, es decir,
     * si {@code endTime} está establecido en {@code -1}.
     *
     * <p>Nota: {@link PunishmentType#KICK} y {@link PunishmentType#WARN}
     * son instantáneos por naturaleza; este método devuelve {@code true}
     * para ellos si {@code endTime == -1}, que es el valor por defecto.</p>
     *
     * @return {@code true} si {@code endTime == -1}.
     */
    public boolean isPermanent() {
        return endTime == -1L;
    }

    /**
     * Determina si la sanción ha expirado por haber transcurrido su duración.
     *
     * <p>Las sanciones permanentes ({@code endTime == -1}) nunca expiran
     * por tiempo.</p>
     *
     * @return {@code true} si no es permanente y el tiempo actual supera {@code endTime}.
     */
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= endTime;
    }

    /**
     * Calcula los milisegundos restantes hasta la expiración de la sanción.
     *
     * <ul>
     *   <li>Si la sanción es permanente, devuelve {@link Long#MAX_VALUE}.</li>
     *   <li>Si ya expiró, devuelve {@code 0}.</li>
     *   <li>En otro caso, devuelve {@code endTime - System.currentTimeMillis()}.</li>
     * </ul>
     *
     * @return Milisegundos restantes, {@code 0} si expiró, o {@link Long#MAX_VALUE} si es permanente.
     */
    public long getRemainingMillis() {
        if (isPermanent()) return Long.MAX_VALUE;
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    /**
     * Determina si la sanción está <strong>actualmente en vigor</strong>.
     *
     * <p>Una sanción está activa si cumple ambas condiciones:</p>
     * <ol>
     *   <li>El campo {@code active} de la DB es {@code true} (no fue removida manualmente).</li>
     *   <li>No ha expirado por tiempo ({@link #isExpired()} es {@code false}).</li>
     * </ol>
     *
     * @return {@code true} si la sanción sigue vigente.
     */
    public boolean isActive() {
        return active && !isExpired();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    /** @return ID de la sanción en la base de datos. {@code -1} si no fue persistida aún. */
    public int getId() {
        return id;
    }

    /** @return UUID del jugador sancionado. */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /** @return Nombre del jugador en el momento en que fue sancionado. */
    public String getPlayerName() {
        return playerName;
    }

    /** @return Tipo de sanción. */
    public PunishmentType getType() {
        return type;
    }

    /** @return Razón mostrada al jugador. */
    public String getReason() {
        return reason;
    }

    /**
     * @return Categoría de la infracción, o {@code null} si es una sanción manual
     *         sin categoría asignada.
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return Nivel dentro de la categoría de infracción.
     *         {@code 0} indica aplicación manual.
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return UUID del miembro del staff que aplicó la sanción,
     *         o {@code null} si proviene de la consola.
     */
    public UUID getStaffUuid() {
        return staffUuid;
    }

    /** @return Nombre del staff (o {@code "CONSOLE"}) que aplicó la sanción. */
    public String getStaffName() {
        return staffName;
    }

    /**
     * @return Nombre del servidor en el que aplica la sanción.
     *         {@code "global"} para toda la red.
     */
    public String getServer() {
        return server;
    }

    /** @return Epoch millis del momento en que se aplicó la sanción. */
    public long getStartTime() {
        return startTime;
    }

    /**
     * @return Epoch millis de expiración.
     *         {@code -1} si la sanción es permanente.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * @return {@code true} si el flag {@code active} en la DB está encendido.
     *         Para una verificación completa que incluya expiración, usar {@link #isActive()}.
     */
    public boolean getActiveFlag() {
        return active;
    }

    /**
     * @return Nombre del staff (o {@code "CONSOLE"}) que removió la sanción,
     *         o {@code null} si no fue removida manualmente.
     */
    public String getRemovedBy() {
        return removedBy;
    }

    /**
     * @return Epoch millis del momento en que fue removida la sanción.
     *         {@code 0} si no fue removida.
     */
    public long getRemovedAt() {
        return removedAt;
    }

    // =========================================================================
    // Setters (solo campos mutables post-creación)
    // =========================================================================

    /**
     * Establece el ID de la base de datos una vez que la sanción ha sido persistida.
     * Llamar únicamente desde la capa de persistencia.
     *
     * @param id ID generado por la DB.
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Marca la sanción como inactiva (removida manualmente).
     *
     * @param active Nuevo estado de activación.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Registra quién removió la sanción.
     *
     * @param removedBy Nombre del staff o {@code "CONSOLE"}.
     */
    public void setRemovedBy(String removedBy) {
        this.removedBy = removedBy;
    }

    /**
     * Registra el instante en que fue removida la sanción.
     *
     * @param removedAt Epoch millis de la remoción.
     */
    public void setRemovedAt(long removedAt) {
        this.removedAt = removedAt;
    }

    /**
     * Establece la dirección IP de la sanción (usado en IP-bans).
     * Llamar desde la capa de persistencia al cargar desde la BD.
     *
     * @param ip Dirección IP, o {@code null}.
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * @return Dirección IP asociada a la sanción, o {@code null} si no aplica.
     */
    public String getIp() {
        return ip;
    }

    // =========================================================================
    // toString
    // =========================================================================

    /**
     * Representación de depuración de la sanción.
     */
    @Override
    public String toString() {
        return (
            "Punishment{" +
            "id=" +
            id +
            ", player='" +
            playerName +
            "' (" +
            playerUuid +
            ")" +
            ", type=" +
            type +
            ", reason='" +
            reason +
            '\'' +
            ", category='" +
            category +
            '\'' +
            ", level=" +
            level +
            ", staff='" +
            staffName +
            '\'' +
            ", server='" +
            server +
            '\'' +
            ", startTime=" +
            startTime +
            ", endTime=" +
            endTime +
            ", active=" +
            active +
            ", removedBy='" +
            removedBy +
            '\'' +
            ", removedAt=" +
            removedAt +
            '}'
        );
    }
}
