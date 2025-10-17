package mc.Spacecat7773.spawnplugin1;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Spawnplugin1 extends JavaPlugin {

    private Location spawnLocation;

    // Track per-player countdown tasks and bossbars so we can cancel/cleanup.
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    @Override
    public void onEnable() {
        // load saved spawn from config if present
        if (getConfig().contains("spawn")) {
            spawnLocation = new Location(
                    Bukkit.getWorld(getConfig().getString("spawn.world")),
                    getConfig().getDouble("spawn.x"),
                    getConfig().getDouble("spawn.y"),
                    getConfig().getDouble("spawn.z"),
                    (float) getConfig().getDouble("spawn.yaw"),
                    (float) getConfig().getDouble("spawn.pitch")
            );
        }
        getLogger().info("SpawnPlugin1 enabled");
    }

    @Override
    public void onDisable() {
        // cancel any running tasks and remove bossbars when plugin disables
        for (BukkitTask task : countdownTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        for (Map.Entry<UUID, BossBar> e : bossBars.entrySet()) {
            BossBar bar = e.getValue();
            if (bar != null) {
                // remove any players from the bar before clearing
                bar.removeAll();
            }
        }
        countdownTasks.clear();
        bossBars.clear();
        getLogger().info("SpawnPlugin1 disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /setspawn
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            spawnLocation = player.getLocation();

            getConfig().set("spawn.world", player.getWorld().getName());
            getConfig().set("spawn.x", spawnLocation.getX());
            getConfig().set("spawn.y", spawnLocation.getY());
            getConfig().set("spawn.z", spawnLocation.getZ());
            getConfig().set("spawn.yaw", spawnLocation.getYaw());
            getConfig().set("spawn.pitch", spawnLocation.getPitch());
            saveConfig();

            player.sendMessage("§aSpawn point set!");
            return true;
        }

        // /spawn
        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (spawnLocation == null) {
                player.sendMessage("§cNo spawn has been set yet!");
                return true;
            }

            UUID uuid = player.getUniqueId();

            // If there's already a countdown running for this player, cancel it first
            if (countdownTasks.containsKey(uuid)) {
                BukkitTask existing = countdownTasks.remove(uuid);
                if (existing != null && !existing.isCancelled()) existing.cancel();
                BossBar existingBar = bossBars.remove(uuid);
                if (existingBar != null) {
                    existingBar.removePlayer(player);
                }
            }

            final int totalSeconds = 5;
            final int[] remaining = { totalSeconds }; // mutable inside lambda

            // create a bossbar for the player
            BossBar bar = Bukkit.createBossBar("Teleporting to spawn: " + remaining[0] + "s", BarColor.BLUE, BarStyle.SOLID, BarFlag.CREATE_FOG);
            bar.setProgress(1.0); // full initially
            bar.addPlayer(player);
            bossBars.put(uuid, bar);

            // send initial action-bar (Spigot-compatible)
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Teleporting to spawn in " + remaining[0] + "s..."));

            // schedule repeating task every 1 second (20 ticks)
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
                remaining[0]--;

                if (remaining[0] > 0) {
                    // update action bar (above hotbar) using Spigot ChatMessageType.ACTION_BAR
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Teleporting to spawn in " + remaining[0] + "s..."));

                    // update bossbar text and progress
                    double progress = (double) remaining[0] / (double) totalSeconds;
                    BossBar b = bossBars.get(uuid);
                    if (b != null) {
                        b.setTitle("Teleporting to spawn: " + remaining[0] + "s");
                        b.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    }
                } else {
                    // final tick: teleport and cleanup
                    player.teleport(spawnLocation);
                    player.sendMessage("§aTeleported to spawn!");

                    // remove action bar final message
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aTeleported to spawn!"));

                    // cleanup bossbar
                    BossBar b = bossBars.remove(uuid);
                    if (b != null) {
                        b.removePlayer(player);
                        b.setProgress(0.0);
                    }

                    // cancel this task and remove from map
                    BukkitTask t = countdownTasks.remove(uuid);
                    if (t != null && !t.isCancelled()) t.cancel();
                }
            }, 20L, 20L); // delay 1s then repeat every 1s

            // store the task so it can be cancelled if needed
            countdownTasks.put(uuid, task);

            return true;
        }

        return false;
    }
}
