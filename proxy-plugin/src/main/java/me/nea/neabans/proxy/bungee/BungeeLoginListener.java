package me.nea.neabans.proxy.bungee;

import me.nea.neabans.proxy.shared.ProxyDatabase;
import me.nea.neabans.proxy.shared.ProxyPunishment;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener de login para <b>BungeeCord</b>.
 *
 * <p>Intercepta el evento {@link LoginEvent} (post-autenticación, pre-handoff al backend)
 * para verificar si el jugador está baneado antes de que entre a cualquier servidor.</p>
 *
 * <h2>Flujo de ejecución (asíncrono)</h2>
 * <ol>
 *   <li>Se registra un "intent" en el evento para aplazar su resolución.</li>
 *   <li>Se lanza una tarea asíncrona al scheduler de BungeeCord.</li>
 *   <li>En el hilo asíncrono se consultan UUID-ban e IP-ban a MySQL.</li>
 *   <li>Si hay ban → {@code setCancelled(true)} + {@code setCancelReason(banScreen)}.</li>
 *   <li>Si está limpio → {@code savePlayerIp} para registro de última IP.</li>
 *   <li>Se llama a {@code completeIntent} para reanudar el procesamiento del login.</li>
 * </ol>
 *
 * <p>El uso de intents garantiza que BungeeCord no continúe el handshake mientras
 * la consulta a la BD está en progreso, sin bloquear el hilo principal del proxy.</p>
 */
public final class BungeeLoginListener implements Listener {

    private final NeaBansProxyBungee plugin;

    public BungeeLoginListener(NeaBansProxyBungee plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Handler principal
    // =========================================================================

    /**
     * Intercepta el login con prioridad LOWEST para actuar antes que otros plugins.
     *
     * <p>{@link LoginEvent} provee:</p>
     * <ul>
     *   <li>{@code getConnection().getUniqueId()} – UUID en modo online.</li>
     *   <li>{@code getConnection().getAddress()} – InetSocketAddress del cliente.</li>
     *   <li>{@code getConnection().getName()} – nombre del jugador.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(LoginEvent event) {
        // Registrar intent asíncrono: BungeeCord esperará a completeIntent()
        // antes de continuar con el procesamiento del login
        event.registerIntent(plugin);

        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            try {
                handleLoginAsync(event);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[NeaBansProxy] Error inesperado en handleLoginAsync: " + e.getMessage(), e);
            } finally {
                // SIEMPRE completar el intent, incluso si ocurre un error,
                // para evitar que el jugador quede colgado infinitamente
                event.completeIntent(plugin);
            }
        });
    }

    // =========================================================================
    // Lógica de verificación (hilo asíncrono)
    // =========================================================================

    private void handleLoginAsync(LoginEvent event) {
        ProxyDatabase db = plugin.getDatabase();

        // Si la BD no está disponible, permitir el login (fail-open)
        if (db == null || !db.isConnected()) {
            plugin.getLogger().warning(
                    "[NeaBansProxy] BD no disponible, login de '"
                    + event.getConnection().getName() + "' permitido por fail-open.");
            return;
        }

        UUID   uuid       = event.getConnection().getUniqueId();
        String playerName = event.getConnection().getName();
        String playerIp   = event.getConnection().getAddress()
                                 .getAddress().getHostAddress();

        // ── 1. Verificar ban por UUID ────────────────────────────────────────
        ProxyPunishment ban = db.getActiveBan(uuid);
        if (ban != null) {
            String screen = resolveScreen(ban);
            event.setCancelReason(toComponent(screen));
            event.setCancelled(true);
            plugin.getLogger().info(
                    "[NeaBansProxy] Login bloqueado [BAN-UUID]: " + playerName
                    + " (" + uuid + ") | Razón: " + ban.getReason());
            return; // No continuar verificaciones
        }

        // ── 2. Verificar IP ban ──────────────────────────────────────────────
        ProxyPunishment ipBan = db.getActiveIpBan(playerIp);
        if (ipBan != null) {
            String screen = resolveIpScreen(ipBan);
            event.setCancelReason(toComponent(screen));
            event.setCancelled(true);
            plugin.getLogger().info(
                    "[NeaBansProxy] Login bloqueado [BAN-IP]: " + playerName
                    + " (" + playerIp + ") | Razón: " + ipBan.getReason());
            return;
        }

        // ── 3. Jugador limpio → registrar IP ─────────────────────────────────
        db.savePlayerIp(uuid, playerName, playerIp);
    }

    // =========================================================================
    // Resolución de plantillas
    // =========================================================================

    /**
     * Devuelve el ban screen formateado, usando la plantilla del config.yml
     * si está disponible, o la plantilla interna del POJO como fallback.
     */
    private String resolveScreen(ProxyPunishment p) {
        String appealLink = plugin.getAppealLink();
        String template = p.isPermanent()
                ? plugin.getBanScreenPermanent()
                : plugin.getBanScreenTemp();

        if (template != null && !template.isBlank()) {
            return p.formatBanScreenFromTemplate(template, appealLink);
        }
        return p.formatBanScreen(appealLink);
    }

    /**
     * Devuelve el IP-ban screen formateado.
     */
    private String resolveIpScreen(ProxyPunishment p) {
        String appealLink = plugin.getAppealLink();
        String template   = plugin.getIpBanScreen();

        if (template != null && !template.isBlank()) {
            return p.formatBanScreenFromTemplate(template, appealLink);
        }
        return p.formatBanScreen(appealLink);
    }

    // =========================================================================
    // Conversión de texto a BaseComponent
    // =========================================================================

    /**
     * Convierte una cadena con códigos {@code &} y saltos de línea {@code \n}
     * a un {@link BaseComponent} compatible con {@link LoginEvent#setCancelReason}.
     *
     * <p>Proceso:</p>
     * <ol>
     *   <li>{@link ChatColor#translateAlternateColorCodes} convierte {@code &c} → {@code §c}.</li>
     *   <li>{@link TextComponent#fromLegacyText} parsea los códigos de color legacy.</li>
     *   <li>Se envuelve en un {@link TextComponent} raíz con los componentes como extras.</li>
     * </ol>
     */
    private BaseComponent toComponent(String text) {
        // Normalizar saltos de línea literales que puedan venir del YAML
        String normalized = text.replace("\\n", "\n");
        // Traducir códigos & a §
        String colored    = ChatColor.translateAlternateColorCodes('&', normalized);
        // Parsear a BaseComponent[]
        BaseComponent[] parts = TextComponent.fromLegacyText(colored);

        TextComponent root = new TextComponent();
        for (BaseComponent part : parts) {
            root.addExtra(part);
        }
        return root;
    }
}
