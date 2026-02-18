package org.ender_venom1.chatStuff;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Pattern;

public final class ChatStuff extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private List<String> xyzTriggers;
    private List<String> itemTriggers;
    private List<String> randomTriggers;
    private List<String> badTriggers;
    private List<String> cuteReplacements;

    private NamedTextColor triggerColor;
    private String language;
    private int localChatDistance;

    private final Random random = new Random();

    private static final Map<String, NamedTextColor> COLOR_MAP = Map.ofEntries(
            Map.entry("BLACK", NamedTextColor.BLACK),
            Map.entry("DARK_BLUE", NamedTextColor.DARK_BLUE),
            Map.entry("DARK_GREEN", NamedTextColor.DARK_GREEN),
            Map.entry("DARK_AQUA", NamedTextColor.DARK_AQUA),
            Map.entry("DARK_RED", NamedTextColor.DARK_RED),
            Map.entry("DARK_PURPLE", NamedTextColor.DARK_PURPLE),
            Map.entry("GOLD", NamedTextColor.GOLD),
            Map.entry("GRAY", NamedTextColor.GRAY),
            Map.entry("DARK_GRAY", NamedTextColor.DARK_GRAY),
            Map.entry("BLUE", NamedTextColor.BLUE),
            Map.entry("GREEN", NamedTextColor.GREEN),
            Map.entry("AQUA", NamedTextColor.AQUA),
            Map.entry("RED", NamedTextColor.RED),
            Map.entry("LIGHT_PURPLE", NamedTextColor.LIGHT_PURPLE),
            Map.entry("YELLOW", NamedTextColor.YELLOW),
            Map.entry("WHITE", NamedTextColor.WHITE)
    );

    private static final List<String> COLORS = new ArrayList<>(COLOR_MAP.keySet());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadValues();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("chatstuff")).setExecutor(this);
        Objects.requireNonNull(getCommand("chatstuff")).setTabCompleter(this);
    }

    private void loadValues() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        xyzTriggers = cfg.getStringList("triggers.xyz");
        itemTriggers = cfg.getStringList("triggers.item");
        randomTriggers = cfg.getStringList("triggers.random");
        badTriggers = cfg.getStringList("triggers.bad-words");
        cuteReplacements = cfg.getStringList("triggers.cute-words");

        language = cfg.getString("language", "ru");

        String colorName = cfg.getString("trigger-color", "GREEN").toUpperCase();
        triggerColor = COLOR_MAP.getOrDefault(colorName, NamedTextColor.GREEN);

        localChatDistance = cfg.getInt("local-chat-distance", 100);
    }

    private Component lang(String path) {
        String text = getConfig().getString("messages." + language + "." + path, "Missing text");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {

        Player player = event.getPlayer();
        String message = fixCaps(event.getMessage());

        boolean global = message.startsWith("!");

        if (global) {
            message = message.substring(1).trim();
            if (message.isEmpty()) {
                event.setCancelled(true);
                return;
            }
        }

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);

        component = replaceXYZ(component, player);
        component = replaceRandom(component);
        component = replaceItem(component, player);
        component = replaceBadWords(component);

        event.setCancelled(true);

        if (global) {

            Component formatted = Component.text("G ", NamedTextColor.GRAY)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" > ", NamedTextColor.WHITE))
                    .append(component.color(NamedTextColor.WHITE));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(formatted);
            }

            return;
        }


        Location loc = player.getLocation();
        double maxDistSquared = localChatDistance * localChatDistance;

        List<Player> receivers = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!p.getWorld().equals(player.getWorld())) continue;

            if (p.getLocation().distanceSquared(loc) <= maxDistSquared) {
                receivers.add(p);
            }
        }

        if (receivers.size() <= 1) {
            player.sendMessage(lang("nobody-heard"));
            return;
        }

        Component formatted = Component.text(player.getName() + " > ")
                .append(component);

        for (Player p : receivers) {
            p.sendMessage(formatted);
        }
    }

    private Pattern buildPattern(String trigger) {
        return Pattern.compile(
                "(?<![A-Za-zА-Яа-я0-9_])" + Pattern.quote(trigger) + "(?![A-Za-zА-Яа-я0-9_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private Component replaceXYZ(Component component, Player player) {
        for (String trigger : xyzTriggers) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(buildPattern(trigger))
                    .replacement((match, builder) -> {
                        Location l = player.getLocation();
                        String coords = l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
                        return Component.text(coords, triggerColor);
                    }).build());
        }
        return component;
    }

    private Component replaceRandom(Component component) {
        for (String trigger : randomTriggers) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(buildPattern(trigger))
                    .replacement((match, builder) ->
                            Component.text(random.nextInt(100) + 1, triggerColor)
                    ).build());
        }
        return component;
    }

    private Component replaceItem(Component component, Player player) {

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return component;

        for (String trigger : itemTriggers) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(buildPattern(trigger))
                    .replacement((match, builder) ->
                            Component.translatable(item.translationKey())
                                    .color(triggerColor)
                                    .hoverEvent(item.asHoverEvent())
                    ).build());
        }
        return component;
    }

    private Component replaceBadWords(Component component) {

        if (cuteReplacements.isEmpty()) return component;

        for (String bad : badTriggers) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(buildPattern(bad))
                    .replacement((match, builder) ->
                            Component.text(
                                    cuteReplacements.get(random.nextInt(cuteReplacements.size()))
                            ).decorate(TextDecoration.ITALIC)
                    ).build());
        }
        return component;
    }

    private String fixCaps(String message) {

        int letters = 0, upper = 0;

        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }

        if (letters == 0) return message;

        if ((upper * 100.0 / letters) > 51.0) {
            String lower = message.toLowerCase();
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        return message;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(lang("no-permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(lang("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload":
                loadValues();
                player.sendMessage(lang("reloaded"));
                break;

            case "color":
                if (args.length < 2) {
                    player.sendMessage(lang("color-usage"));
                    break;
                }

                String color = args[1].toUpperCase();
                if (!COLOR_MAP.containsKey(color)) {
                    player.sendMessage(lang("invalid-color"));
                    break;
                }

                getConfig().set("trigger-color", color);
                saveConfig();
                loadValues();
                player.sendMessage(lang("color-changed")
                        .append(Component.text(color)));
                break;

            case "language":
                if (args.length < 2) {
                    player.sendMessage(lang("language-usage"));
                    break;
                }

                String langArg = args[1].toLowerCase();
                if (!langArg.equals("ru") && !langArg.equals("en")) {
                    player.sendMessage(lang("invalid-language"));
                    break;
                }

                getConfig().set("language", langArg);
                saveConfig();
                loadValues();
                player.sendMessage(lang("language-changed"));
                break;

            case "distance":
                if (args.length < 2) {
                    player.sendMessage(lang("distance-usage"));
                    break;
                }

                try {
                    int dist = Integer.parseInt(args[1]);
                    if (dist < 1) {
                        player.sendMessage(lang("invalid-distance"));
                        break;
                    }

                    localChatDistance = dist;
                    getConfig().set("local-chat-distance", dist);
                    saveConfig();

                    player.sendMessage(lang("distance-changed")
                            .append(Component.text(dist)));

                } catch (NumberFormatException e) {
                    player.sendMessage(lang("invalid-distance"));
                }
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player) || !player.isOp())
            return Collections.emptyList();

        if (args.length == 1)
            return Arrays.asList("reload", "color", "language", "distance");

        if (args.length == 2 && args[0].equalsIgnoreCase("color"))
            return COLORS;

        if (args.length == 2 && args[0].equalsIgnoreCase("language"))
            return Arrays.asList("ru", "en");

        return Collections.emptyList();
    }
}
