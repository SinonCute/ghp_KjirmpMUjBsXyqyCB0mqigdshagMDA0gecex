package net.minevn.minigameplaceholder;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;

public final class MinigamePlaceholder extends JavaPlugin implements PluginMessageListener, Listener {

    private static MinigamePlaceholder instance;
    private Map<String, Integer> serverPlayerCount;
    private boolean isRequesting;
    private int totalNeedUpdate;
    private long lastRequestTime;

    @Override
    public void onEnable() {
        instance = this;
        serverPlayerCount = new java.util.HashMap<>();
        lastRequestTime = 0;
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        this.getServer().getPluginManager().registerEvents(this, this);
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MinigameExpansion().register();
        }
        Bukkit.getScheduler().runTaskTimer(this, this::sendRequestServerList, 0, 20 * 60); // 1 minute
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (System.currentTimeMillis() - lastRequestTime > 1000) {
            sendRequestServerList();
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        switch (subchannel) {
            case "PlayerList": {
                String server = in.readUTF();
                String rawPlayerList = in.readUTF().trim();
                if (rawPlayerList.isEmpty()) {
                    serverPlayerCount.put(server, 0);
                    totalNeedUpdate--;
                    return;
                } else {
                    String[] playerList = rawPlayerList.split(", ");
                    serverPlayerCount.put(server, playerList.length);
                }
                totalNeedUpdate--;
                break;
            }
            case "GetServers": {
                String[] serverList = in.readUTF().split(", ");
                for (String server : serverList) {
                    if (server.startsWith("minigame")) {
                        requestPlayerCount(server);
                        totalNeedUpdate++;
                    }
                }
                break;
            }
            default: {
                getLogger().warning("Unknown subchannel: " + subchannel);
                break;
            }
        }
        if (totalNeedUpdate == 0) {
            lastRequestTime = System.currentTimeMillis();
            isRequesting = false;
        }
    }

    private void sendRequestServerList() {
        if (isRequesting) {
            return;
        }
        isRequesting = true;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServers");
        Bukkit.getOnlinePlayers().stream().findFirst().ifPresent(player -> player.sendPluginMessage(this, "BungeeCord", out.toByteArray()));
    }

    private void requestPlayerCount(String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerList");
        out.writeUTF(server);
        Bukkit.getOnlinePlayers().stream().findFirst().ifPresent(player -> player.sendPluginMessage(this, "BungeeCord", out.toByteArray()));
    }

    public static MinigamePlaceholder getInstance() {
        return instance;
    }

    public int getPlayerCount(String server) {
        return serverPlayerCount.getOrDefault(server, 0);
    }

    public int getTotalPlayerCount() {
        return serverPlayerCount.values().stream().mapToInt(Integer::intValue).sum();
    }
}