package me.nea.neabans.model;

/**
 * Enum que representa todos los tipos de sanción disponibles en NeaBans.
 *
 * <p>Cada tipo lleva consigo metadatos útiles como nombre de visualización
 * y color en formato HTML (#RRGGBB) para su uso en mensajes de chat
 * y componentes de Adventure/MiniMessage.</p>
 *
 * <p>Jerarquía:</p>
 * <ul>
 *   <li>Bans     → {@link #BAN}, {@link #TEMP_BAN}</li>
 *   <li>Mutes    → {@link #MUTE}, {@link #TEMP_MUTE}</li>
 *   <li>Otros    → {@link #KICK}, {@link #WARN}</li>
 * </ul>
 */
public enum PunishmentType {
    // -------------------------------------------------------------------------
    // Valores del enum
    // -------------------------------------------------------------------------

    /** Baneo permanente del servidor. */
    BAN("Ban permanente", "#FF4444"),

    /** Baneo temporal del servidor. */
    TEMP_BAN("Ban temporal", "#FF8844"),

    /** Silenciado permanente (no puede escribir en el chat). */
    MUTE("Mute permanente", "#AA44FF"),

    /** Silenciado temporal. */
    TEMP_MUTE("Mute temporal", "#CC88FF"),

    /** Expulsión inmediata sin historial de IP ni ban posterior. */
    KICK("Kick", "#FFDD44"),

    /** Advertencia registrada en el historial del jugador. */
    WARN("Advertencia", "#44AAFF"),

    /** Ban permanente por dirección IP. */
    IP_BAN("IP-Ban permanente", "#CC0000"),

    /** Ban temporal por dirección IP. */
    TEMP_IP_BAN("IP-Ban temporal", "#FF6600");

    // -------------------------------------------------------------------------
    // Campos internos
    // -------------------------------------------------------------------------

    /** Nombre legible para mostrar en mensajes y GUIs. */
    private final String displayName;

    /**
     * Color representativo en formato {@code #RRGGBB}.
     * Compatible con MiniMessage ({@code <color:#RRGGBB>}) y
     * componentes de Adventure.
     */
    private final String color;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    PunishmentType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    // -------------------------------------------------------------------------
    // Métodos helper
    // -------------------------------------------------------------------------

    /**
     * Indica si este tipo corresponde a alguna modalidad de ban
     * (permanente o temporal).
     *
     * @return {@code true} si es {@link #BAN} o {@link #TEMP_BAN}.
     */
    public boolean isBan() {
        return (
            this == BAN ||
            this == TEMP_BAN ||
            this == IP_BAN ||
            this == TEMP_IP_BAN
        );
    }

    /** Indica si este tipo es un ban por IP (permanente o temporal). */
    public boolean isIpBan() {
        return this == IP_BAN || this == TEMP_IP_BAN;
    }

    /**
     * Indica si este tipo corresponde a alguna modalidad de mute
     * (permanente o temporal).
     *
     * @return {@code true} si es {@link #MUTE} o {@link #TEMP_MUTE}.
     */
    public boolean isMute() {
        return this == MUTE || this == TEMP_MUTE;
    }

    /**
     * Indica si este tipo de sanción es temporal, es decir, tiene
     * una duración finita definida.
     *
     * @return {@code true} si es {@link #TEMP_BAN} o {@link #TEMP_MUTE}.
     */
    public boolean isTemporary() {
        return this == TEMP_BAN || this == TEMP_MUTE || this == TEMP_IP_BAN;
    }

    /**
     * Indica si este tipo de sanción es permanente (no expira con el tiempo).
     * {@link #KICK} y {@link #WARN} no son "permanentes" en el sentido de duración,
     * pero tampoco son temporales — se los trata como no-temporales aquí.
     *
     * @return {@code true} si <strong>no</strong> es temporal.
     */
    public boolean isPermanent() {
        return !isTemporary();
    }

    /**
     * Nombre de visualización legible para el tipo de sanción.
     * Útil en mensajes de chat, GUIs y logs.
     *
     * @return Nombre formateado en español (e.g. {@code "Ban temporal"}).
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Color representativo del tipo de sanción en formato {@code #RRGGBB}.
     *
     * <p>Ejemplo de uso con MiniMessage:</p>
     * <pre>
     *   String msg = "<color:" + type.getColor() + ">" + type.getDisplayName() + "</color>";
     * </pre>
     *
     * @return String con el color, e.g. {@code "#FF4444"}.
     */
    public String getColor() {
        return color;
    }

    // -------------------------------------------------------------------------
    // Utilidades estáticas
    // -------------------------------------------------------------------------

    /**
     * Convierte un String (case-insensitive) al enum correspondiente,
     * devolviendo {@code null} si no coincide ningún valor.
     *
     * <p>Prefiere este método en lugar de {@link #valueOf(String)} para
     * evitar lanzar {@link IllegalArgumentException} al leer datos de la DB.</p>
     *
     * @param value String a convertir.
     * @return El {@link PunishmentType} correspondiente, o {@code null}.
     */
    public static PunishmentType fromString(String value) {
        if (value == null) return null;
        for (PunishmentType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
