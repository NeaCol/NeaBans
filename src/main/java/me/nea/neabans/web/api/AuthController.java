package me.nea.neabans.web.api;

import io.javalin.http.Context;
import me.nea.neabans.NeaBans;
import me.nea.neabans.database.WebDatabase;
import me.nea.neabans.web.WebAuthManager;
import me.nea.neabans.web.model.WebUser;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Controller de autenticación del panel web.
 *
 * <h2>Rutas</h2>
 * <ul>
 *   <li>{@code POST /api/auth/login} — Autentica un usuario y devuelve un JWT.</li>
 * </ul>
 *
 * <h2>Modo Bootstrap</h2>
 * <p>Si no existe ningún usuario en la base de datos, el primer intento de login
 * con cualquier usuario/contraseña crea automáticamente un usuario administrador
 * con el nombre y contraseña proporcionados. Esto facilita la configuración inicial.</p>
 */
public class AuthController {

    private final NeaBans plugin;
    private final WebAuthManager authManager;
    private final Logger logger;

    public AuthController(NeaBans plugin, WebAuthManager authManager) {
        this.plugin      = plugin;
        this.authManager = authManager;
        this.logger      = plugin.getLogger();
    }

    // =========================================================================
    // POST /api/auth/login
    // =========================================================================

    /**
     * Autentica un usuario web y devuelve un token JWT.
     *
     * <p>Request body (JSON):</p>
     * <pre>{"username": "admin", "password": "mypassword"}</pre>
     *
     * <p>Response exitosa (200):</p>
     * <pre>{
     *   "success": true,
     *   "token": "eyJhbGci...",
     *   "username": "admin",
     *   "isAdmin": true,
     *   "permissions": { ... }
     * }</pre>
     *
     * <p>Response de error (401):</p>
     * <pre>{"success": false, "error": "Credenciales incorrectas"}</pre>
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        // --- Parsear el body ---
        Map<String, String> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of(
                "success", false,
                "error", "Body inválido. Se esperaba JSON con 'username' y 'password'."
            ));
            return;
        }

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.status(400).json(Map.of(
                "success", false,
                "error", "Se requieren los campos 'username' y 'password'."
            ));
            return;
        }

        WebDatabase db = (WebDatabase) plugin.getDatabase();

        // --- Modo Bootstrap: primer usuario admin ---
        if (db.countWebUsers() == 0) {
            logger.info("[WebAuth] Modo bootstrap activo. Creando primer usuario admin: " + username);

            WebUser adminUser = new WebUser();
            adminUser.setUsername(username);
            adminUser.setPasswordHash(authManager.hashPassword(password));
            adminUser.setBanPlayers(true);
            adminUser.setUnbanPlayers(true);
            adminUser.setDeleteBanHistory(true);
            adminUser.setMutePlayers(true);
            adminUser.setUnmutePlayers(true);
            adminUser.setDeleteMuteHistory(true);
            adminUser.setEditAccounts(true);
            adminUser.setAdmin(true);
            adminUser.setCreatedAt(System.currentTimeMillis());

            WebUser created = db.createWebUser(adminUser);

            String token = authManager.generateToken(created.getUsername(), true);
            logger.info("[WebAuth] Usuario admin '" + created.getUsername() + "' creado exitosamente (bootstrap).");

            ctx.json(Map.of(
                "success",     true,
                "token",       token,
                "username",    created.getUsername(),
                "isAdmin",     true,
                "permissions", created.toJson(),
                "bootstrap",   true
            ));
            return;
        }

        // --- Autenticación normal ---
        WebUser user = db.getWebUser(username);
        if (user == null) {
            // Evitar timing attack: siempre verificar una contraseña ficticia
            authManager.verifyPassword("__dummy__", "$2a$12$dummy.hash.to.prevent.timing");
            logger.warning("[WebAuth] Intento de login fallido para usuario inexistente: " + username);
            ctx.status(401).json(Map.of("success", false, "error", "Credenciales incorrectas."));
            return;
        }

        if (!authManager.verifyPassword(password, user.getPasswordHash())) {
            logger.warning("[WebAuth] Contraseña incorrecta para usuario: " + username);
            ctx.status(401).json(Map.of("success", false, "error", "Credenciales incorrectas."));
            return;
        }

        // --- Login exitoso ---
        String token = authManager.generateToken(user.getUsername(), user.isAdmin());
        logger.info("[WebAuth] Login exitoso para usuario: " + username + " (admin=" + user.isAdmin() + ")");

        ctx.json(Map.of(
            "success",     true,
            "token",       token,
            "username",    user.getUsername(),
            "isAdmin",     user.isAdmin(),
            "permissions", user.toJson()
        ));
    }
}
