package me.nea.neabans.manager;

import me.nea.neabans.NeaBans;
import me.nea.neabans.model.Punishment;
import me.nea.neabans.model.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.logging.Level;

/**
 * Manejo de plugin messaging con BungeeCord/Velocity para sincronización
 * de sanciones entre servidores de la red.
 *
 * <p>Si {@code network.enabled} es {@code false} en config.yml, todos los
 * métodos son no-ops y no se registra ningún canal.</p>
 *
 * <h2>Protocolo de mensajes</h2>
 * <ul>
 *   <li>Byte 0 = tipo: {@code 0} = nueva sanción, {@code 1} = remoción</li>
 *   <li>Para sanción: UUID, nombre, tipo, razón, endTime (-1=perm), server</li>
 *   <li>Para remoción: UUID, tipo, staffName</li>
 * </ul>
 */
public final class NetworkManager implements PluginMessageListener {

    private static final String CHANNEL = "neabans:sync";

    private final NeaBans plugin;
    private final boolean enabled;

    public NetworkManager(NeaBans plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("network.enabled", false);
    }

    // ──────────────────────────────────────────────────
    //  Ciclo de vida
    // ──────────────────────────────────────────────────

    /** Registra el canal de plugin messaging si el modo red está habilitado. */
    public void register() {
        if (!enabled) return;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("[Network] Canal '" + CHANNEL + "' registrado.");
    }

    /** Desregistra el canal al deshabilitar el plugin. */
    public void unregister() {
        if (!enabled) return;
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
    }

    // ──────────────────────────────────────────────────
    //  Envío de mensajes
    // ──────────────────────────────────────────────────

    /**
     * Notifica a todos los servidores de la red sobre una nueva sanción.
     * El mensaje viaja a través de BungeeCord usando el primer jugador online.
     */
    public void broadcastPunishment(Punishment p) {
        if (!enabled) return;
        Player relay = getRelayPlayer();
        if (relay == null) return;

        try {
            ByteArrayOutputStream bos  = new ByteArrayOutputStream();
            DataOutputStream      dos  = new DataOutputStream(bos);

            dos.writeByte(0); // ACTION: new punishment
            dos.writeUTF(p.getPlayerUuid().toString());
            dos.writeUTF(p.getPlayerName());
            dos.writeUTF(p.getType().name());
            dos.writeUTF(p.getReason());
            dos.writeLong(p.getEndTime());
            dos.writeUTF(p.getServer());
            dos.writeUTF(p.getStaffName());
            dos.writeInt(p.getId());

            relay.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[Network] Error al enviar sanción al proxy", e);
        }
    }

    /**
     * Notifica a todos los servidores de la red sobre la remoción de una sanción.
     */
    public void broadcastUnpunishment(java.util.UUID targetUuid,
                                       PunishmentType type,
                                       String staffName) {
        if (!enabled) return;
        Player relay = getRelayPlayer();
        if (relay == null) return;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream      dos = new DataOutputStream(bos);

            dos.writeByte(1); // ACTION: remove punishment
            dos.writeUTF(targetUuid.toString());
            dos.writeUTF(type.name());
            dos.writeUTF(staffName);

            relay.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[Network] Error al enviar remoción al proxy", e);
        }
    }

    // ──────────────────────────────────────────────────
    //  Recepción de mensajes
    // ──────────────────────────────────────────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try {
            DataInputStream dis    = new DataInputStream(new ByteArrayInputStream(message));
            byte            action = dis.readByte();

            switch (action) {
                case 0 -> handleIncomingPunishment(dis);
                case 1 -> handleIncomingUnpunishment(dis);
                default -> plugin.getLogger().warning(
                        "[Network] Acción desconocida recibida: " + action);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[Network] Error al leer mensaje entrante", e);
        }
    }

    private void handleIncomingPunishment(DataInputStream dis) throws IOException {
        java.util.UUID uuid      = java.util.UUID.fromString(dis.readUTF());
        String         name      = dis.readUTF();
        PunishmentType type      = PunishmentType.valueOf(dis.readUTF());
        String         reason    = dis.readUTF();
        long           endTime   = dis.readLong();
        String         server    = dis.readUTF();
        String         staffName = dis.readUTF();
        int            id        = dis.readInt();

        // Si el jugador está en este servidor y es un ban, expulsarlo
        if (type.isBan()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                Punishment dummy = new Punishment(
                        uuid, name, type, reason, null, 0,
                        null, staffName, server, endTime
                );
                // Kick en main thread
                final String screen = plugin.getPunishmentManager().buildBanScreen(dummy);
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.kick(net.kyori.adventure.text.serializer.legacy
                                .LegacyComponentSerializer.legacySection()
                                .deserialize(screen))
                );
            }
        }

        plugin.getLogger().info("[Network] Recibida sanción " + type.name()
                + " para " + name + " (id=" + id + ")");
    }

    private void handleIncomingUnpunishment(DataInputStream dis) throws IOException {
        java.util.UUID uuid      = java.util.UUID.fromString(dis.readUTF());
        PunishmentType type      = PunishmentType.valueOf(dis.readUTF());
        String         staffName = dis.readUTF();
        plugin.getLogger().info("[Network] Recibida remoción " + type.name()
                + " para " + uuid + " por " + staffName);
    }

    // ──────────────────────────────────────────────────
    //  Utilidades
    // ──────────────────────────────────────────────────

    /** Devuelve un jugador online para usar como relay de plugin messaging. */
    private Player getRelayPlayer() {
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }

    public boolean isEnabled() { return enabled; }
}
