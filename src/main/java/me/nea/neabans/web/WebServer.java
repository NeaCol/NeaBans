package me.nea.neabans.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.nea.neabans.NeaBans;
import me.nea.neabans.web.api.AdminController;
import me.nea.neabans.web.api.AuthController;
import me.nea.neabans.web.api.DashboardController;
import me.nea.neabans.web.api.PlayerController;
import me.nea.neabans.web.api.PunishmentController;

/**
 * Servidor web embebido para el panel de administración de NeaBans.
 *
 * <p>Usa <a href="https://javalin.io/">Javalin 6.x</a> como HTTP server, con
 * Jetty como motor subyacente. Se inicia automáticamente con el plugin si
 * {@code web.enabled: true} en {@code config.yml}.</p>
 *
 * <h2>Configuración en config.yml</h2>
 * <pre>
 * web:
 *   enabled: false          # Cambiar a true para activar el panel
 *   port: 8080              # Puerto HTTP en el que escucha el servidor
 *   jwt-secret: ""          # Clave secreta para firmar JWT (generar una aleatoria segura)
 * </pre>
 *
 * <h2>Rutas de la API</h2>
 * <pre>
 * POST   /api/auth/login                       Autenticación (público)
 * GET    /api/dashboard                        Estadísticas del panel
 * GET    /api/servers                          Servidores únicos
 * GET    /api/punishments                      Lista paginada de sanciones
 * GET    /api/punishments/{id}                 Detalle de una sanción
 * DELETE /api/punishments/{id}/history         Eliminar del historial
 * GET    /api/player/{name}                    Perfil del jugador
 * POST   /api/player/{name}/unban              Desbanear
 * POST   /api/player/{name}/unmute             Desmutear
 * POST   /api/player/{uuid}/clearhistory       Borrar historial completo
 * GET    /api/admin/users                      Listar usuarios web
 * POST   /api/admin/users                      Crear usuario web
 * PUT    /api/admin/users/{id}                 Actualizar usuario web
 * DELETE /api/admin/users/{id}                 Eliminar usuario web
 * </pre>
 */
public class WebServer {

    // =========================================================================
    // Rutas públicas (no requieren JWT)
    // =========================================================================
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login");

    // =========================================================================
    // Campos
    // =========================================================================

    private final NeaBans plugin;
    private final Logger logger;
    private final WebAuthManager authManager;
    private WebSocketManager wsManager;
    private Javalin app;
    private boolean running = false;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Crea el WebServer. No inicia el servidor; llamar a {@link #start()} para ello.
     *
     * @param plugin Instancia del plugin principal.
     */
    public WebServer(NeaBans plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.authManager = new WebAuthManager(plugin);
        this.wsManager = new WebSocketManager(plugin.getLogger());
    }

    /**
     * Devuelve el {@link WebAuthManager} asociado a este servidor.
     * Útil para que otros componentes puedan acceder al auth manager.
     */
    public WebAuthManager getAuthManager() {
        return authManager;
    }

    /** Devuelve el gestor de WebSocket para enviar eventos en tiempo real. */
    public WebSocketManager getWsManager() {
        return wsManager;
    }

    /** IP detectada del servidor para mostrar en comandos y consola. */
    public String getDisplayIp() {
        return detectPublicIp();
    }

    // =========================================================================
    // Ciclo de vida
    // =========================================================================

    /**
     * Inicia el servidor web en el puerto configurado.
     *
     * <p>Si {@code web.enabled} es {@code false} en config.yml, este método
     * no hace nada.</p>
     *
     * <p>Si la carpeta {@code web/} no existe en el data folder del plugin,
     * la extrae del JAR automáticamente.</p>
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("web.enabled", false)) {
            logger.info(
                "[WebServer] Panel web deshabilitado (web.enabled=false). " +
                    "Cambia a true en config.yml para activarlo."
            );
            return;
        }

        int port = plugin.getConfig().getInt("web.port", 8080);
        String host = plugin.getConfig().getString("web.bind-host", "0.0.0.0");

        // Preparar directorio de archivos estáticos
        File webDir = new File(plugin.getDataFolder(), "web");
        ensureWebDirectory(webDir);

        // Crear controllers
        AuthController authController = new AuthController(plugin, authManager);
        DashboardController dashboardController = new DashboardController(
            plugin
        );
        PunishmentController punishmentController = new PunishmentController(
            plugin
        );
        PlayerController playerController = new PlayerController(plugin);
        AdminController adminController = new AdminController(
            plugin,
            authManager
        );

        // Crear instancia de Javalin
        final File finalWebDir = webDir;
        app = Javalin.create(config -> {
            // Archivos estáticos externos (frontend HTML/CSS/JS)
            if (finalWebDir.exists() && finalWebDir.isDirectory()) {
                config.staticFiles.add(staticConfig -> {
                    staticConfig.directory = finalWebDir.getAbsolutePath();
                    staticConfig.location = Location.EXTERNAL;
                });
            }

            // CORS: permite peticiones desde cualquier origen
            // (útil si el frontend está en un puerto diferente durante desarrollo)
            config.bundledPlugins.enableCors(cors ->
                cors.addRule(rule -> rule.anyHost())
            );

            // Silenciar el banner de Javalin en los logs
            config.showJavalinBanner = false;
        });

        // =====================================================================
        // Middleware de autenticación JWT para todas las rutas /api/*
        // =====================================================================
        app.before("/api/*", this::validateToken);

        // =====================================================================
        // Rutas — Autenticación (pública)
        // =====================================================================
        app.post("/api/auth/login", authController::login);

        // =====================================================================
        // Rutas — Dashboard
        // =====================================================================
        app.get("/api/dashboard", dashboardController::getStats);
        app.get("/api/servers", dashboardController::getServers);

        // =====================================================================
        // Rutas — Sanciones
        // =====================================================================
        app.get("/api/punishments", punishmentController::list);
        app.get("/api/punishments/{id}", punishmentController::get);
        app.delete(
            "/api/punishments/{id}/history",
            punishmentController::deleteFromHistory
        );

        // =====================================================================
        // Rutas — Jugadores
        // =====================================================================
        app.get("/api/player/{name}", playerController::getPlayer);
        app.post("/api/player/{name}/unban", playerController::unban);
        app.post("/api/player/{name}/unmute", playerController::unmute);
        app.post(
            "/api/player/{uuid}/clearhistory",
            playerController::clearHistory
        );

        // =====================================================================
        // Rutas — Administración de usuarios web
        // =====================================================================
        app.get("/api/admin/users", adminController::listUsers);
        app.post("/api/admin/users", adminController::createUser);
        app.put("/api/admin/users/{id}", adminController::updateUser);
        app.delete("/api/admin/users/{id}", adminController::deleteUser);

        // =====================================================================
        // WebSocket — Tiempo real para el panel web
        // =====================================================================
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                // Verificar token JWT enviado como query param: /ws?token=...
                String token = ctx.queryParam("token");
                if (token == null || token.isBlank()) {
                    ctx.session.close(4001, "Token requerido");
                    return;
                }
                try {
                    authManager.validateToken(token); // lanza JwtException si inválido
                    wsManager.addSession(ctx);
                } catch (io.jsonwebtoken.JwtException e) {
                    ctx.session.close(4001, "Token inválido");
                }
            });
            ws.onClose(ctx -> wsManager.removeSession(ctx));
            ws.onError(ctx -> wsManager.removeSession(ctx));
            // Los clientes pueden enviar mensajes (ignoramos por ahora — solo server→client)
            ws.onMessage(ctx -> {
                if ("PING".equals(ctx.message())) ctx.send(
                    "{\"type\":\"PONG\"}"
                );
            });
        });

        // =====================================================================
        // Manejo de errores HTTP
        // =====================================================================
        app.exception(UnauthorizedResponse.class, (e, ctx) ->
            ctx
                .status(401)
                .json(
                    Map.of(
                        "success",
                        false,
                        "error",
                        e.getMessage() != null
                            ? e.getMessage()
                            : "No autorizado."
                    )
                )
        );
        app.exception(Exception.class, (e, ctx) -> {
            logger.log(
                Level.WARNING,
                "[WebServer] Error no manejado en " + ctx.path(),
                e
            );
            ctx
                .status(500)
                .json(
                    Map.of(
                        "success",
                        false,
                        "error",
                        "Error interno del servidor."
                    )
                );
        });
        app.error(401, ctx ->
            ctx.json(Map.of("success", false, "error", "No autorizado."))
        );
        app.error(403, ctx ->
            ctx.json(Map.of("success", false, "error", "Acceso denegado."))
        );
        app.error(404, ctx -> {
            // Solo interceptar rutas /api/, dejar las demás para el SPA
            if (ctx.path().startsWith("/api/")) {
                ctx.json(
                    Map.of("success", false, "error", "Ruta no encontrada.")
                );
            }
        });

        // =====================================================================
        // Iniciar el servidor
        // =====================================================================
        try {
            app.start(host, port);
            running = true;

            // Detectar la IP del servidor para mostrarla en consola
            String displayIp = detectPublicIp();
            String panelUrl = "http://" + displayIp + ":" + port;

            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info(" 🌐 Panel Web NeaBans ACTIVO");
            logger.info(" URL:   " + panelUrl);
            logger.info(" Login: " + panelUrl + "/login.html");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception e) {
            logger.log(
                Level.SEVERE,
                "[WebServer] Error al iniciar el panel web en el puerto " +
                    port,
                e
            );
        }
    }

    /**
     * Detiene el servidor web de forma ordenada.
     * Llama a este método en {@code onDisable()} del plugin.
     */
    public void stop() {
        if (app != null && running) {
            try {
                app.stop();
                running = false;
                logger.info("[WebServer] Panel web detenido correctamente.");
            } catch (Exception e) {
                logger.log(
                    Level.WARNING,
                    "[WebServer] Error al detener el panel web",
                    e
                );
            }
        }
    }

    /**
     * @return {@code true} si el servidor está activo.
     */
    public boolean isRunning() {
        return running;
    }

    // =========================================================================
    // Middleware JWT
    // =========================================================================

    /**
     * Valida el token JWT en el header {@code Authorization: Bearer <token>}.
     *
     * <p>Las rutas en {@link #PUBLIC_PATHS} se saltan esta validación.
     * Si el token es inválido o falta, lanza {@link UnauthorizedResponse}
     * para que Javalin devuelva un 401.</p>
     *
     * @param ctx Contexto de la petición Javalin.
     * @throws UnauthorizedResponse Si el token falta, es inválido o ha expirado.
     */
    private void validateToken(Context ctx) {
        String path = ctx.path();

        // Permitir rutas públicas sin autenticación
        if (PUBLIC_PATHS.contains(path)) return;

        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Token de autenticación requerido.");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = authManager.validateToken(token);
            // Guardar claims en los atributos del contexto para uso en controllers
            ctx.attribute("username", claims.getSubject());
            ctx.attribute("isAdmin", claims.get("admin", Boolean.class));
        } catch (JwtException e) {
            throw new UnauthorizedResponse("Token inválido o expirado.");
        }
    }

    // =========================================================================
    // Preparación del directorio web
    // =========================================================================

    /**
     * Detecta la IP pública o local del servidor para mostrar la URL correcta en consola.
     *
     * <p>Estrategia:
     * <ol>
     *   <li>Intenta obtener la IP pública consultando un servicio externo ({@code https://api4.ipify.org})</li>
     *   <li>Si falla, usa la dirección de red local (non-loopback)</li>
     *   <li>Fallback: devuelve {@code "<IP-del-servidor>"}  como placeholder</li>
     * </ol>
     * </p>
     *
     * @return IP como String, nunca null
     */
    private String detectPublicIp() {
        // 1. Intentar IP pública via ipify (confiable, rápido, sólo IPv4)
        try {
            java.net.URL url = java.net.URI.create(
                "https://api4.ipify.org"
            ).toURL();
            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("User-Agent", "NeaBans/2.0");
            try (
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream())
                )
            ) {
                String ip = br.readLine();
                if (ip != null && !ip.isBlank()) return ip.trim();
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) {
            // Sin internet o timeout — continuar con IP local
        }

        // 2. IP de red local (no loopback)
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                java.net.NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs =
                    iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (
                        addr instanceof java.net.Inet4Address &&
                        !addr.isLoopbackAddress()
                    ) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Placeholder
        return "<IP-del-servidor>";
    }

    /**
     * Asegura que el directorio de archivos estáticos existe.
     *
     * <p>Si no existe, intenta extraerlos del JAR del plugin.
     * Si tampoco están en el JAR, crea un {@code index.html} placeholder.</p>
     *
     * @param webDir Directorio {@code <dataFolder>/web/}.
     */
    private void ensureWebDirectory(File webDir) {
        if (webDir.exists()) return;

        logger.info("[WebServer] Directorio 'web/' no encontrado. Creando...");
        webDir.mkdirs();

        // Intentar copiar recursos del JAR
        boolean extracted = tryExtractFromJar(webDir, "web/index.html");
        if (!extracted) {
            // Crear un index.html placeholder si no hay frontend en el JAR
            createPlaceholderIndex(webDir);
        }
    }

    /**
     * Intenta extraer un recurso del JAR del plugin al sistema de archivos.
     *
     * @param targetDir Directorio destino.
     * @param resource  Ruta del recurso dentro del JAR.
     * @return {@code true} si el recurso fue extraído correctamente.
     */
    private boolean tryExtractFromJar(File targetDir, String resource) {
        try (
            InputStream is = plugin
                .getClass()
                .getClassLoader()
                .getResourceAsStream(resource)
        ) {
            if (is == null) return false;
            File dest = new File(targetDir, "index.html");
            Files.copy(is, dest.toPath());
            logger.info(
                "[WebServer] Archivo '" + resource + "' extraído del JAR."
            );
            return true;
        } catch (IOException e) {
            logger.warning(
                "[WebServer] No se pudo extraer '" +
                    resource +
                    "' del JAR: " +
                    e.getMessage()
            );
            return false;
        }
    }

    /**
     * Crea un {@code index.html} de placeholder cuando no hay frontend disponible.
     *
     * @param webDir Directorio destino.
     */
    private void createPlaceholderIndex(File webDir) {
        File indexFile = new File(webDir, "index.html");
        try (FileWriter fw = new FileWriter(indexFile)) {
            fw.write(
                """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>NeaBans Web Panel</title>
                    <style>
                        body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }
                        h1   { color: #FF4D4D; }
                        code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; }
                        pre  { background: #f4f4f4; padding: 15px; border-radius: 8px; overflow-x: auto; }
                    </style>
                </head>
                <body>
                    <h1>NeaBans Web Panel</h1>
                    <p>El backend del panel web esta funcionando correctamente.</p>
                    <p>Para usar el panel web, coloca los archivos del frontend en la carpeta
                    <code>plugins/NeaBans/web/</code>.</p>
                    <h2>API disponible en:</h2>
                    <pre>POST   /api/auth/login
                GET    /api/dashboard
                GET    /api/servers
                GET    /api/punishments
                GET    /api/player/{name}
                GET    /api/admin/users</pre>
                </body>
                </html>
                """
            );
            logger.info(
                "[WebServer] index.html placeholder creado en: " +
                    indexFile.getAbsolutePath()
            );
        } catch (IOException e) {
            logger.warning(
                "[WebServer] No se pudo crear index.html: " + e.getMessage()
            );
        }
    }
}
