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

        if (world.getTime() >= 12000 || world.getEnvironment() != World.Environment.NORMAL) return;

        event.setCancelled(true);

        boolean success = player.sleep(block.getLocation(), true);
        if (!success) return;

        sleepingPlayers.add(player.getUniqueId());

        // --- ДОБАВЛЯЕМ СООБЩЕНИЕ В ACTIONBAR ---
        // Создаем компонент с желтым текстом
        Component actionBarMessage = Component.text("Вы проспите весь день...", NamedTextColor.YELLOW);
        
        // Отправляем сообщение (по умолчанию висит около 2-3 секунд)
        player.sendActionBar(actionBarMessage);

        // Отключаем цикл времени, чтобы сервер не мешал
        Boolean oldDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        
        world.setTime(13000);

        // Удерживаем время на 13000
        BukkitRunnable timeLocker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !sleepingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                world.setTime(13000);
                
                // Дополнительно обновляем ActionBar каждый тик, 
                // чтобы он не пропадал раньше времени (опционально)
                player.sendActionBar(actionBarMessage);
            }
        };
        timeLocker.runTaskTimer(plugin, 0L, 1L);

        // Через 3 секунды будим
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sleepingPlayers.contains(player.getUniqueId())) {
                timeLocker.cancel(); 
                
                if (oldDaylightCycle != null) {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, oldDaylightCycle);
                }
                
                world.setTime(0); // Утро
                sleepingPlayers.remove(player.getUniqueId());
                
                // Очищаем ActionBar, чтобы текст исчез сразу после пробуждения
                player.sendActionBar(Component.empty());
            }
        }, 60L); // 3 секунды
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
