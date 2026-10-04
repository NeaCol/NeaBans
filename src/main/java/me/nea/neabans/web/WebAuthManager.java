package me.nea.neabans.web;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.nea.neabans.NeaBans;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Gestiona la autenticación del panel web de NeaBans.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Hashing seguro de contraseñas con BCrypt (cost=12).</li>
 *   <li>Generación y validación de JSON Web Tokens (JWT) firmados con HS256.</li>
 *   <li>Extracción de claims ({@code username}, {@code admin}) de los tokens.</li>
 * </ul>
 *
 * <h2>Ciclo de vida del token</h2>
 * <ol>
 *   <li>El usuario envía sus credenciales a {@code POST /api/auth/login}.</li>
 *   <li>Se verifica la contraseña con {@link #verifyPassword(String, String)}.</li>
 *   <li>Si es válida, se genera un token con {@link #generateToken(String, boolean)}.</li>
 *   <li>El frontend incluye el token en el header {@code Authorization: Bearer <token>}.</li>
 *   <li>El middleware lo valida con {@link #validateToken(String)} en cada petición.</li>
 * </ol>
 */
public class WebAuthManager {

    // =========================================================================
    // Constantes
    // =========================================================================

    /** Duración del token JWT: 24 horas en milisegundos. */
    private static final long TOKEN_EXPIRY_MS = 24L * 60L * 60L * 1_000L;

    /** Cost factor de BCrypt. 12 = ~300ms por hash (buena protección vs fuerza bruta). */
    private static final int BCRYPT_COST = 12;

    // =========================================================================
    // Campos
    // =========================================================================

    private final SecretKey signingKey;
    private final Logger logger;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Inicializa el manager leyendo la clave secreta desde la configuración del plugin.
     *
     * <p>Si la clave no está configurada, se genera una aleatoria en tiempo de ejecución.
     * <strong>Atención:</strong> una clave aleatoria significa que todos los tokens
     * quedan invalidados al reiniciar el servidor. Para persistencia entre reinicios,
     * configura {@code web.jwt-secret} en {@code config.yml}.</p>
     *
     * @param plugin Instancia del plugin principal de Paper.
     */
    public WebAuthManager(NeaBans plugin) {
        this.logger = plugin.getLogger();

        // Leer o generar la clave secreta
        String secret = plugin.getConfig().getString("web.jwt-secret", "");
        if (secret == null || secret.isBlank()) {
            secret = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            logger.warning(
                "[WebAuth] No hay 'web.jwt-secret' configurado. " +
                "Se usará una clave aleatoria que cambia con cada reinicio. " +
                "Configura 'web.jwt-secret' en config.yml para persistencia."
            );
        }

        // La clave debe tener al menos 256 bits (32 bytes) para HS256
        // Si es más corta, la rellenamos con el UUID del plugin
        while (secret.length() < 32) {
            secret = secret + UUID.randomUUID().toString();
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Keys.hmacShaKeyFor trunca o usa los primeros 32 bytes para HS256
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        logger.info("[WebAuth] JWT signing key configurada correctamente.");
    }

    // =========================================================================
    // Hashing de contraseñas
    // =========================================================================

    /**
     * Genera un hash BCrypt seguro de una contraseña en texto plano.
     *
     * <p>Usa un cost factor de {@value BCRYPT_COST}, que tarda ~300ms por hash,
     * haciendo ataques de fuerza bruta extremadamente costosos.</p>
     *
     * @param plaintext Contraseña en texto plano.
     * @return Hash BCrypt listo para almacenar en la base de datos.
     * @throws IllegalArgumentException si la contraseña está vacía.
     */
    public String hashPassword(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía.");
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, plaintext.toCharArray());
    }

    /**
     * Verifica que una contraseña en texto plano coincida con su hash BCrypt.
     *
     * @param plaintext Contraseña a verificar.
     * @param hash      Hash BCrypt almacenado en la base de datos.
     * @return {@code true} si la contraseña coincide con el hash.
     */
    public boolean verifyPassword(String plaintext, String hash) {
        if (plaintext == null || hash == null) return false;
        BCrypt.Result result = BCrypt.verifyer().verify(plaintext.toCharArray(), hash);
        return result.verified;
    }

    // =========================================================================
    // Generación y validación de tokens JWT
    // =========================================================================

    /**
     * Genera un JWT firmado con HS256 para el usuario autenticado.
     *
     * <p>Claims incluidos:</p>
     * <ul>
     *   <li>{@code sub} — Nombre de usuario (subject estándar de JWT).</li>
     *   <li>{@code admin} — {@code true} si el usuario tiene rol de admin.</li>
     *   <li>{@code exp} — Expiración: ahora + {@value TOKEN_EXPIRY_MS}ms (24 horas).</li>
     *   <li>{@code iat} — Timestamp de emisión.</li>
     * </ul>
     *
     * @param username Nombre de usuario del panel web.
     * @param isAdmin  Si el usuario tiene el rol de administrador.
     * @return Token JWT compacto listo para enviar al cliente.
     */
    public String generateToken(String username, boolean isAdmin) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
            .subject(username)
            .claim("admin", isAdmin)
            .issuedAt(new Date(nowMs))
            .expiration(new Date(nowMs + TOKEN_EXPIRY_MS))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Valida y parsea un JWT, devolviendo sus claims si la firma es válida
     * y el token no ha expirado.
     *
     * @param token Token JWT en formato compacto (sin el prefijo "Bearer ").
     * @return {@link Claims} del token.
     * @throws JwtException Si el token es inválido, ha expirado o la firma no coincide.
     */
    public Claims validateToken(String token) throws JwtException {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    // =========================================================================
    // Helpers de extracción de claims
    // =========================================================================

    /**
     * Extrae el nombre de usuario ({@code sub}) de un token JWT.
     *
     * @param token Token JWT válido.
     * @return Nombre de usuario, o {@code null} si el token es inválido.
     */
    public String getUsername(String token) {
        try {
            return validateToken(token).getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Extrae el claim {@code admin} de un token JWT.
     *
     * @param token Token JWT válido.
     * @return {@code true} si el token tiene el claim admin=true.
     */
    public boolean isAdmin(String token) {
        try {
            Boolean admin = validateToken(token).get("admin", Boolean.class);
            return Boolean.TRUE.equals(admin);
        } catch (JwtException e) {
            return false;
        }
    }
}
