package com.example.daysleep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
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

        Location originalLocation = player.getLocation().clone();
        Location bedCenter = block.getLocation().add(0.5, 0.1, 0.5);
        
        // Визуальная имитация сна
        player.teleport(bedCenter);
        player.setPose(org.bukkit.entity.Pose.SLEEPING);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);

        Component actionBarMessage = Component.text("Вы проспите весь день...", NamedTextColor.WHITE);
        player.sendActionBar(actionBarMessage);

        sleepingPlayers.add(player.getUniqueId());

        Boolean oldDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(13000);

        // Задача, которая держит время и сбрасывает sleepTicks
        BukkitRunnable timeLocker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !sleepingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                world.setTime(13000);
                player.sendActionBar(actionBarMessage);
                
                // СБРОС ТАЙМЕРА СНА
                try {
                    Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
                    // Ищем поле sleepTicks (может быть в суперклассе)
                    Field sleepTicksField = null;
                    Class<?> clazz = nmsPlayer.getClass();
                    while (clazz != null) {
                        try {
                            sleepTicksField = clazz.getDeclaredField("sleepTicks");
                            break;
                        } catch (NoSuchFieldException e) {
                            clazz = clazz.getSuperclass();
                        }
                    }
                    
                    if (sleepTicksField != null) {
                        sleepTicksField.setAccessible(true);
                        sleepTicksField.setInt(nmsPlayer, 98); // Держим ниже порога 100
                    } else {
                        // Если поле не найдено — предупреждаем один раз и отменяем задачу
                        plugin.getLogger().warning("Не удалось найти поле sleepTicks. Время может откатываться.");
                        this.cancel();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка при сбросе sleepTicks: " + e.getMessage());
                    this.cancel();
                }
            }
        };
        timeLocker.runTaskTimer(plugin, 0L, 1L); // Каждый тик

        // Через 3 секунды будим
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sleepingPlayers.contains(player.getUniqueId())) {
                timeLocker.cancel();
                
                // Возвращаем всё назад
                player.setPose(org.bukkit.entity.Pose.STANDING);
                player.teleport(originalLocation);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
                player.sendActionBar(Component.empty());
                
                if (oldDaylightCycle != null) {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, oldDaylightCycle);
                }
                world.setTime(1000); // Утро
                
                sleepingPlayers.remove(player.getUniqueId());
            }
        }, 60L);
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
            player.setPose(org.bukkit.entity.Pose.STANDING);
        }
    }
}
