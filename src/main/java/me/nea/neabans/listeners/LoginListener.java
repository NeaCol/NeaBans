package me.nea.neabans.listeners;

import java.util.UUID;
import me.nea.neabans.NeaBans;
import me.nea.neabans.cache.PunishmentCache;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener de login con máxima prioridad.
 *
 * <p><strong>EventPriority.HIGHEST</strong> — se ejecuta después de la mayoría de plugins
 * (AuthMe, EssentialsX, etc.) para garantizar que nuestro ban-screen
 * es el último en aplicarse y no puede ser sobreescrito.</p>
 *
 * <p>Flujo async (seguro para consultas a BD y caché):</p>
 * <ol>
 *   <li>Guardar IP del jugador en BD</li>
 *   <li>Comprobar IP-ban (caché → BD)</li>
 *   <li>Comprobar ban por UUID (caché → BD)</li>
 *   <li>Si baneado → disallow con ban-screen</li>
 *   <li>Si expiraste → limpiar BD y dejar pasar</li>
 * </ol>
 */
public final class LoginListener implements Listener {

    private final NeaBans plugin;

    public LoginListener(NeaBans plugin) {
        this.plugin = plugin;
    }

    /**
     * Prioridad HIGHEST para ejecutarse después de otros plugins.
     * ignoreCancelled=false para poder bloquear incluso si otro plugin ya canceló.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // Si ya fue rechazado por otro plugin (e.g. whitelist, mantenimiento) no procesar
        if (
            event.getLoginResult() ==
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST ||
            event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_FULL
        ) {
            return;
        }

        UUID uuid = event.getUniqueId();
        String name = event.getName();
        String ip = extractIp(event);

        // 1. Guardar/actualizar datos del jugador en BD (no bloquear si falla)
        try {
            plugin.getDatabase().savePlayerIp(uuid, name, ip);
        } catch (Exception e) {
            plugin
                .getLogger()
                .warning(
                    "[Login] Error guardando datos de " +
                        name +
                        ": " +
                        e.getMessage()
                );
        }

        PunishmentCache cache = plugin.getCache();

        // ─── 2. IP-Ban ────────────────────────────────────
        if (ip != null) {
            Punishment ipBan = resolveIpBan(cache, ip);
            if (ipBan != null) {
                if (ipBan.isExpired()) {
                    plugin.getDatabase().deactivateIpBan(ip, "SYSTEM");
                    cache.invalidateIpBan(ip);
                } else {
                    String screen = plugin
                        .getPunishmentManager()
                        .buildBanScreen(ipBan);
                    event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        toComponent(screen)
                    );
                    return;
                }
            }
        }

        // ─── 3. UUID-Ban ──────────────────────────────────
        Punishment ban = resolveBan(cache, uuid);
        if (ban != null) {
            if (ban.isExpired()) {
                plugin
                    .getDatabase()
                    .deactivatePunishment(ban.getId(), "SYSTEM");
                cache.invalidateBan(uuid);
                cache.cacheBan(uuid, null); // "no hay ban" cacheado
            } else {
                String screen = plugin
                    .getPunishmentManager()
                    .buildBanScreen(ban);
                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    toComponent(screen)
                );
                return;
            }
        }

        // Jugador limpio → cachear "sin ban" para ahorrar queries futuras
        if (!cache.getBan(uuid).isCached()) cache.cacheBan(uuid, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Refrescar nombre en BD al entrar en el hilo async
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String ip =
            event.getPlayer().getAddress() != null
                ? event.getPlayer().getAddress().getAddress().getHostAddress()
                : null;
        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getDatabase().savePlayerIp(uuid, name, ip);
                } catch (Exception ignored) {}
            });
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    /**
     * Consulta el ban por UUID: primero en caché, si no va a BD y actualiza caché.
     * Retorna null si no está baneado.
     */
    private Punishment resolveBan(PunishmentCache cache, UUID uuid) {
        PunishmentCache.CacheResult<Punishment> cached = cache.getBan(uuid);
        if (cached.isCached()) return cached.getValue(); // puede ser null = sin ban
        // Cache miss → consultar BD
        Punishment ban = plugin.getDatabase().getActiveBan(uuid);
        cache.cacheBan(uuid, ban);
        return ban;
    }

    /** Consulta el IP-ban: caché → BD. */
    private Punishment resolveIpBan(PunishmentCache cache, String ip) {
        PunishmentCache.CacheResult<Punishment> cached = cache.getIpBan(ip);
        if (cached.isCached()) return cached.getValue();
        Punishment ipBan = plugin.getDatabase().getActiveIpBan(ip);
        cache.cacheIpBan(ip, ipBan);
        return ipBan;
    }

    private String extractIp(AsyncPlayerPreLoginEvent event) {
        try {
            java.net.InetAddress addr = event.getAddress();
            return addr != null ? addr.getHostAddress() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Component toComponent(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(
            ColorUtils.color(text)
        );
    }
}
