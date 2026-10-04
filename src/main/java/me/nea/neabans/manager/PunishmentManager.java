package me.nea.neabans.manager;

import java.util.Map;
import java.util.UUID;
import me.nea.neabans.NeaBans;
import me.nea.neabans.config.LangManager;
import me.nea.neabans.database.Database;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import me.nea.neabans.utils.TimeUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Núcleo del sistema de sanciones de NeaBans.
 *
 * <p>Todos los métodos son síncronos — la ejecución asíncrona es responsabilidad
 * de la capa de comandos y GUI que llama a este manager.</p>
 */
public final class PunishmentManager {

    private final NeaBans plugin;
    private final Database db;
    private final LangManager lang;

    public PunishmentManager(NeaBans plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabase();
        this.lang = plugin.getLang();
    }

    // ──────────────────────────────────────────────────
    //  BAN PERMANENTE
    // ──────────────────────────────────────────────────

    /**
     * Aplica un ban permanente. Retorna el id generado o -1 si ya está baneado.
     */
    public int ban(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        String reason,
        String category,
        int level
    ) {
        if (db.getActiveBan(targetUuid) != null) return -1;

        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.BAN,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            -1L // permanente
        );
        int id = db.addPunishment(p);

        kickIfOnline(targetUuid, buildBanScreen(p));
        broadcastStaff(
            "ban.broadcast",
            targetName,
            staffName,
            reason,
            TimeUtils.format(-1)
        );
        plugin.getNetworkManager().broadcastPunishment(p);
        broadcastWebSocket(p);
        return id;
    }

    // ──────────────────────────────────────────────────
    //  TEMPBAN
    // ──────────────────────────────────────────────────

    public int tempBan(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        long durationMillis,
        String reason,
        String category,
        int level
    ) {
        if (db.getActiveBan(targetUuid) != null) return -1;

        long end = System.currentTimeMillis() + durationMillis;
        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.TEMP_BAN,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            end
        );
        int id = db.addPunishment(p);

        kickIfOnline(targetUuid, buildBanScreen(p));
        broadcastStaff(
            "ban.broadcast-temp",
            targetName,
            staffName,
            reason,
            TimeUtils.format(durationMillis)
        );
        plugin.getNetworkManager().broadcastPunishment(p);
        return id;
    }

    // ──────────────────────────────────────────────────
    //  UNBAN
    // ──────────────────────────────────────────────────

    public boolean unban(String staffName, UUID targetUuid) {
        if (db.getActiveBan(targetUuid) == null) return false;
        db.deactivateByType(targetUuid, PunishmentType.BAN, staffName);
        db.deactivateByType(targetUuid, PunishmentType.TEMP_BAN, staffName);
        String name = db.getLastName(targetUuid);
        if (name != null) broadcastStaff(
            "ban.broadcast-unban",
            name,
            staffName,
            "",
            ""
        );
        plugin
            .getNetworkManager()
            .broadcastUnpunishment(targetUuid, PunishmentType.BAN, staffName);
        return true;
    }

    // ──────────────────────────────────────────────────
    //  MUTE PERMANENTE
    // ──────────────────────────────────────────────────

    public int mute(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        String reason,
        String category,
        int level
    ) {
        if (db.getActiveMute(targetUuid) != null) return -1;

        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.MUTE,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            -1L
        );
        int id = db.addPunishment(p);

        notifyIfOnline(
            targetUuid,
            lang.get(
                "mute.notify-target",
                Map.of("reason", reason, "remaining", "Permanente")
            )
        );
        broadcastStaff(
            "mute.broadcast",
            targetName,
            staffName,
            reason,
            "Permanente"
        );
        return id;
    }

    // ──────────────────────────────────────────────────
    //  TEMPMUTE
    // ──────────────────────────────────────────────────

    public int tempMute(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        long durationMillis,
        String reason,
        String category,
        int level
    ) {
        if (db.getActiveMute(targetUuid) != null) return -1;

        long end = System.currentTimeMillis() + durationMillis;
        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.TEMP_MUTE,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            end
        );
        int id = db.addPunishment(p);

        notifyIfOnline(
            targetUuid,
            lang.get(
                "mute.notify-target",
                Map.of(
                    "reason",
                    reason,
                    "remaining",
                    TimeUtils.format(durationMillis)
                )
            )
        );
        broadcastStaff(
            "mute.broadcast",
            targetName,
            staffName,
            reason,
            TimeUtils.format(durationMillis)
        );
        return id;
    }

    // ──────────────────────────────────────────────────
    //  UNMUTE
    // ──────────────────────────────────────────────────

    public boolean unmute(String staffName, UUID targetUuid) {
        if (db.getActiveMute(targetUuid) == null) return false;
        db.deactivateByType(targetUuid, PunishmentType.MUTE, staffName);
        db.deactivateByType(targetUuid, PunishmentType.TEMP_MUTE, staffName);
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null) online.sendMessage(
            lang.getPrefix() + lang.get("mute.removed-notify")
        );
        String name = db.getLastName(targetUuid);
        if (name != null) broadcastStaff(
            "mute.broadcast-unmute",
            name,
            staffName,
            "",
            ""
        );
        return true;
    }

    // ──────────────────────────────────────────────────
    //  KICK
    // ──────────────────────────────────────────────────

    /**
     * Expulsa a un jugador que DEBE estar online. Retorna -1 si no está online.
     */
    public int kick(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        String reason,
        String category,
        int level
    ) {
        Player online = Bukkit.getPlayer(targetUuid);
        if (online == null) return -1;

        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.KICK,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            0L // kicks no tienen duración
        );
        int id = db.addPunishment(p);

        String screen = lang.get(
            "kick.screen",
            Map.of(
                "reason",
                reason,
                "staff",
                staffName,
                "appeal",
                plugin.getConfig().getString("settings.appeal-link", "")
            )
        );
        // Kick debe ejecutarse en el main thread
        Bukkit.getScheduler().runTask(plugin, () ->
            online.kick(toLegacy(screen))
        );
        broadcastStaff("kick.broadcast", targetName, staffName, reason, "");
        return id;
    }

    // ──────────────────────────────────────────────────
    //  WARN
    // ──────────────────────────────────────────────────

    public int warn(
        UUID staffUuid,
        String staffName,
        UUID targetUuid,
        String targetName,
        String reason,
        String category,
        int level
    ) {
        Punishment p = new Punishment(
            targetUuid,
            targetName,
            PunishmentType.WARN,
            reason,
            category,
            level,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            0L
        );
        int id = db.addPunishment(p);
        int warnCount = db.getWarnCount(targetUuid);

        notifyIfOnline(
            targetUuid,
            lang.get(
                "warn.notify",
                Map.of("reason", reason, "count", String.valueOf(warnCount))
            )
        );
        broadcastStaff("warn.broadcast", targetName, staffName, reason, "");

        // Auto-acción si se alcanza max-warns
        int maxWarns = plugin.getConfig().getInt("settings.max-warns", 0);
        if (maxWarns > 0 && warnCount >= maxWarns) {
            String action = plugin
                .getConfig()
                .getString("settings.max-warns-action", "")
                .replace("{player}", targetName);
            if (!action.isBlank()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action)
                );
            }
        }
        return id;
    }

    // ──────────────────────────────────────────────────
    //  Helpers de consulta
    // ──────────────────────────────────────────────────

    public boolean isBanned(UUID uuid) {
        Punishment p = db.getActiveBan(uuid);
        return p != null && !p.isExpired();
    }

    public boolean isMuted(UUID uuid) {
        Punishment p = db.getActiveMute(uuid);
        return p != null && !p.isExpired();
    }

    public Punishment getActiveBan(UUID uuid) {
        return db.getActiveBan(uuid);
    }

    public Punishment getActiveMute(UUID uuid) {
        return db.getActiveMute(uuid);
    }

    // ──────────────────────────────────────────────────
    //  IP-BAN
    // ──────────────────────────────────────────────────

    /**
     * Aplica un IP-ban permanente. Retorna el id generado, o -1 si la IP ya está baneada.
     *
     * @param playerName Nombre del jugador (puede ser la IP si no se conoce).
     */
    public int ipBan(
        UUID staffUuid,
        String staffName,
        String ip,
        String playerName,
        String reason
    ) {
        if (db.getActiveIpBan(ip) != null) return -1;

        // UUID del jugador puede ser null (si solo se conoce la IP)
        UUID targetUuid = playerName != null ? db.getUUID(playerName) : null;
        String name = playerName != null ? playerName : ip;

        Punishment p = new Punishment(
            targetUuid != null ? targetUuid : java.util.UUID.randomUUID(),
            name,
            PunishmentType.IP_BAN,
            reason,
            "ip-ban",
            0,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            -1L
        );
        p.setIp(ip);
        int id = db.addPunishment(p);

        // Kickear a todos los online con esa IP
        kickByIp(ip, buildBanScreen(p));
        broadcastStaff(
            "ipban.broadcast",
            name,
            staffName,
            reason,
            "Permanente"
        );
        plugin.getNetworkManager().broadcastPunishment(p);
        return id;
    }

    /** Aplica un IP-ban temporal. */
    public int tempIpBan(
        UUID staffUuid,
        String staffName,
        String ip,
        String playerName,
        long durationMillis,
        String reason
    ) {
        if (db.getActiveIpBan(ip) != null) return -1;

        UUID targetUuid = playerName != null ? db.getUUID(playerName) : null;
        String name = playerName != null ? playerName : ip;
        long end = System.currentTimeMillis() + durationMillis;

        Punishment p = new Punishment(
            targetUuid != null ? targetUuid : java.util.UUID.randomUUID(),
            name,
            PunishmentType.TEMP_IP_BAN,
            reason,
            "ip-ban",
            0,
            staffUuid,
            staffName,
            plugin.getConfig().getString("settings.server-name", "global"),
            end
        );
        p.setIp(ip);
        int id = db.addPunishment(p);

        kickByIp(ip, buildBanScreen(p));
        broadcastStaff(
            "ipban.broadcast-temp",
            name,
            staffName,
            reason,
            me.nea.neabans.utils.TimeUtils.format(durationMillis)
        );
        plugin.getNetworkManager().broadcastPunishment(p);
        return id;
    }

    /** Elimina el IP-ban activo de una IP. Retorna false si no había ban. */
    public boolean unIpBan(String staffName, String ip) {
        if (db.getActiveIpBan(ip) == null) return false;
        db.deactivateIpBan(ip, staffName);
        plugin.getCache().invalidateIpBan(ip);
        broadcastStaff("ipban.broadcast-unban", ip, staffName, "", "");
        return true;
    }

    /** Construye el mensaje de ban-screen formateado. */
    public String buildBanScreen(Punishment p) {
        String key = p.isPermanent()
            ? "ban-screen.permanent"
            : "ban-screen.temporary";
        return lang.get(
            key,
            Map.of(
                "reason",
                p.getReason(),
                "staff",
                p.getStaffName(),
                "server",
                p.getServer(),
                "date",
                TimeUtils.formatDate(p.getStartTime()),
                "expire",
                p.isPermanent()
                    ? lang.get("general.permanent")
                    : TimeUtils.formatDate(p.getEndTime()),
                "remaining",
                p.isPermanent()
                    ? lang.get("general.permanent")
                    : TimeUtils.format(p.getRemainingMillis()),
                "appeal",
                plugin
                    .getConfig()
                    .getString(
                        "settings.appeal-link",
                        "https://discord.gg/YcjMtCPuKH"
                    )
            )
        );
    }

    /** Kickea al jugador con el ban-screen si está online. */
    private void kickIfOnline(UUID uuid, String screen) {
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) return;
        Bukkit.getScheduler().runTask(plugin, () ->
            online.kick(toLegacy(screen))
        );
    }

    /** Kickea a todos los jugadores con una IP específica (para IP-bans). */
    private void kickByIp(String ip, String screen) {
        if (ip == null) return;
        Bukkit.getOnlinePlayers()
            .stream()
            .filter(
                p ->
                    p.getAddress() != null &&
                    ip.equals(p.getAddress().getAddress().getHostAddress())
            )
            .forEach(p ->
                Bukkit.getScheduler().runTask(plugin, () ->
                    p.kick(toLegacy(screen))
                )
            );
    }

    /** Notifica con un mensaje si el jugador está online. */
    private void notifyIfOnline(UUID uuid, String message) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) online.sendMessage(message);
    }

    /** Broadcast solo a staff con permiso neabans.staff.notify. */
    private void broadcastStaff(
        String key,
        String player,
        String staff,
        String reason,
        String duration
    ) {
        if (
            !plugin
                .getConfig()
                .getBoolean("settings.broadcast-punishments", true)
        ) return;
        String msg =
            lang.getPrefix() +
            lang.get(
                key,
                Map.of(
                    "player",
                    player,
                    "staff",
                    staff,
                    "reason",
                    reason,
                    "duration",
                    duration
                )
            );
        boolean staffOnly = plugin
            .getConfig()
            .getBoolean("settings.staff-only-broadcast", false);
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (
                !staffOnly || p.hasPermission("neabans.staff.notify")
            ) p.sendMessage(msg);
        });
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    /** Envía el evento de nueva sanción al panel web en tiempo real (si está activo). */
    private void broadcastWebSocket(Punishment p) {
        var ws = plugin.getWebServer();
        if (ws != null) ws.getWsManager().broadcastPunishment(p);
    }

    /** Convierte un string con colores legacy (&x / §x) a Adventure Component. */
    private Component toLegacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(
            text.replace("§", "§")
        ); // ya viene con §
    }
}
