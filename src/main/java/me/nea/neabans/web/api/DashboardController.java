package me.nea.neabans.web.api;

import io.javalin.http.Context;
import me.nea.neabans.NeaBans;
import me.nea.neabans.database.WebDatabase;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.TimeUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller del dashboard del panel web.
 *
 * <h2>Rutas</h2>
 * <ul>
 *   <li>{@code GET /api/dashboard} — Devuelve estadísticas generales.</li>
 *   <li>{@code GET /api/servers}   — Devuelve lista de servidores únicos.</li>
 * </ul>
 */
public class DashboardController {

    private final NeaBans plugin;

    public DashboardController(NeaBans plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // GET /api/dashboard
    // =========================================================================

    /**
     * Devuelve las estadísticas principales del panel.
     *
     * <p>Response (200):</p>
     * <pre>{
     *   "success": true,
     *   "data": {
     *     "totalPunishments": 150,
     *     "activeBans": 12,
     *     "activeMutes": 5,
     *     "totalBans": 80,
     *     "totalMutes": 40,
     *     "totalKicks": 20,
     *     "totalWarns": 30,
     *     "recentPunishments": [...],
     *     "servers": ["survival", "skyblock"]
     *   }
     * }</pre>
     */
    public void getStats(Context ctx) {
        WebDatabase db = (WebDatabase) plugin.getDatabase();

        int totalPunishments = db.getTotalPunishments();
        int activeBans       = db.getTotalActiveBans();
        int activeMutes      = db.getTotalActiveMutes();
        int totalBans        = totalPunishments; // se recalcula abajo
        int totalMutes       = db.getTotalMutes();
        int totalKicks       = db.getTotalKicks();
        int totalWarns       = db.getTotalWarns();

        List<Punishment> recent   = db.getRecentPunishments(10);
        List<String>     servers  = db.getDistinctServers();

        // Convertir sanciones recientes a JSON
        List<Map<String, Object>> recentJson = recent.stream()
            .map(DashboardController::punishmentToMap)
            .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPunishments", totalPunishments);
        data.put("activeBans",       activeBans);
        data.put("activeMutes",      activeMutes);
        data.put("totalMutes",       totalMutes);
        data.put("totalKicks",       totalKicks);
        data.put("totalWarns",       totalWarns);
        data.put("recentPunishments", recentJson);
        data.put("servers",          servers);

        ctx.json(Map.of("success", true, "data", data));
    }

    // =========================================================================
    // GET /api/servers
    // =========================================================================

    /**
     * Devuelve la lista de servidores únicos en la tabla de sanciones.
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "data": ["survival", "skyblock", "lobby"]}</pre>
     */
    public void getServers(Context ctx) {
        WebDatabase db = (WebDatabase) plugin.getDatabase();
        List<String> servers = db.getDistinctServers();
        ctx.json(Map.of("success", true, "data", servers));
    }

    // =========================================================================
    // Helpers estáticos compartidos
    // =========================================================================

    /**
     * Convierte un {@link Punishment} a un {@link Map} apto para JSON.
     * Método público y estático para que otros controllers puedan reutilizarlo.
     *
     * @param p Sanción a convertir.
     * @return Map con todos los campos relevantes.
     */
    public static Map<String, Object> punishmentToMap(Punishment p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",          p.getId());
        map.put("playerName",  p.getPlayerName());
        map.put("playerUuid",  p.getPlayerUuid().toString());
        map.put("staffName",   p.getStaffName());
        map.put("staffUuid",   p.getStaffUuid() != null ? p.getStaffUuid().toString() : null);
        map.put("type",        p.getType().name());
        map.put("typeDisplay", p.getType().getDisplayName());
        map.put("reason",      p.getReason());
        map.put("category",    p.getCategory());
        map.put("server",      p.getServer());
        map.put("startTime",   p.getStartTime());
        map.put("endTime",     p.getEndTime());
        map.put("active",      p.isActive());
        map.put("permanent",   p.isPermanent());
        map.put("expired",     p.isExpired());
        map.put("removedBy",   p.getRemovedBy());
        map.put("removedAt",   p.getRemovedAt());
        map.put("level",       p.getLevel());

        // Tiempo restante legible
        if (!p.isPermanent() && p.isActive()) {
            map.put("remaining", TimeUtils.format(p.getRemainingMillis()));
        } else if (p.isPermanent() && p.isActive()) {
            map.put("remaining", "Permanente");
        } else {
            map.put("remaining", null);
        }

        return map;
    }
}
