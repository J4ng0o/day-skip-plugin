package com.example.daysleep;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BedListener implements Listener {

    private final JavaPlugin plugin;

    public BedListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Только правый клик по блоку
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Проверяем, что это кровать
        if (!block.getType().name().endsWith("_BED")) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Работаем только днём (0–12000 тиков)
        long time = world.getTime();
        if (time >= 12000) return;

        // Только в обычном мире (в Нижнем/Энде кровати взрываются)
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // Отменяем стандартное поведение, чтобы кровать не взорвалась
        event.setCancelled(true);

        // Принудительно укладываем игрока в кровать
        // force = true позволяет лечь днём
        boolean success = player.sleep(block.getLocation(), true);
        if (!success) {
            // Если по какой-то причине не удалось — просто выходим
            return;
        }

        // Не отправляем сообщений (по требованию)

        // С задержкой меняем время на ночь,
        // чтобы игрок успел визуально «полежать» в кровати
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.setTime(13000);
        }, 20L); // 1 секунда (20 тиков)
    }
}
