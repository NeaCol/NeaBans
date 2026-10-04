package me.nea.neabans.proxy.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import java.util.UUID;
import java.util.logging.Level;
import me.nea.neabans.proxy.shared.ProxyDatabase;
import me.nea.neabans.proxy.shared.ProxyPunishment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Listener de login para Velocity.
 *
 * Usa {@link PreLoginEvent} que es el evento estándar de Velocity para
 * denegar conexiones. Disponible en todas las versiones de Velocity 3.x.
 */
public final class VelocityLoginListener {

    private final NeaBansProxyVelocity plugin;

    public VelocityLoginListener(NeaBansProxyVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            try {
                handleAsync(event);
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "[NeaBansProxy] Error en onPreLogin: " + e.getMessage(),
                        e
                    );
            }
        });
    }

    private void handleAsync(PreLoginEvent event) {
        ProxyDatabase db = plugin.getDatabase();

        if (db == null || !db.isConnected()) {
            plugin
                .getLogger()
                .warning(
                    "[NeaBansProxy] BD no disponible — fail-open para " +
                        event.getUsername()
                );
            return;
        }

        String playerName = event.getUsername();
        String playerIp = event
            .getConnection()
            .getRemoteAddress()
            .getAddress()
            .getHostAddress();

        // ── 1. Verificar ban por UUID (buscamos UUID por nombre en nuestra BD) ──
        UUID uuid = db.getUUID(playerName);
        if (uuid != null) {
            ProxyPunishment ban = db.getActiveBan(uuid);
            if (ban != null) {
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        toComponent(resolveScreen(ban))
                    )
                );
                plugin
                    .getLogger()
                    .info(
                        "[NeaBansProxy] Bloqueado [BAN]: " +
                            playerName +
                            " | " +
                            ban.getReason()
                    );
                return;
            }
        }

        // ── 2. Verificar IP ban ──────────────────────────────────────────────
        ProxyPunishment ipBan = db.getActiveIpBan(playerIp);
        if (ipBan != null) {
            event.setResult(
                PreLoginEvent.PreLoginComponentResult.denied(
                    toComponent(resolveIpScreen(ipBan))
                )
            );
            plugin
                .getLogger()
                .info(
                    "[NeaBansProxy] Bloqueado [IP-BAN]: " +
                        playerName +
                        " (" +
                        playerIp +
                        ") | " +
                        ipBan.getReason()
                );
            return;
        }

        // ── 3. Jugador limpio → registrar IP si tenemos UUID ─────────────────
        if (uuid != null) {
            db.savePlayerIp(uuid, playerName, playerIp);
        }
    }

    private String resolveScreen(ProxyPunishment p) {
        String template = p.isPermanent()
            ? plugin.getBanScreenPermanent()
            : plugin.getBanScreenTemp();
        if (
            template != null && !template.isBlank()
        ) return p.formatBanScreenFromTemplate(
            template,
            plugin.getAppealLink()
        );
        return p.formatBanScreen(plugin.getAppealLink());
    }

    private String resolveIpScreen(ProxyPunishment p) {
        String template = plugin.getIpBanScreen();
        if (
            template != null && !template.isBlank()
        ) return p.formatBanScreenFromTemplate(
            template,
            plugin.getAppealLink()
        );
        return p.formatBanScreen(plugin.getAppealLink());
    }

    private Component toComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(
            text.replace("\\n", "\n")
        );
    }
}
