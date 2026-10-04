package me.nea.neabans.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.nea.neabans.NeaBans;
import me.nea.neabans.cache.PunishmentCache;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.TimeUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bloquea chat y comandos de chat para jugadores muteados.
 *
 * <p>Usa el {@link PunishmentCache} para evitar queries a BD en cada mensaje.
 * Si hay un cache miss, consulta la BD y actualiza la caché.</p>
 *
 * <p><strong>EventPriority.HIGHEST</strong> — se ejecuta antes de que otros
 * plugins procesen el chat (EssentialsX format, etc.), garantizando que el
 * bloqueo ocurre primero.</p>
 */
public final class ChatListener implements Listener {

    /** Comandos de chat bloqueados también al estar muteado. */
    private static final List<String> BLOCKED_COMMANDS = List.of(
        "/me",
        "/say",
        "/tell",
        "/msg",
        "/reply",
        "/r",
        "/w",
        "/whisper",
        "/shout",
        "/g",
        "/local",
        "/channel"
    );

    private final NeaBans plugin;

    public ChatListener(NeaBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("neabans.bypass.mute")) return;

        Punishment mute = resolveMute(player.getUniqueId());
        if (mute == null) return;

        if (mute.isExpired()) {
            plugin.getDatabase().deactivatePunishment(mute.getId(), "SYSTEM");
            plugin.getCache().invalidateMute(player.getUniqueId());
            plugin.getCache().cacheMute(player.getUniqueId(), null);
            return;
        }

        event.setCancelled(true);
        sendMuteMessage(player, mute);
    }

    /**
     * Segundo listener en MONITOR — re-cancela el evento para plugins que usan
     * ignoreCancelled=false como DiscordSRV. Garantiza que ningún plugin
     * externo procese mensajes de jugadores muteados.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatMonitor(AsyncChatEvent event) {
        if (!event.isCancelled()) return; // solo actuar si ya fue cancelado
        Player player = event.getPlayer();
        if (player.hasPermission("neabans.bypass.mute")) return;

        Punishment mute = resolveMute(player.getUniqueId());
        if (mute != null && !mute.isExpired()) {
            // Forzar cancelación — esto bloquea DiscordSRV y similares
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        if (player.hasPermission("neabans.bypass.mute")) return;

        boolean blocked = BLOCKED_COMMANDS.stream().anyMatch(
            c -> command.startsWith(c + " ") || command.equals(c)
        );
        if (!blocked) return;

        Punishment mute = resolveMute(player.getUniqueId());
        if (mute == null) return;

        if (mute.isExpired()) {
            plugin.getDatabase().deactivatePunishment(mute.getId(), "SYSTEM");
            plugin.getCache().invalidateMute(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        sendMuteMessage(player, mute);
    }

    /**
     * Al salir del servidor, invalidamos la caché de mute del jugador.
     * Esto asegura que cuando vuelva, se vuelva a consultar la BD.
     * Evita que un mute aplicado mientras estaba offline quede sin detectarse.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getCache().invalidateMute(uuid);
        plugin.getCache().invalidateBan(uuid);
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    /**
     * Consulta el mute usando caché primero.
     * Si es un cache miss, va a la BD y guarda el resultado.
     */
    private Punishment resolveMute(UUID uuid) {
        PunishmentCache.CacheResult<Punishment> cached = plugin
            .getCache()
            .getMute(uuid);
        if (cached.isCached()) return cached.getValue(); // null = sin mute

        // Cache miss → BD
        Punishment mute = plugin.getDatabase().getActiveMute(uuid);
        plugin.getCache().cacheMute(uuid, mute); // guardar incluso null
        return mute;
    }

    private void sendMuteMessage(Player player, Punishment mute) {
        String remaining = mute.isPermanent()
            ? plugin.getLang().get("general.permanent")
            : TimeUtils.format(mute.getRemainingMillis());
        player.sendMessage(
            plugin
                .getLang()
                .get(
                    "mute.blocked",
                    Map.of("remaining", remaining, "reason", mute.getReason())
                )
        );
    }
}
