package org.ender_venom1.chatStuff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public final class ChatStuff extends JavaPlugin implements Listener {

    private List<String> xyzTriggers;
    private List<String> itemTriggers;
    private List<String> randomTriggers;
    private NamedTextColor triggerColor;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();

        xyzTriggers = cfg.getStringList("triggers.xyz");
        itemTriggers = cfg.getStringList("triggers.item");
        randomTriggers = cfg.getStringList("triggers.random");

        String colorName = cfg.getString("trigger-color", "GREEN").toUpperCase();
        triggerColor = NamedTextColor.NAMES.value(colorName);
        if (triggerColor == null) triggerColor = NamedTextColor.GREEN;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);

        component = replaceXYZ(component, player);
        component = replaceRandom(component);
        component = replaceItem(component, player);

        event.setCancelled(true);

        Component finalMessage = Component.text(player.getName() + ": ")
                .append(component);

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalMessage));
    }

    /*
        Универсальный паттерн:
        Не буква и не цифра перед триггером
        Не буква и не цифра после триггера

        Это позволяет работать так:
        !xyz!я!на!кордах!
        .xyz?
        ,рандом;
        (предмет)
    */
    private Pattern buildTriggerPattern(String trigger) {
        return Pattern.compile(
                "(?i)(?<![A-Za-zА-Яа-я0-9_])"
                        + Pattern.quote(trigger) +
                        "(?![A-Za-zА-Яа-я0-9_])"
        );
    }

    private Component replaceXYZ(Component component, Player player) {
        for (String trigger : xyzTriggers) {

            Pattern pattern = buildTriggerPattern(trigger);

            component = component.replaceText(TextReplacementConfig.builder()
                    .match(pattern)
                    .replacement((match, builder) -> {
                        String coords = player.getLocation().getBlockX() + ", "
                                + player.getLocation().getBlockY() + ", "
                                + player.getLocation().getBlockZ();

                        return Component.text(coords, triggerColor);
                    })
                    .build());
        }
        return component;
    }

    private Component replaceRandom(Component component) {
        for (String trigger : randomTriggers) {

            Pattern pattern = buildTriggerPattern(trigger);

            component = component.replaceText(TextReplacementConfig.builder()
                    .match(pattern)
                    .replacement((match, builder) -> {
                        int value = random.nextInt(100) + 1;
                        return Component.text("Рандом: " + value, triggerColor);
                    })
                    .build());
        }
        return component;
    }

    private Component replaceItem(Component component, Player player) {

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return component;

        for (String trigger : itemTriggers) {

            Pattern pattern = buildTriggerPattern(trigger);

            component = component.replaceText(TextReplacementConfig.builder()
                    .match(pattern)
                    .replacement((match, builder) ->
                            Component.translatable(item.translationKey())
                                    .color(triggerColor)
                                    .hoverEvent(item.asHoverEvent())
                    )
                    .build());
        }
        return component;
    }
}