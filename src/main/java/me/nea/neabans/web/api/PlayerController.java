package me.nea.neabans.web.api;

import io.javalin.http.Context;
import me.nea.neabans.NeaBans;
import me.nea.neabans.database.WebDatabase;
import me.nea.neabans.manager.PunishmentManager;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.web.model.WebUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Controller de información y acciones sobre jugadores.
 *
 * <h2>Rutas</h2>
 * <ul>
 *   <li>{@code GET  /api/player/{name}}               — Perfil del jugador.</li>
 *   <li>{@code POST /api/player/{name}/unban}          — Desbanear jugador.</li>
 *   <li>{@code POST /api/player/{name}/unmute}         — Desmutear jugador.</li>
 *   <li>{@code POST /api/player/{uuid}/clearhistory}   — Borrar historial completo.</li>
 * </ul>
 */
public class PlayerController {

    private final NeaBans plugin;
    private final Logger logger;

    public PlayerController(NeaBans plugin) {
        this.plugin  = plugin;
        this.logger  = plugin.getLogger();
    }

    // =========================================================================
    // GET /api/player/{name}
    // =========================================================================

    /**
     * Devuelve el perfil completo de un jugador.
     *
     * <p>Response (200):</p>
     * <pre>{
     *   "success": true,
     *   "data": {
     *     "uuid": "...",
     *     "name": "Steve",
     *     "activeBan": { ... } o null,
     *     "activeMute": { ... } o null,
     *     "isBanned": true,
     *     "isMuted": false,
     *     "history": [...],
     *     "historyCount": 5
     *   }
     * }</pre>
     *
     * <p>Response (404):</p>
     * <pre>{"success": false, "error": "Jugador no encontrado."}</pre>
     */
    public void getPlayer(Context ctx) {
        String name = ctx.pathParam("name");
        WebDatabase db = (WebDatabase) plugin.getDatabase();

        UUID uuid = db.getUUID(name);
        if (uuid == null) {
            ctx.status(404).json(Map.of(
                "success", false,
                "error", "Jugador '" + name + "' no encontrado en la base de datos."
            ));
            return;
        }

        String lastName    = db.getLastName(uuid);
        Punishment activeBan  = db.getActiveBan(uuid);
        Punishment activeMute = db.getActiveMute(uuid);

        List<Punishment> history = db.getHistory(uuid, 1, 20);
        int historyCount = db.getHistoryCount(uuid);

        List<Map<String, Object>> historyJson = history.stream()
            .map(DashboardController::punishmentToMap)
            .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid",         uuid.toString());
        data.put("name",         lastName != null ? lastName : name);
        data.put("activeBan",    activeBan  != null ? DashboardController.punishmentToMap(activeBan)  : null);
        data.put("activeMute",   activeMute != null ? DashboardController.punishmentToMap(activeMute) : null);
        data.put("isBanned",     activeBan  != null);
        data.put("isMuted",      activeMute != null);
        data.put("history",      historyJson);
        data.put("historyCount", historyCount);

        ctx.json(Map.of("success", true, "data", data));
    }

    // =========================================================================
    // POST /api/player/{name}/unban
    // =========================================================================

    /**
     * Desbanea a un jugador.
     *
     * <p>Requiere permiso {@code unban_players} o rol {@code is_admin}.</p>
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "message": "Steve ha sido desbaneado correctamente."}</pre>
     */
    public void unban(Context ctx) {
        String username = ctx.attribute("username");
        Boolean isAdmin = ctx.attribute("isAdmin");
        String playerName = ctx.pathParam("name");

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        WebUser webUser = db.getWebUser(username);

        if (webUser == null) {
            ctx.status(401).json(Map.of("success", false, "error", "Usuario de sesión no encontrado."));
            return;
        }

        // Verificar permiso
        if (!Boolean.TRUE.equals(isAdmin) && !webUser.isAdmin() && !webUser.isUnbanPlayers()) {
            ctx.status(403).json(Map.of(
                "success", false,
                "error", "No tienes el permiso 'unban_players'."
            ));
            return;
        }

        UUID targetUuid = db.getUUID(playerName);
        if (targetUuid == null) {
            ctx.status(404).json(Map.of(
                "success", false,
                "error", "Jugador '" + playerName + "' no encontrado en la base de datos."
            ));
            return;
        }

        PunishmentManager pm = plugin.getPunishmentManager();
        boolean unbanned = pm.unban("WebPanel:" + username, targetUuid);

        if (!unbanned) {
            ctx.status(400).json(Map.of(
                "success", false,
                "error", "El jugador '" + playerName + "' no tiene un ban activo."
            ));
            return;
        }

        logger.info("[WebPanel] " + username + " desbaneó a " + playerName);
        ctx.json(Map.of(
            "success", true,
            "message", "El jugador '" + playerName + "' ha sido desbaneado correctamente."
        ));
    }

    // =========================================================================
    // POST /api/player/{name}/unmute
    // =========================================================================

    /**
     * Desmutea a un jugador.
     *
     * <p>Requiere permiso {@code unmute_players} o rol {@code is_admin}.</p>
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "message": "Steve ha sido desmuteado correctamente."}</pre>
     */
    public void unmute(Context ctx) {
        String username   = ctx.attribute("username");
        Boolean isAdmin   = ctx.attribute("isAdmin");
        String playerName = ctx.pathParam("name");

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        WebUser webUser = db.getWebUser(username);

        if (webUser == null) {
            ctx.status(401).json(Map.of("success", false, "error", "Usuario de sesión no encontrado."));
            return;
        }

        // Verificar permiso
        if (!Boolean.TRUE.equals(isAdmin) && !webUser.isAdmin() && !webUser.isUnmutePlayers()) {
            ctx.status(403).json(Map.of(
                "success", false,
                "error", "No tienes el permiso 'unmute_players'."
            ));
            return;
        }

        UUID targetUuid = db.getUUID(playerName);
        if (targetUuid == null) {
            ctx.status(404).json(Map.of(
                "success", false,
                "error", "Jugador '" + playerName + "' no encontrado en la base de datos."
            ));
            return;
        }

        PunishmentManager pm = plugin.getPunishmentManager();
        boolean unmuted = pm.unmute("WebPanel:" + username, targetUuid);

        if (!unmuted) {
            ctx.status(400).json(Map.of(
                "success", false,
                "error", "El jugador '" + playerName + "' no tiene un mute activo."
            ));
            return;
        }

        logger.info("[WebPanel] " + username + " desmuteó a " + playerName);
        ctx.json(Map.of(
            "success", true,
            "message", "El jugador '" + playerName + "' ha sido desmuteado correctamente."
        ));
    }

    // =========================================================================
    // POST /api/player/{uuid}/clearhistory
    // =========================================================================

    /**
     * Borra el historial completo de sanciones de un jugador.
     *
     * <p>Operación irreversible. Requiere rol {@code is_admin}.</p>
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "message": "Historial de Steve borrado correctamente."}</pre>
     */
    public void clearHistory(Context ctx) {
        String username = ctx.attribute("username");
        Boolean isAdmin = ctx.attribute("isAdmin");

        // Solo admins pueden borrar historial completo
        if (!Boolean.TRUE.equals(isAdmin)) {
            WebDatabase db = (WebDatabase) plugin.getDatabase();
            WebUser webUser = db.getWebUser(username);
            if (webUser == null || !webUser.isAdmin()) {
                ctx.status(403).json(Map.of(
                    "success", false,
                    "error", "Se requiere rol de administrador para borrar el historial completo."
                ));
                return;
            }
        }

        String uuidParam = ctx.pathParam("uuid");
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(uuidParam);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("success", false, "error", "UUID inválido: " + uuidParam));
            return;
        }

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        String playerName = db.getLastName(targetUuid);
        if (playerName == null) playerName = uuidParam;

        db.clearHistory(targetUuid);

        logger.info("[WebPanel] " + username + " borró el historial completo de " + playerName + " (" + targetUuid + ")");
        ctx.json(Map.of(
            "success", true,
            "message", "El historial del jugador '" + playerName + "' ha sido borrado correctamente."
        ));
    }
}
