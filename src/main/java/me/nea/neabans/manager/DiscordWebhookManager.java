package me.nea.neabans.manager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.TimeUtils;

/**
 * Gestiona el envío de notificaciones a Discord mediante Webhooks.
 *
 * <p>Todas las peticiones HTTP se ejecutan en un hilo asíncrono para no
 * bloquear el hilo principal del servidor. Se aplica un rate-limit básico
 * de 1 request/segundo para no sobrecargar la API de Discord.</p>
 *
 * <h2>Configuración en config.yml</h2>
 * <pre>
 * discord:
 *   enabled: false
 *   webhook-url: ""
 *   notify-ban: true
 *   notify-tempban: true
 *   notify-mute: true
 *   notify-tempmute: true
 *   notify-kick: false
 *   notify-warn: false
 *   notify-unban: true
 *   notify-unmute: true
 *   color-ban: "FF4D4D"
 *   color-tempban: "FF8C00"
 *   color-mute: "FFD700"
 *   color-tempmute: "F1C40F"
 *   color-kick: "3498DB"
 *   color-warn: "2ECC71"
 *   bot-name: "NeaBans"
 *   bot-avatar: "https://mc-heads.net/avatar/{uuid}/64"
 * </pre>
 */
public final class DiscordWebhookManager {

    // ─────────────────────────────────────────────────────────────────────
    //  Constantes
    // ─────────────────────────────────────────────────────────────────────

    /** Tiempo mínimo (ms) entre dos requests consecutivos al webhook. */
    private static final long RATE_LIMIT_MS = 1_000L;

    /** Timeout de conexión al endpoint de Discord (ms). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Timeout de lectura de la respuesta de Discord (ms). */
    private static final int READ_TIMEOUT_MS = 10_000;

    /** Versión del plugin que aparece en el footer del embed. */
    private static final String FOOTER_TEXT = "NeaBans v2.0";

    /** Emojis de título indexados por tipo de sanción. */
    private static final String EMOJI_BAN = "⛔";
    private static final String EMOJI_TEMP_BAN = "⏳";
    private static final String EMOJI_MUTE = "🔇";
    private static final String EMOJI_TEMP_MUTE = "🔇";
    private static final String EMOJI_KICK = "⚡";
    private static final String EMOJI_WARN = "⚠️";
    private static final String EMOJI_UNBAN = "✅";
    private static final String EMOJI_UNMUTE = "✅";

    // ─────────────────────────────────────────────────────────────────────
    //  Campos
    // ─────────────────────────────────────────────────────────────────────

    private final NeaBans plugin;

    /** Timestamp del último request enviado (para rate-limiting). */
    private volatile long lastSent = 0L;

    // ─── Config cacheada ─────────────────────────────────────────────────

    private boolean enabled;
    private String webhookUrl;

    private boolean notifyBan;
    private boolean notifyTempBan;
    private boolean notifyMute;
    private boolean notifyTempMute;
    private boolean notifyKick;
    private boolean notifyWarn;
    private boolean notifyUnban;
    private boolean notifyUnmute;

    private String colorBan;
    private String colorTempBan;
    private String colorMute;
    private String colorTempMute;
    private String colorKick;
    private String colorWarn;

    private String botName;
    private String botAvatarTemplate;

    // ─────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Crea una nueva instancia y carga la configuración desde config.yml.
     *
     * @param plugin Instancia principal del plugin.
     */
    public DiscordWebhookManager(NeaBans plugin) {
        this.plugin = plugin;
        reload();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Envía una notificación a Discord cuando se aplica una nueva sanción.
     *
     * <p>La petición se lanza en un hilo asíncrono; este método retorna
     * inmediatamente al llamador.</p>
     *
     * @param punishment Sanción recién aplicada.
     */
    public void notifyPunishment(Punishment punishment) {
        if (!isEnabled()) return;
        if (!shouldNotify(punishment.getType())) return;

        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    String title = buildPunishmentTitle(punishment.getType());
                    int color = hexToDecimal(
                        getColorForType(punishment.getType())
                    );
                    String thumbUrl = buildAvatarUrl(
                        punishment.getPlayerUuid().toString()
                    );
                    String duration = punishment.isPermanent()
                        ? "Permanente"
                        : TimeUtils.format(
                              punishment.getEndTime() -
                                  punishment.getStartTime()
                          );

                    String json = buildPunishmentEmbed(
                        title,
                        color,
                        thumbUrl,
                        punishment.getPlayerName(),
                        punishment.getStaffName(),
                        punishment.getReason(),
                        duration,
                        punishment.getServer(),
                        formatTimestamp(punishment.getStartTime())
                    );

                    sendWebhook(json);
                } catch (Exception e) {
                    plugin
                        .getLogger()
                        .log(
                            Level.WARNING,
                            "[DiscordWebhook] Error al enviar notificación de sanción: " +
                                e.getMessage()
                        );
                }
            });
    }

    /**
     * Envía una notificación a Discord cuando se remueve una sanción.
     *
     * <p>La petición se lanza en un hilo asíncrono; este método retorna
     * inmediatamente al llamador.</p>
     *
     * @param targetName Nombre del jugador cuya sanción fue removida.
     * @param type       Tipo de sanción removida (p.ej. "BAN", "MUTE").
     * @param removedBy  Nombre del staff (o "CONSOLE") que realizó la remoción.
     * @param reason     Razón de la remoción.
     */
    public void notifyUnpunishment(
        String targetName,
        String type,
        String removedBy,
        String reason
    ) {
        if (!isEnabled()) return;

        boolean isBanRemoval =
            type != null && type.toUpperCase().contains("BAN");
        boolean isMuteRemoval =
            type != null && type.toUpperCase().contains("MUTE");

        if (isBanRemoval && !notifyUnban) return;
        if (isMuteRemoval && !notifyUnmute) return;

        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    String emoji = isBanRemoval ? EMOJI_UNBAN : EMOJI_UNMUTE;
                    String label = isBanRemoval ? "Desbaneo" : "Desmuteo";
                    String title = emoji + " " + label;
                    // Color verde claro para remociones
                    int color = hexToDecimal("57F287");

                    String json = buildUnpunishmentEmbed(
                        title,
                        color,
                        targetName,
                        removedBy,
                        reason,
                        formatTimestamp(System.currentTimeMillis())
                    );

                    sendWebhook(json);
                } catch (Exception e) {
                    plugin
                        .getLogger()
                        .log(
                            Level.WARNING,
                            "[DiscordWebhook] Error al enviar notificación de remoción: " +
                                e.getMessage()
                        );
                }
            });
    }

    /**
     * Verifica si el webhook está habilitado y tiene una URL configurada.
     *
     * @return {@code true} si está listo para enviar notificaciones.
     */
    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    /**
     * Recarga la configuración del webhook desde config.yml sin necesidad de
     * reiniciar el plugin.
     */
    public void reload() {
        var cfg = plugin.getConfig();

        enabled = cfg.getBoolean("discord.enabled", false);
        webhookUrl = cfg.getString("discord.webhook-url", "");

        notifyBan = cfg.getBoolean("discord.notify-ban", true);
        notifyTempBan = cfg.getBoolean("discord.notify-tempban", true);
        notifyMute = cfg.getBoolean("discord.notify-mute", true);
        notifyTempMute = cfg.getBoolean("discord.notify-tempmute", true);
        notifyKick = cfg.getBoolean("discord.notify-kick", false);
        notifyWarn = cfg.getBoolean("discord.notify-warn", false);
        notifyUnban = cfg.getBoolean("discord.notify-unban", true);
        notifyUnmute = cfg.getBoolean("discord.notify-unmute", true);

        colorBan = cfg.getString("discord.color-ban", "FF4D4D");
        colorTempBan = cfg.getString("discord.color-tempban", "FF8C00");
        colorMute = cfg.getString("discord.color-mute", "FFD700");
        colorTempMute = cfg.getString("discord.color-tempmute", "F1C40F");
        colorKick = cfg.getString("discord.color-kick", "3498DB");
        colorWarn = cfg.getString("discord.color-warn", "2ECC71");

        botName = cfg.getString("discord.bot-name", "NeaBans");
        botAvatarTemplate = cfg.getString(
            "discord.bot-avatar",
            "https://mc-heads.net/avatar/{uuid}/64"
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Construcción del payload JSON
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Construye el payload JSON para un embed de nueva sanción.
     *
     * <p>El JSON se construye manualmente (sin librerías externas) para
     * mantener la dependencia mínima.</p>
     */
    private String buildPunishmentEmbed(
        String title,
        int color,
        String thumbnailUrl,
        String playerName,
        String staffName,
        String reason,
        String duration,
        String server,
        String date
    ) {
        return (
            "{" +
            "\"username\":" +
            jsonString(botName) +
            "," +
            "\"avatar_url\":" +
            jsonString(thumbnailUrl) +
            "," +
            "\"embeds\":[{" +
            "\"title\":" +
            jsonString(title) +
            "," +
            "\"color\":" +
            color +
            "," +
            "\"thumbnail\":{\"url\":" +
            jsonString(thumbnailUrl) +
            "}," +
            "\"fields\":[" +
            buildField("Jugador", playerName, true) +
            "," +
            buildField("Staff", staffName, true) +
            "," +
            buildField("Razón", reason, false) +
            "," +
            buildField("Duración", duration, true) +
            "," +
            buildField("Servidor", server, true) +
            "," +
            buildField("Fecha", date, true) +
            "]," +
            "\"footer\":{\"text\":" +
            jsonString(FOOTER_TEXT) +
            "}" +
            "}]" +
            "}"
        );
    }

    /**
     * Construye el payload JSON para un embed de remoción de sanción.
     */
    private String buildUnpunishmentEmbed(
        String title,
        int color,
        String playerName,
        String removedBy,
        String reason,
        String date
    ) {
        return (
            "{" +
            "\"username\":" +
            jsonString(botName) +
            "," +
            "\"embeds\":[{" +
            "\"title\":" +
            jsonString(title) +
            "," +
            "\"color\":" +
            color +
            "," +
            "\"fields\":[" +
            buildField("Jugador", playerName, true) +
            "," +
            buildField("Removido por", removedBy, true) +
            "," +
            buildField("Razón", reason, false) +
            "," +
            buildField("Fecha", date, true) +
            "]," +
            "\"footer\":{\"text\":" +
            jsonString(FOOTER_TEXT) +
            "}" +
            "}]" +
            "}"
        );
    }

    /**
     * Construye un objeto field de Discord embed en JSON.
     *
     * @param name   Nombre del campo.
     * @param value  Valor del campo.
     * @param inline Si el campo debe mostrarse en línea junto a otros.
     * @return Fragmento JSON del field.
     */
    private String buildField(String name, String value, boolean inline) {
        return (
            "{" +
            "\"name\":" +
            jsonString(name) +
            "," +
            "\"value\":" +
            jsonString(value) +
            "," +
            "\"inline\":" +
            inline +
            "}"
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Envío HTTP
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Envía el payload JSON al webhook de Discord usando {@link HttpURLConnection}.
     *
     * <p>Aplica rate-limiting básico: si el tiempo transcurrido desde el
     * último envío es inferior a {@link #RATE_LIMIT_MS}, el hilo actual
     * duerme el tiempo restante antes de enviar.</p>
     *
     * @param jsonPayload Cuerpo de la petición HTTP (JSON).
     * @throws Exception Si se produce cualquier error de red o I/O.
     */
    private void sendWebhook(String jsonPayload) throws Exception {
        // ── Rate-limit básico ──────────────────────────────────────────
        long now = System.currentTimeMillis();
        long elapsed = now - lastSent;
        if (elapsed < RATE_LIMIT_MS) {
            long waitMs = RATE_LIMIT_MS - elapsed;
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                plugin
                    .getLogger()
                    .warning(
                        "[DiscordWebhook] Hilo interrumpido en rate-limit."
                    );
                return;
            }
        }

        // ── Conexión ──────────────────────────────────────────────────
        URL url = URI.create(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            );
            conn.setRequestProperty("User-Agent", "NeaBans-Webhook/2.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int responseCode = conn.getResponseCode();
            lastSent = System.currentTimeMillis();

            // Discord devuelve 204 No Content para webhooks correctos
            if (responseCode != 200 && responseCode != 204) {
                plugin
                    .getLogger()
                    .warning(
                        "[DiscordWebhook] Respuesta inesperada del webhook: HTTP " +
                            responseCode
                    );
            }
        } finally {
            conn.disconnect();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Determina si se debe enviar notificación para el tipo de sanción dado.
     *
     * @param type Tipo de sanción.
     * @return {@code true} si la notificación está habilitada para ese tipo.
     */
    private boolean shouldNotify(PunishmentType type) {
        return switch (type) {
            case BAN -> notifyBan;
            case TEMP_BAN -> notifyTempBan;
            case MUTE -> notifyMute;
            case TEMP_MUTE -> notifyTempMute;
            case KICK -> notifyKick;
            case WARN -> notifyWarn;
            case IP_BAN, TEMP_IP_BAN -> plugin
                .getConfig()
                .getBoolean("discord.notify-ipban", true);
        };
    }

    /**
     * Obtiene el color hexadecimal (sin {@code #}) configurado para un tipo.
     *
     * @param type Tipo de sanción.
     * @return String con el color hex, p.ej. {@code "FF4D4D"}.
     */
    private String getColorForType(PunishmentType type) {
        return switch (type) {
            case BAN -> colorBan;
            case TEMP_BAN -> colorTempBan;
            case MUTE -> colorMute;
            case TEMP_MUTE -> colorTempMute;
            case KICK -> colorKick;
            case WARN -> colorWarn;
            case IP_BAN, TEMP_IP_BAN -> plugin
                .getConfig()
                .getString("discord.color-ipban", "CC0000");
        };
    }

    /**
     * Construye el título del embed con emoji según el tipo de sanción.
     *
     * @param type Tipo de sanción.
     * @return Título del embed (emoji + nombre).
     */
    private String buildPunishmentTitle(PunishmentType type) {
        return switch (type) {
            case BAN -> EMOJI_BAN + " Nuevo Ban";
            case TEMP_BAN -> EMOJI_TEMP_BAN + " Nuevo TempBan";
            case MUTE -> EMOJI_MUTE + " Nuevo Mute";
            case TEMP_MUTE -> EMOJI_TEMP_MUTE + " Nuevo TempMute";
            case KICK -> EMOJI_KICK + " Nuevo Kick";
            case WARN -> EMOJI_WARN + " Nueva Advertencia";
            case IP_BAN -> "🚫 Nuevo IP-Ban";
            case TEMP_IP_BAN -> "🚫 Nuevo IP-TempBan";
        };
    }

    /**
     * Construye la URL del avatar del jugador usando la plantilla configurada.
     *
     * @param uuid UUID del jugador (sin guiones o con guiones).
     * @return URL de la imagen de la skin del jugador.
     */
    private String buildAvatarUrl(String uuid) {
        return botAvatarTemplate.replace("{uuid}", uuid);
    }

    /**
     * Convierte un color hexadecimal (sin {@code #}) a su valor decimal
     * equivalente para el campo {@code color} de los embeds de Discord.
     *
     * <p>Ejemplo: {@code "FF4D4D"} → {@code 16728397}.</p>
     *
     * @param hex String hexadecimal de 6 caracteres (sin {@code #}).
     * @return Valor entero del color.
     */
    private int hexToDecimal(String hex) {
        try {
            // Long.parseLong para manejar valores como "FF4D4D" sin overflow
            return (int) Long.parseLong(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            plugin
                .getLogger()
                .warning(
                    "[DiscordWebhook] Color hex inválido: '" +
                        hex +
                        "', usando gris."
                );
            return 0x95A5A6; // gris Discord por defecto
        }
    }

    /**
     * Formatea un timestamp de epoch millis a {@code "dd/MM/yyyy HH:mm"}.
     *
     * @param timestampMs Timestamp en milisegundos.
     * @return Fecha formateada.
     */
    private String formatTimestamp(long timestampMs) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestampMs));
    }

    /**
     * Escapa un String para su uso seguro como valor de cadena JSON.
     *
     * <p>Escapa los caracteres especiales: {@code "}, {@code \},
     * saltos de línea, retornos de carro y tabulaciones.</p>
     *
     * @param value String a escapar. Si es {@code null}, devuelve {@code "null"}.
     * @return El string rodeado de comillas dobles y correctamente escapado.
     */
    private String jsonString(String value) {
        if (value == null) return "\"null\"";
        return (
            "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") +
            "\""
        );
    }
}
