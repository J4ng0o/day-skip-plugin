package com.example.daysleep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BedListener implements Listener {

    private final JavaPlugin plugin;
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

        // Если ночь или не обычный мир - не вмешиваемся
        if (world.getTime() >= 12000 || world.getEnvironment() != World.Environment.NORMAL) return;

        event.setCancelled(true);

        // 1. Укладываем игрока (force = true позволяет лечь днем)
        boolean success = player.sleep(block.getLocation(), true);
        if (!success) return;

        sleepingPlayers.add(player.getUniqueId());

        // 2. Надпись БЕЛЫМ цветом (NamedTextColor.WHITE)
        Component actionBarMessage = Component.text("Вы проспите весь день...", NamedTextColor.WHITE);
        player.sendActionBar(actionBarMessage);

        // 3. Сохраняем старый геймрул и выключаем течение времени
        Boolean oldDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        
        // 4. Принудительно ставим время на ночь (чтобы сервер не выкинул игрока)
        world.setTime(13000);

        // 5. Удерживаем время и ActionBar
        BukkitRunnable timeLocker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !sleepingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                
                // Жестко фиксируем время, чтобы ванильный сервер не мог его сбросить
                world.setTime(13000);
                player.sendActionBar(actionBarMessage);
            }
        };
        timeLocker.runTaskTimer(plugin, 0L, 1L);

        // 6. Через 3 секунды будим игрока
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sleepingPlayers.contains(player.getUniqueId())) {
                timeLocker.cancel(); 
                
                // Возвращаем геймрул
                if (oldDaylightCycle != null) {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, oldDaylightCycle);
                }
                
                // Ставим утро (1000 тиков - это стандартное утро в Minecraft, 0 - это тоже утро, но 1000 красивее)
                world.setTime(1000); 
                
                sleepingPlayers.remove(player.getUniqueId());
                
                // Очищаем ActionBar
                player.sendActionBar(Component.empty());
            }
        }, 60L); // 3 секунды (60 тиков)
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (sleepingPlayers.remove(player.getUniqueId())) {
            World world = player.getWorld();
            Boolean oldCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
            if (oldCycle != null) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, oldCycle);
            }
        }
    }
}
