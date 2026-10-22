package me.dlgaddar.mobradar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class MobRadarPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, UUID> mobTargetMap = new HashMap<>();
    private final Set<UUID> activeRadarPlayers = new HashSet<>();
    private final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private final Set<String> ignoredMobs = new HashSet<>();

    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigOptions();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        logFile = new File(getDataFolder(), "radar.log");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("radar") != null) {
            getCommand("radar").setExecutor(this);
        }
        if (getCommand("mobradar") != null) {
            getCommand("mobradar").setExecutor(this);
        }

        boolean defaultOn = getConfig().getBoolean("hud.enabled-by-default", true);
        if (defaultOn) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                activeRadarPlayers.add(player.getUniqueId());
            }
        }

        long interval = getConfig().getLong("hud.update-interval-ticks", 10L);
        getServer().getScheduler().runTaskTimer(this, this::updateRadarHud, interval, interval);

        logToFile("SYSTEM", "MobRadar basariyla yuklendi.");
        getLogger().info("MobRadar aktif edildi. Yoksayilan mob sayisi: " + ignoredMobs.size());
    }

    @Override
    public void onDisable() {
        activeRadarPlayers.clear();
        mobTargetMap.clear();
        ignoredMobs.clear();
        logToFile("SYSTEM", "MobRadar devre disi birakildi.");
        getLogger().info("MobRadar kapatildi.");
    }

    private void loadConfigOptions() {
        ignoredMobs.clear();
        List<String> list = getConfig().getStringList("ignored-mobs");
        for (String s : list) {
            if (s != null && !s.trim().isEmpty()) {
                ignoredMobs.add(s.toUpperCase().trim());
            }
        }
    }

    private boolean isMobIgnored(EntityType type) {
        if (type == null) return false;
        return ignoredMobs.contains(type.name().toUpperCase());
    }

    private synchronized void logToFile(String tag, String message) {
        if (!getConfig().getBoolean("logging.file-logging", true)) {
            return;
        }

        String time = dateFormat.format(new Date());
        String cleanMessage = ChatColor.stripColor(message);
        String line = String.format("[%s] [%s] %s", time, tag, cleanMessage);

        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(line);
        } catch (IOException e) {
            getLogger().warning("Log dosyasina yazilamadi: " + e.getMessage());
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (getConfig().getBoolean("hud.enabled-by-default", true)) {
            activeRadarPlayers.add(player.getUniqueId());
        }

        String prefix = color(getConfig().getString("messages.prefix", "&8[&6Radar&8] "));
        player.sendMessage(prefix + "§7Canli mob radari devrede. Komutlar icin: §f/radar");
    }

    private void updateRadarHud() {
        double scanRadius = getConfig().getDouble("hud.scan-radius", 40.0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!activeRadarPlayers.contains(player.getUniqueId())) {
                continue;
            }

            Location pLoc = player.getLocation();
            List<Entity> nearby = player.getNearbyEntities(scanRadius, scanRadius / 2.0, scanRadius);

            int totalHostiles = 0;
            int targetingCount = 0;
            String nearestMobName = null;
            double nearestDistance = Double.MAX_VALUE;
            String threatMobName = null;
            double threatDistance = Double.MAX_VALUE;

            for (Entity e : nearby) {
                // Kara listedeki moblari filtrele
                if (isMobIgnored(e.getType())) {
                    continue;
                }

                if (e instanceof Monster) {
                    totalHostiles++;
                    double dist = Math.round(e.getLocation().distance(pLoc) * 10.0) / 10.0;

                    if (dist < nearestDistance) {
                        nearestDistance = dist;
                        nearestMobName = getMobName(e.getType());
                    }

                    if (e instanceof Mob) {
                        LivingEntity target = ((Mob) e).getTarget();
                        if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
                            targetingCount++;
                            if (dist < threatDistance) {
                                threatDistance = dist;
                                threatMobName = getMobName(e.getType());
                            }
                        }
                    }
                }
            }

            String hud;
            if (targetingCount > 0) {
                String template = getConfig().getString("messages.hud-danger", "&c[DIKKAT] %mob% seni hedef aldi! (%distance%m) &8| &7Takip: &c%targeting% mob &8| &7Cevrede: %total%");
                hud = color(template
                        .replace("%mob%", threatMobName != null ? threatMobName : "-")
                        .replace("%distance%", String.valueOf(threatDistance))
                        .replace("%targeting%", String.valueOf(targetingCount))
                        .replace("%total%", String.valueOf(totalHostiles)));
            } else if (nearestDistance <= 16.0 && nearestMobName != null) {
                String template = getConfig().getString("messages.hud-warning", "&6[Radar] &7En yakin: %mob% (%distance%m) &8| &7Cevrede: %total% mob");
                hud = color(template
                        .replace("%mob%", nearestMobName)
                        .replace("%distance%", String.valueOf(nearestDistance))
                        .replace("%total%", String.valueOf(totalHostiles)));
            } else {
                if (totalHostiles == 0) {
                    hud = color(getConfig().getString("messages.hud-safe", "&a[Radar] Guvendesin &8(Yakininda mob yok)"));
                } else {
                    String template = getConfig().getString("messages.hud-safe-nearby", "&a[Radar] Guvendesin &8| &7En yakin: %mob% (%distance%m) &8| &7Toplam: %total%");
                    hud = color(template
                            .replace("%mob%", nearestMobName != null ? nearestMobName : "-")
                            .replace("%distance%", String.valueOf(nearestDistance < 1000 ? nearestDistance : 0.0))
                            .replace("%total%", String.valueOf(totalHostiles)));
                }
            }

            try {
                player.sendActionBar(Component.text(hud));
            } catch (Throwable t) {
                try {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(hud));
                } catch (Throwable ignored) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();

        // Sadece canli moblar hedef alma uyarisi verebilir (tecrube kuresi vb. haric)
        if (!(entity instanceof Mob)) {
            return;
        }

        // Kara listedeki moblari yoksay
        if (isMobIgnored(entity.getType())) {
            return;
        }

        LivingEntity target = event.getTarget();
        UUID mobId = entity.getUniqueId();
        String mobName = getMobName(entity.getType());

        if (target instanceof Player) {
            Player player = (Player) target;
            Location mobLoc = entity.getLocation();
            Location playerLoc = player.getLocation();
            double distance = Math.round(mobLoc.distance(playerLoc) * 10.0) / 10.0;

            mobTargetMap.put(mobId, player.getUniqueId());

            String template = getConfig().getString("messages.target-acquired", "&c%mob% &7sizi &e%distance% blok &7mesafeden algiladi! &8(X:%x% Y:%y% Z:%z%)");
            String chatMsg = color(getConfig().getString("messages.prefix", "&8[&6Radar&8] ") + template
                    .replace("%mob%", mobName)
                    .replace("%distance%", String.valueOf(distance))
                    .replace("%x%", String.format("%.0f", mobLoc.getX()))
                    .replace("%y%", String.format("%.0f", mobLoc.getY()))
                    .replace("%z%", String.format("%.0f", mobLoc.getZ())));

            player.sendMessage(chatMsg);
            playAlertSound(player);

            String consoleMsg = String.format("%s oyuncuyu algiladi: %.1fm (X:%.0f Y:%.0f Z:%.0f | Oyuncu: %s)",
                    mobName, distance, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ(), player.getName());

            if (getConfig().getBoolean("logging.console-logging", true)) {
                getLogger().info(consoleMsg);
            }
            logToFile("HEDEF", consoleMsg);
        } else if (target == null && mobTargetMap.containsKey(mobId)) {
            UUID prevPlayerId = mobTargetMap.remove(mobId);
            Player prevPlayer = Bukkit.getPlayer(prevPlayerId);

            if (prevPlayer != null && prevPlayer.isOnline()) {
                double distance = Math.round(entity.getLocation().distance(prevPlayer.getLocation()) * 10.0) / 10.0;

                String template = getConfig().getString("messages.target-lost", "&7%mob% takibi birakti, radarindan ciktiniz. &8(Mesafe: %distance% blok)");
                String chatMsg = color(getConfig().getString("messages.prefix", "&8[&6Radar&8] ") + template
                        .replace("%mob%", mobName)
                        .replace("%distance%", String.valueOf(distance)));

                prevPlayer.sendMessage(chatMsg);

                String consoleMsg = String.format("%s takibi birakti (Mesafe: %.1fm | Oyuncu: %s)",
                        mobName, distance, prevPlayer.getName());

                if (getConfig().getBoolean("logging.console-logging", true)) {
                    getLogger().info(consoleMsg);
                }
                logToFile("KOPMA", consoleMsg);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        Entity victim = event.getEntity();

        if (isMobIgnored(victim.getType())) {
            return;
        }

        Location hitLoc = victim.getLocation();

        if (victim instanceof PigZombie) {
            double radius = getConfig().getDouble("alerts.piglin-radius", 35.0);
            int angerTicks = getConfig().getInt("alerts.piglin-anger-ticks", 600);
            List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
            List<PigZombie> alerted = new ArrayList<>();

            for (Entity e : nearby) {
                if (e instanceof PigZombie && !e.getUniqueId().equals(victim.getUniqueId()) && !isMobIgnored(e.getType())) {
                    PigZombie pz = (PigZombie) e;
                    alerted.add(pz);
                    pz.setTarget(player);
                    pz.setAnger(angerTicks);
                }
            }

            int count = alerted.size();
            String template = getConfig().getString("messages.piglin-alert", "&8[&ePiglin Alarm&8] &c1 Piglin'e vurdunuz! &735 blok icindeki &c%count% &7Zombi Piglin'e haber verildi.");
            String chatMsg = color(template.replace("%count%", String.valueOf(count)));

            player.sendMessage(chatMsg);
            playAlertSound(player);

            String logMsg = String.format("1 Zombi Piglin'e vuruldu -> %.0fm icindeki %d Piglin'e haber verildi.", radius, count);
            if (getConfig().getBoolean("logging.console-logging", true)) {
                getLogger().info(logMsg);
            }
            logToFile("PIGLIN_ALARM", logMsg);

            if (count > 0) {
                StringBuilder detail = new StringBuilder("Haber alan piglinler:\n");
                for (int i = 0; i < Math.min(count, 5); i++) {
                    PigZombie pz = alerted.get(i);
                    double dist = Math.round(pz.getLocation().distance(hitLoc) * 10.0) / 10.0;
                    detail.append(String.format("  - Piglin #%d: Mesafe=%.1fm, Konum=(%.0f, %.0f, %.0f)\n",
                            (i + 1), dist, pz.getLocation().getX(), pz.getLocation().getY(), pz.getLocation().getZ()));
                }
                logToFile("PIGLIN_DETAY", detail.toString());
            }
        } else if (victim instanceof Wolf) {
            Wolf wolf = (Wolf) victim;
            if (!wolf.isTamed()) {
                double radius = getConfig().getDouble("alerts.wolf-radius", 16.0);
                List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
                int wolfCount = 0;
                for (Entity e : nearby) {
                    if (e instanceof Wolf && !((Wolf) e).isTamed() && !isMobIgnored(e.getType())) {
                        wolfCount++;
                        ((Wolf) e).setTarget(player);
                    }
                }
                String template = getConfig().getString("messages.wolf-alert", "&8[&cKurt Surusu&8] &7Vahsi kurda vurdunuz! 16 bloktaki &c%count% kurt &7saldiriya gecti.");
                player.sendMessage(color(template.replace("%count%", String.valueOf(wolfCount))));
                logToFile("KURT_ALARM", "Vahsi kurda vuruldu -> " + wolfCount + " kurt saldiriya gecti.");
            }
        } else if (victim instanceof Bee) {
            double radius = getConfig().getDouble("alerts.bee-radius", 16.0);
            List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
            int beeCount = 0;
            for (Entity e : nearby) {
                if (e instanceof Bee && !isMobIgnored(e.getType())) {
                    beeCount++;
                    ((Bee) e).setTarget(player);
                }
            }
            String template = getConfig().getString("messages.bee-alert", "&8[&eAri Alarmi&8] &7Ariya vurdunuz! 16 bloktaki &c%count% ari &7saldiriya gecti.");
            player.sendMessage(color(template.replace("%count%", String.valueOf(beeCount))));
            logToFile("ARI_ALARM", "Ariya vuruldu -> " + beeCount + " ari saldiriya gecti.");
        }
    }

    private void playAlertSound(Player player) {
        if (!getConfig().getBoolean("sound.enabled", true)) {
            return;
        }

        Long last = lastSoundTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last == null || now - last > 1500) {
            lastSoundTime.put(player.getUniqueId(), now);
            try {
                String soundName = getConfig().getString("sound.type", "BLOCK_NOTE_BLOCK_BELL");
                Sound sound = Sound.valueOf(soundName);
                float volume = (float) getConfig().getDouble("sound.volume", 1.0);
                float pitch = (float) getConfig().getDouble("sound.pitch", 1.5);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("yenile"))) {
            if (sender.hasPermission("mobradar.admin") || sender.isOp() || !(sender instanceof Player)) {
                reloadConfig();
                loadConfigOptions();
                sender.sendMessage("§8[§6Radar§8] §aYapilandirma dosyasi (config.yml) basariyla yenilendi. Yoksayilan moblar guncellendi.");
                return true;
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("[MobRadar] Bu komut sadece oyuncular tarafindan kullanilabilir.");
            return true;
        }

        Player player = (Player) sender;
        UUID pid = player.getUniqueId();

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("ac") || sub.equals("on")) {
                activeRadarPlayers.add(pid);
                player.sendMessage("§8[§6Radar§8] §aEkran radari acildi.");
                return true;
            }
            if (sub.equals("kapat") || sub.equals("off")) {
                activeRadarPlayers.remove(pid);
                player.sendMessage("§8[§6Radar§8] §cEkran radari kapatildi.");
                return true;
            }
            if (sub.equals("liste") || sub.equals("list")) {
                player.sendMessage("§8[§6Radar§8] §e40 blok icindeki moblar (Filtrelenmis):");
                List<Entity> nearby = player.getNearbyEntities(40.0, 20.0, 40.0);
                int count = 0;

                for (Entity e : nearby) {
                    if (isMobIgnored(e.getType())) {
                        continue;
                    }

                    if (e instanceof Monster) {
                        count++;
                        double dist = Math.round(e.getLocation().distance(player.getLocation()) * 10.0) / 10.0;
                        String name = getMobName(e.getType());
                        Location loc = e.getLocation();

                        boolean targeting = false;
                        if (e instanceof Mob && ((Mob) e).getTarget() != null) {
                            targeting = ((Mob) e).getTarget().getUniqueId().equals(player.getUniqueId());
                        }

                        String mark = targeting ? " §c[Sana Kilitlendi]" : "";
                        player.sendMessage(String.format(" §7- §f%s §7(§e%.1fm§7) §8[%.0f, %.0f, %.0f]%s",
                                name, dist, loc.getX(), loc.getY(), loc.getZ(), mark));
                    }
                }

                if (count == 0) {
                    player.sendMessage(" §aYakinlarda hic mob yok.");
                } else {
                    player.sendMessage("§8Toplam " + count + " mob bulundu.");
                }
                return true;
            }
            if (sub.equals("blacklist") || sub.equals("ignored") || sub.equals("yoksayilanlar")) {
                player.sendMessage("§8[§6Radar§8] §eYoksayilan Moblar (Kara Liste):");
                if (ignoredMobs.isEmpty()) {
                    player.sendMessage(" §7Hicbir mob yoksayilmiyor.");
                } else {
                    player.sendMessage(" §c" + String.join(", ", ignoredMobs));
                }
                player.sendMessage("§8(Duzenlemek icin: config.yml -> ignored-mobs)");
                return true;
            }
        }

        boolean on = activeRadarPlayers.contains(pid);
        player.sendMessage("§8=== §6Mob Radar §8===");
        player.sendMessage("§7Durum: " + (on ? "§aAcik" : "§cKapali") + " §8| §7Kara Liste: §c" + ignoredMobs.size() + " mob");
        player.sendMessage("§f/radar ac §7- Ekran radarini acar");
        player.sendMessage("§f/radar kapat §7- Ekran radarini kapatir");
        player.sendMessage("§f/radar liste §7- Yakinlardaki moblari listeler");
        player.sendMessage("§f/radar blacklist §7- Yoksayilan moblari gosterir");
        if (player.isOp()) {
            player.sendMessage("§f/radar reload §7- config.yml dosyasini yeniler");
        }
        return true;
    }

    private String getMobName(EntityType type) {
        switch (type) {
            case ZOMBIE: return "Zombi";
            case SKELETON: return "Iskelet";
            case CREEPER: return "Creeper";
            case SPIDER: return "Orumcek";
            case CAVE_SPIDER: return "Magara Orumcegi";
            case ENDERMAN: return "Enderman";
            case PIGLIN: return "Piglin";
            case PIGLIN_BRUTE: return "Piglin Savascisi";
            case ZOMBIFIED_PIGLIN: return "Zombi Piglin";
            case WARDEN: return "Warden";
            case WITCH: return "Cadi";
            case PHANTOM: return "Phantom";
            case BLAZE: return "Blaze";
            case GHAST: return "Ghast";
            case WITHER_SKELETON: return "Wither Iskeleti";
            case DROWNED: return "Drowned";
            case HUSK: return "Husk";
            case STRAY: return "Stray";
            case SLIME: return "Slime";
            case MAGMA_CUBE: return "Magma Kubu";
            case WOLF: return "Kurt";
            case BEE: return "Ari";
            case IRON_GOLEM: return "Demir Golem";
            case ENDER_DRAGON: return "Ender Dragon";
            case WITHER: return "Wither";
            default: return type.name();
        }
    }
}
