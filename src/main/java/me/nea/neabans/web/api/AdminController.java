package me.nea.neabans.web.api;

import io.javalin.http.Context;
import me.nea.neabans.NeaBans;
import me.nea.neabans.database.WebDatabase;
import me.nea.neabans.web.WebAuthManager;
import me.nea.neabans.web.model.WebUser;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Controller de gestión de cuentas web (solo administradores).
 *
 * <h2>Rutas</h2>
 * <ul>
 *   <li>{@code GET    /api/admin/users}      — Lista todos los usuarios web.</li>
 *   <li>{@code POST   /api/admin/users}      — Crea un nuevo usuario web.</li>
 *   <li>{@code PUT    /api/admin/users/{id}} — Actualiza permisos de un usuario.</li>
 *   <li>{@code DELETE /api/admin/users/{id}} — Elimina un usuario web.</li>
 * </ul>
 *
 * <p>Todas las rutas requieren rol {@code is_admin}.</p>
 */
public class AdminController {

    private final NeaBans plugin;
    private final WebAuthManager authManager;
    private final Logger logger;

    public AdminController(NeaBans plugin, WebAuthManager authManager) {
        this.plugin      = plugin;
        this.authManager = authManager;
        this.logger      = plugin.getLogger();
    }

    // =========================================================================
    // GET /api/admin/users
    // =========================================================================

    /**
     * Lista todos los usuarios web registrados.
     *
     * <p>Response (200):</p>
     * <pre>{
     *   "success": true,
     *   "data": [
     *     { "id": 1, "username": "admin", "admin": true, ... },
     *     { "id": 2, "username": "moderador", "admin": false, ... }
     *   ]
     * }</pre>
     */
    public void listUsers(Context ctx) {
        if (!isAdminRequest(ctx)) return;

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        List<WebUser> users = db.getAllWebUsers();
        List<Map<String, Object>> data = users.stream()
            .map(WebUser::toJson)
            .toList();

        ctx.json(Map.of("success", true, "data", data));
    }

    // =========================================================================
    // POST /api/admin/users
    // =========================================================================

    /**
     * Crea un nuevo usuario web.
     *
     * <p>Request body (JSON):</p>
     * <pre>{
     *   "username":          "moderador",
     *   "password":          "contraseña123",
     *   "banPlayers":        true,
     *   "unbanPlayers":      false,
     *   "deleteBanHistory":  false,
     *   "mutePlayers":       true,
     *   "unmutePlayers":     false,
     *   "deleteMuteHistory": false,
     *   "editAccounts":      false,
     *   "admin":             false
     * }</pre>
     *
     * <p>Response (201):</p>
     * <pre>{"success": true, "data": { ... nuevo usuario sin hash ... }}</pre>
     */
    @SuppressWarnings("unchecked")
    public void createUser(Context ctx) {
        if (!isAdminRequest(ctx)) return;

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("success", false, "error", "Body JSON inválido."));
            return;
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || username.isBlank()) {
            ctx.status(400).json(Map.of("success", false, "error", "El campo 'username' es requerido."));
            return;
        }
        if (password == null || password.isBlank()) {
            ctx.status(400).json(Map.of("success", false, "error", "El campo 'password' es requerido."));
            return;
        }
        if (username.length() > 32) {
            ctx.status(400).json(Map.of("success", false, "error", "El nombre de usuario no puede superar 32 caracteres."));
            return;
        }

        WebDatabase db = (WebDatabase) plugin.getDatabase();

        if (db.webUserExists(username)) {
            ctx.status(409).json(Map.of("success", false, "error", "Ya existe un usuario con el nombre '" + username + "'."));
            return;
        }

        WebUser newUser = new WebUser();
        newUser.setUsername(username);
        newUser.setPasswordHash(authManager.hashPassword(password));
        newUser.setBanPlayers(        getBool(body, "banPlayers",        false));
        newUser.setUnbanPlayers(      getBool(body, "unbanPlayers",      false));
        newUser.setDeleteBanHistory(  getBool(body, "deleteBanHistory",  false));
        newUser.setMutePlayers(       getBool(body, "mutePlayers",       false));
        newUser.setUnmutePlayers(     getBool(body, "unmutePlayers",     false));
        newUser.setDeleteMuteHistory( getBool(body, "deleteMuteHistory", false));
        newUser.setEditAccounts(      getBool(body, "editAccounts",      false));
        newUser.setAdmin(             getBool(body, "admin",             false));
        newUser.setCreatedAt(System.currentTimeMillis());

        WebUser created = db.createWebUser(newUser);

        logger.info("[WebPanel] " + ctx.attribute("username") +
            " creó nuevo usuario web: " + created.getUsername() +
            " (admin=" + created.isAdmin() + ")");

        ctx.status(201).json(Map.of("success", true, "data", created.toJson()));
    }

    // =========================================================================
    // PUT /api/admin/users/{id}
    // =========================================================================

    /**
     * Actualiza los permisos de un usuario web existente.
     *
     * <p>Request body: igual que {@code POST}, pero todos los campos son opcionales.
     * Solo se actualizan los permisos; no cambia username ni contraseña.</p>
     *
     * <p>Para cambiar la contraseña, incluir el campo {@code "password"}.</p>
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "data": { ... usuario actualizado ... }}</pre>
     */
    @SuppressWarnings("unchecked")
    public void updateUser(Context ctx) {
        if (!isAdminRequest(ctx)) return;

        int id = parseId(ctx.pathParam("id"));
        if (id < 0) {
            ctx.status(400).json(Map.of("success", false, "error", "ID inválido."));
            return;
        }

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("success", false, "error", "Body JSON inválido."));
            return;
        }

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        WebUser existing = db.getWebUserById(id);
        if (existing == null) {
            ctx.status(404).json(Map.of("success", false, "error", "Usuario con id=" + id + " no encontrado."));
            return;
        }

        // Actualizar solo los campos presentes en el body
        if (body.containsKey("banPlayers"))        existing.setBanPlayers(        getBool(body, "banPlayers",        existing.isBanPlayers()));
        if (body.containsKey("unbanPlayers"))       existing.setUnbanPlayers(      getBool(body, "unbanPlayers",      existing.isUnbanPlayers()));
        if (body.containsKey("deleteBanHistory"))   existing.setDeleteBanHistory(  getBool(body, "deleteBanHistory",  existing.isDeleteBanHistory()));
        if (body.containsKey("mutePlayers"))        existing.setMutePlayers(       getBool(body, "mutePlayers",       existing.isMutePlayers()));
        if (body.containsKey("unmutePlayers"))      existing.setUnmutePlayers(     getBool(body, "unmutePlayers",     existing.isUnmutePlayers()));
        if (body.containsKey("deleteMuteHistory"))  existing.setDeleteMuteHistory( getBool(body, "deleteMuteHistory", existing.isDeleteMuteHistory()));
        if (body.containsKey("editAccounts"))       existing.setEditAccounts(      getBool(body, "editAccounts",      existing.isEditAccounts()));
        if (body.containsKey("admin"))              existing.setAdmin(             getBool(body, "admin",             existing.isAdmin()));

        db.updateWebUser(existing);

        // Cambio de contraseña opcional
        String newPassword = (String) body.get("password");
        if (newPassword != null && !newPassword.isBlank()) {
            db.updateWebUserPassword(id, authManager.hashPassword(newPassword));
            logger.info("[WebPanel] " + ctx.attribute("username") + " cambió la contraseña del usuario id=" + id);
        }

        logger.info("[WebPanel] " + ctx.attribute("username") + " actualizó permisos del usuario id=" + id);
        ctx.json(Map.of("success", true, "data", existing.toJson()));
    }

    // =========================================================================
    // DELETE /api/admin/users/{id}
    // =========================================================================

    /**
     * Elimina un usuario web.
     *
     * <p>No se puede eliminar a uno mismo.</p>
     *
     * <p>Response (200):</p>
     * <pre>{"success": true, "message": "Usuario eliminado correctamente."}</pre>
     */
    public void deleteUser(Context ctx) {
        if (!isAdminRequest(ctx)) return;

        int id = parseId(ctx.pathParam("id"));
        if (id < 0) {
            ctx.status(400).json(Map.of("success", false, "error", "ID inválido."));
            return;
        }

        WebDatabase db = (WebDatabase) plugin.getDatabase();
        WebUser toDelete = db.getWebUserById(id);
        if (toDelete == null) {
            ctx.status(404).json(Map.of("success", false, "error", "Usuario con id=" + id + " no encontrado."));
            return;
        }

        // Protección: no eliminarse a uno mismo
        String requestingUser = ctx.attribute("username");
        if (toDelete.getUsername().equals(requestingUser)) {
            ctx.status(400).json(Map.of("success", false, "error", "No puedes eliminar tu propia cuenta."));
            return;
        }

        // Protección: no puede haber menos de 1 usuario admin
        if (toDelete.isAdmin() && db.countWebUsers() <= 1) {
            ctx.status(400).json(Map.of(
                "success", false,
                "error", "No se puede eliminar el último usuario administrador."
            ));
            return;
        }

        db.deleteWebUser(id);
        logger.info("[WebPanel] " + requestingUser + " eliminó al usuario web '" + toDelete.getUsername() + "' (id=" + id + ")");

        ctx.json(Map.of("success", true, "message", "Usuario '" + toDelete.getUsername() + "' eliminado correctamente."));
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Verifica que el usuario que hace la petición tenga rol de admin.
     * En caso contrario, responde con 403 y retorna false.
     */
    private boolean isAdminRequest(Context ctx) {
        Boolean isAdmin = ctx.attribute("isAdmin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            // Double-check en DB por si el token está desactualizado
            String username = ctx.attribute("username");
            if (username != null) {
                WebDatabase db = (WebDatabase) plugin.getDatabase();
                WebUser user = db.getWebUser(username);
                if (user != null && user.isAdmin()) return true;
            }
            ctx.status(403).json(Map.of(
                "success", false,
                "error", "Se requiere rol de administrador para esta operación."
            ));
            return false;
        }
        return true;
    }

    private int parseId(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    private boolean getBool(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n)  return n.intValue() != 0;
        return defaultValue;
    }
}
