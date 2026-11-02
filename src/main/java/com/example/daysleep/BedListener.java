package com.example.daysleep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BedListener implements Listener {

    private final JavaPlugin plugin;
    // Храним игроков, которые сейчас "спят днем", чтобы не сломать им выход из игры
    private final Set<UUID> sleepingPlayers = new HashSet<>();

    public BedListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !block.getType().name().endsWith("_BED")) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Если уже ночь или это не обычный мир — работаем как в ваниле
        if (world.getTime() >= 12000 || world.getEnvironment() != World.Environment.NORMAL) return;

        // Отменяем стандартное поведение (чтобы кровать не взорвалась днем)
        event.setCancelled(true);

        // 1. Принудительно укладываем игрока в кровать
        // Метод force = true позволяет лечь днем
        boolean success = player.sleep(block.getLocation(), true);
        if (!success) return;

        // Запоминаем, что игрок спит
        sleepingPlayers.add(player.getUniqueId());

        // 2. Сразу же (в следующем тике) меняем время на ночь,
        // чтобы сервер не выкинул игрока из кровати из-за дневного времени
        Bukkit.getScheduler().runTask(plugin, () -> {
            world.setTime(13000);
        });

        // 3. Ждем 3 секунды (60 тиков) и меняем время на утро.
        // В этот момент игрок "проснется" и встанет с кровати.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sleepingPlayers.contains(player.getUniqueId())) {
                world.setTime(0); // Наступает утро (или можете поставить 1000)
                sleepingPlayers.remove(player.getUniqueId());
            }
        }, 60L); // 60 тиков = 3 секунды
    }

    // Очистка, если игрок выйдет из игры пока "спит"
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sleepingPlayers.remove(event.getPlayer().getUniqueId());
    }
}
