package me.nea.neabans.web;

import io.javalin.websocket.WsContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.utils.TimeUtils;

/**
 * Gestiona las sesiones WebSocket activas del panel web.
 *
 * <p>Permite enviar eventos en tiempo real al frontend cuando ocurren
 * sanciones, desbaneos u otras acciones sin que el cliente tenga que
 * hacer polling.</p>
 *
 * <h2>Thread safety</h2>
 * Usa {@link ConcurrentHashMap} para el conjunto de sesiones activas,
 * ya que los eventos pueden enviarse desde distintos hilos del servidor.
 *
 * <h2>Protocolo de mensajes (JSON)</h2>
 * <pre>
 * // Nueva sanción
 * { "type": "NEW_PUNISHMENT", "data": { ... campos del Punishment ... } }
 *
 * // Remoción de sanción
 * { "type": "REMOVE_PUNISHMENT", "data": { "target": "...", "punishmentType": "..." } }
 *
 * // Heartbeat (enviado cada 30s para mantener la conexión viva)
 * { "type": "PING" }
 * </pre>
 */
public final class WebSocketManager {

    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();
    private final Logger logger;

    public WebSocketManager(Logger logger) {
        this.logger = logger;
    }

    // ──────────────────────────────────────────────────
    //  Gestión de sesiones
    // ──────────────────────────────────────────────────

    public void addSession(WsContext ctx) {
        sessions.add(ctx);
        logger.fine(
            "[WS] Cliente conectado | Sesiones activas: " + sessions.size()
        );
    }

    public void removeSession(WsContext ctx) {
        sessions.remove(ctx);
        logger.fine(
            "[WS] Cliente desconectado | Sesiones activas: " + sessions.size()
        );
    }

    /** @return Número de clientes web actualmente conectados. */
    public int getActiveCount() {
        return sessions.size();
    }

    // ──────────────────────────────────────────────────
    //  Broadcast de eventos
    // ──────────────────────────────────────────────────

    /**
     * Envía un evento de nueva sanción a todos los clientes conectados.
     * Llamar desde {@code PunishmentManager} justo después de persistir la sanción.
     */
    public void broadcastPunishment(Punishment p) {
        if (sessions.isEmpty()) return;

        String endTimeStr = p.isPermanent()
            ? "Permanente"
            : TimeUtils.formatDate(p.getEndTime());
        String remaining = p.isPermanent()
            ? "Permanente"
            : TimeUtils.format(p.getRemainingMillis());

        // Map.of() soporta máx 10 pares — usamos buildJson directamente
        String json = buildJson(
            "NEW_PUNISHMENT",
            new java.util.LinkedHashMap<String, String>() {
                {
                    put("id", String.valueOf(p.getId()));
                    put("playerName", safe(p.getPlayerName()));
                    put(
                        "playerUuid",
                        p.getPlayerUuid() != null
                            ? p.getPlayerUuid().toString()
                            : ""
                    );
                    put("staffName", safe(p.getStaffName()));
                    put(
                        "staffUuid",
                        p.getStaffUuid() != null
                            ? p.getStaffUuid().toString()
                            : ""
                    );
                    put("type", p.getType().name());
                    put("reason", safe(p.getReason()));
                    put(
                        "category",
                        p.getCategory() != null ? p.getCategory() : ""
                    );
                    put("server", safe(p.getServer()));
                    put("startTime", String.valueOf(p.getStartTime()));
                    put("endTime", String.valueOf(p.getEndTime()));
                    put("permanent", String.valueOf(p.isPermanent()));
                    put("expire", endTimeStr);
                    put("remaining", remaining);
                    put("ip", p.getIp() != null ? p.getIp() : "");
                }
            }
        );
        broadcast(json);
    }

    /**
     * Envía un evento de remoción de sanción a todos los clientes conectados.
     */
    public void broadcastRemoval(
        String targetName,
        String punishmentType,
        String staffName
    ) {
        if (sessions.isEmpty()) return;
        String json = buildJson(
            "REMOVE_PUNISHMENT",
            Map.of(
                "target",
                safe(targetName),
                "punishmentType",
                safe(punishmentType),
                "staffName",
                safe(staffName)
            )
        );
        broadcast(json);
    }

    /** Envía un ping a todos para mantener las conexiones activas. */
    public void broadcastPing() {
        if (sessions.isEmpty()) return;
        broadcast("{\"type\":\"PING\"}");
    }

    // ──────────────────────────────────────────────────
    //  Utilidades privadas
    // ──────────────────────────────────────────────────

    /** Envía un mensaje JSON a todas las sesiones activas. Elimina las cerradas. */
    private void broadcast(String message) {
        sessions.removeIf(ctx -> {
            try {
                if (!ctx.session.isOpen()) return true;
                ctx.send(message);
                return false;
            } catch (Exception e) {
                logger.fine(
                    "[WS] Sesión inválida eliminada: " + e.getMessage()
                );
                return true; // eliminar sesión rota
            }
        });
    }

    /** Construye un JSON simple: {"type":"...","data":{...}} */
    private String buildJson(String type, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(escape(type)).append("\",\"data\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) sb.append(",");
            sb
                .append("\"")
                .append(escape(entry.getKey()))
                .append("\":")
                .append("\"")
                .append(escape(entry.getValue()))
                .append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    /** Null-safe string. */
    private String safe(String s) {
        return s != null ? s : "";
    }

    /** Escapa caracteres especiales JSON básicos. */
    private String escape(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
