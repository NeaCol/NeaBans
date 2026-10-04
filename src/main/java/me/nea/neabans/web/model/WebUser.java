package me.nea.neabans.web.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representa un usuario del panel web de NeaBans.
 *
 * <p>Los usuarios web son independientes de los jugadores de Minecraft;
 * se autentican con usuario/contraseña y tienen permisos granulares
 * para realizar acciones en el panel.</p>
 *
 * <h2>Permisos disponibles</h2>
 * <ul>
 *   <li>{@code banPlayers}         — Puede banear jugadores desde el panel.</li>
 *   <li>{@code unbanPlayers}       — Puede desbanear jugadores.</li>
 *   <li>{@code deleteBanHistory}   — Puede eliminar registros del historial de banes.</li>
 *   <li>{@code mutePlayers}        — Puede mutear jugadores.</li>
 *   <li>{@code unmutePlayers}      — Puede desmutear jugadores.</li>
 *   <li>{@code deleteMuteHistory}  — Puede eliminar registros del historial de mutes.</li>
 *   <li>{@code editAccounts}       — Puede editar permisos de otros usuarios web.</li>
 *   <li>{@code isAdmin}            — Acceso total: puede crear/borrar cuentas web y ver todo.</li>
 * </ul>
 *
 * <p><strong>Nunca serializar {@code passwordHash} al frontend.</strong>
 * Usar {@link #toJson()} para obtener un Map seguro sin el hash.</p>
 */
public class WebUser {

    // =========================================================================
    // Campos
    // =========================================================================

    /** ID autoincremental de la tabla {@code nb_web_users}. */
    private int id;

    /** Nombre de usuario único para login. */
    private String username;

    /**
     * Hash BCrypt de la contraseña.
     * <strong>Nunca incluir en respuestas JSON al frontend.</strong>
     */
    private String passwordHash;

    // --- Permisos ---

    /** Puede banear jugadores desde el panel. */
    private boolean banPlayers;

    /** Puede desbanear jugadores desde el panel. */
    private boolean unbanPlayers;

    /** Puede eliminar registros del historial de banes. */
    private boolean deleteBanHistory;

    /** Puede mutear jugadores desde el panel. */
    private boolean mutePlayers;

    /** Puede desmutear jugadores desde el panel. */
    private boolean unmutePlayers;

    /** Puede eliminar registros del historial de mutes. */
    private boolean deleteMuteHistory;

    /**
     * Puede editar permisos de otros usuarios web (excepto otros admins).
     * Implica poder ver la lista de usuarios.
     */
    private boolean editAccounts;

    /**
     * Acceso de administrador total.
     * Puede crear, editar y eliminar cuentas web, y tiene todos los permisos.
     */
    private boolean isAdmin;

    /** Timestamp (epoch millis) de creación de la cuenta. */
    private long createdAt;

    // =========================================================================
    // Constructor vacío (requerido para deserialización)
    // =========================================================================

    public WebUser() {}

    // =========================================================================
    // Constructor completo
    // =========================================================================

    /**
     * Constructor completo para reconstruir un usuario desde la base de datos.
     *
     * @param id                ID autoincremental.
     * @param username          Nombre de usuario.
     * @param passwordHash      Hash BCrypt de la contraseña.
     * @param banPlayers        Permiso: banear jugadores.
     * @param unbanPlayers      Permiso: desbanear jugadores.
     * @param deleteBanHistory  Permiso: eliminar historial de banes.
     * @param mutePlayers       Permiso: mutear jugadores.
     * @param unmutePlayers     Permiso: desmutear jugadores.
     * @param deleteMuteHistory Permiso: eliminar historial de mutes.
     * @param editAccounts      Permiso: editar cuentas web.
     * @param isAdmin           Rol de administrador total.
     * @param createdAt         Timestamp de creación (epoch millis).
     */
    public WebUser(
            int id,
            String username,
            String passwordHash,
            boolean banPlayers,
            boolean unbanPlayers,
            boolean deleteBanHistory,
            boolean mutePlayers,
            boolean unmutePlayers,
            boolean deleteMuteHistory,
            boolean editAccounts,
            boolean isAdmin,
            long createdAt
    ) {
        this.id                = id;
        this.username          = username;
        this.passwordHash      = passwordHash;
        this.banPlayers        = banPlayers;
        this.unbanPlayers      = unbanPlayers;
        this.deleteBanHistory  = deleteBanHistory;
        this.mutePlayers       = mutePlayers;
        this.unmutePlayers     = unmutePlayers;
        this.deleteMuteHistory = deleteMuteHistory;
        this.editAccounts      = editAccounts;
        this.isAdmin           = isAdmin;
        this.createdAt         = createdAt;
    }

    // =========================================================================
    // Serialización JSON (sin contraseña)
    // =========================================================================

    /**
     * Serializa este usuario a un {@link Map} apto para respuestas JSON.
     *
     * <p><strong>No incluye {@code passwordHash}</strong> por seguridad.</p>
     *
     * @return Map con todos los campos del usuario excepto el hash de contraseña.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",                id);
        map.put("username",          username);
        map.put("banPlayers",        banPlayers);
        map.put("unbanPlayers",      unbanPlayers);
        map.put("deleteBanHistory",  deleteBanHistory);
        map.put("mutePlayers",       mutePlayers);
        map.put("unmutePlayers",     unmutePlayers);
        map.put("deleteMuteHistory", deleteMuteHistory);
        map.put("editAccounts",      editAccounts);
        map.put("admin",             isAdmin);
        map.put("createdAt",         createdAt);
        return map;
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public int getId()                 { return id; }
    public String getUsername()        { return username; }
    public String getPasswordHash()    { return passwordHash; }
    public boolean isBanPlayers()      { return banPlayers; }
    public boolean isUnbanPlayers()    { return unbanPlayers; }
    public boolean isDeleteBanHistory(){ return deleteBanHistory; }
    public boolean isMutePlayers()     { return mutePlayers; }
    public boolean isUnmutePlayers()   { return unmutePlayers; }
    public boolean isDeleteMuteHistory(){ return deleteMuteHistory; }
    public boolean isEditAccounts()    { return editAccounts; }
    public boolean isAdmin()           { return isAdmin; }
    public long getCreatedAt()         { return createdAt; }

    // =========================================================================
    // Setters
    // =========================================================================

    public void setId(int id)                           { this.id = id; }
    public void setUsername(String username)            { this.username = username; }
    public void setPasswordHash(String passwordHash)    { this.passwordHash = passwordHash; }
    public void setBanPlayers(boolean banPlayers)       { this.banPlayers = banPlayers; }
    public void setUnbanPlayers(boolean unban)          { this.unbanPlayers = unban; }
    public void setDeleteBanHistory(boolean del)        { this.deleteBanHistory = del; }
    public void setMutePlayers(boolean mute)            { this.mutePlayers = mute; }
    public void setUnmutePlayers(boolean unmute)        { this.unmutePlayers = unmute; }
    public void setDeleteMuteHistory(boolean del)       { this.deleteMuteHistory = del; }
    public void setEditAccounts(boolean edit)           { this.editAccounts = edit; }
    public void setAdmin(boolean admin)                 { this.isAdmin = admin; }
    public void setCreatedAt(long createdAt)            { this.createdAt = createdAt; }

    // =========================================================================
    // toString
    // =========================================================================

    @Override
    public String toString() {
        return "WebUser{id=" + id + ", username='" + username + "', admin=" + isAdmin + "}";
    }
}
