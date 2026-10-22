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

    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void onEnable() {
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            activeRadarPlayers.add(player.getUniqueId());
        }

        // Action Bar HUD guncelleme gorevi (10 tick = 0.5 saniye)
        getServer().getScheduler().runTaskTimer(this, this::updateRadarHud, 10L, 10L);

        logToFile("SYSTEM", "MobRadar basariyla yuklendi.");
        getLogger().info("MobRadar aktif edildi. Log: " + logFile.getAbsolutePath());
    }

    @Override
    public void onDisable() {
        activeRadarPlayers.clear();
        mobTargetMap.clear();
        logToFile("SYSTEM", "MobRadar devre disi birakildi.");
        getLogger().info("MobRadar kapatildi.");
    }

    private synchronized void logToFile(String tag, String message) {
        String time = dateFormat.format(new Date());
        String cleanMessage = ChatColor.stripColor(message);
        String line = String.format("[%s] [%s] %s", time, tag, cleanMessage);

        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(line);
        } catch (IOException e) {
            getLogger().warning("Log dosyasina yazilamadi: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        activeRadarPlayers.add(player.getUniqueId());

        player.sendMessage("§8[§6Radar§8] §7Canli mob radari devrede. Kapatmak veya acmak icin: §f/radar");
    }

    private void updateRadarHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!activeRadarPlayers.contains(player.getUniqueId())) {
                continue;
            }

            Location pLoc = player.getLocation();
            List<Entity> nearby = player.getNearbyEntities(40.0, 20.0, 40.0);

            int totalHostiles = 0;
            int targetingCount = 0;
            String nearestMobName = null;
            double nearestDistance = Double.MAX_VALUE;
            String threatMobName = null;
            double threatDistance = Double.MAX_VALUE;

            for (Entity e : nearby) {
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
                hud = String.format("§c[DIKKAT] %s seni hedef aldi! (%.1fm) §8| §7Takip: §c%d mob §8| §7Cevrede: %d",
                        threatMobName, threatDistance, targetingCount, totalHostiles);
            } else if (nearestDistance <= 16.0 && nearestMobName != null) {
                String distColor = nearestDistance <= 6.0 ? "§c" : "§e";
                hud = String.format("§6[Radar] §7En yakin: %s%s (%.1fm) §8| §7Cevrede: %d mob",
                        distColor, nearestMobName, nearestDistance, totalHostiles);
            } else {
                if (totalHostiles == 0) {
                    hud = "§a[Radar] Guvendesin §8(Yakininda mob yok)";
                } else {
                    hud = String.format("§a[Radar] Guvendesin §8| §7En yakin: %s (%.1fm) §8| §7Toplam: %d",
                            nearestMobName != null ? nearestMobName : "-", nearestDistance < 1000 ? nearestDistance : 0.0, totalHostiles);
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
        LivingEntity target = event.getTarget();
        UUID mobId = entity.getUniqueId();
        String mobName = getMobName(entity.getType());

        if (target instanceof Player) {
            Player player = (Player) target;
            Location mobLoc = entity.getLocation();
            Location playerLoc = player.getLocation();
            double distance = Math.round(mobLoc.distance(playerLoc) * 10.0) / 10.0;

            mobTargetMap.put(mobId, player.getUniqueId());

            String consoleMsg = String.format("%s oyuncuyu algiladi: %.1fm (Mob: X:%.0f Y:%.0f Z:%.0f | Oyuncu: %s)",
                    mobName, distance, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ(), player.getName());

            String chatMsg = String.format("§8[§cRadar§8] §c%s §7sizi §e%.1f blok §7mesafeden algiladi! §8(X:%.0f Y:%.0f Z:%.0f)",
                    mobName, distance, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ());

            player.sendMessage(chatMsg);
            playAlertSound(player);

            getLogger().info(consoleMsg);
            logToFile("HEDEF", consoleMsg);
        } else if (target == null && mobTargetMap.containsKey(mobId)) {
            UUID prevPlayerId = mobTargetMap.remove(mobId);
            Player prevPlayer = Bukkit.getPlayer(prevPlayerId);

            if (prevPlayer != null && prevPlayer.isOnline()) {
                double distance = Math.round(entity.getLocation().distance(prevPlayer.getLocation()) * 10.0) / 10.0;

                String consoleMsg = String.format("%s takibi birakti (Mesafe: %.1fm | Oyuncu: %s)",
                        mobName, distance, prevPlayer.getName());

                String chatMsg = String.format("§8[§aRadar§8] §7%s takibi birakti, radarindan ciktiniz. §8(Mesafe: %.1f blok)",
                        mobName, distance);

                prevPlayer.sendMessage(chatMsg);
                getLogger().info(consoleMsg);
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
        Location hitLoc = victim.getLocation();

        if (victim instanceof PigZombie) {
            double radius = 35.0;
            List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
            List<PigZombie> alerted = new ArrayList<>();

            for (Entity e : nearby) {
                if (e instanceof PigZombie && !e.getUniqueId().equals(victim.getUniqueId())) {
                    PigZombie pz = (PigZombie) e;
                    alerted.add(pz);
                    pz.setTarget(player);
                    pz.setAnger(600);
                }
            }

            int count = alerted.size();
            String logMsg = String.format("1 Zombi Piglin'e vuruldu -> 35m icindeki %d Piglin'e haber verildi.", count);
            String chatMsg = String.format("§8[§ePiglin Alarm§8] §c1 Piglin'e vurdunuz! §735 blok icindeki §c%d §7Zombi Piglin'e haber verildi (30sn ofke).", count);

            player.sendMessage(chatMsg);
            playAlertSound(player);
            getLogger().info(logMsg);
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
                double radius = 16.0;
                List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
                int wolfCount = 0;
                for (Entity e : nearby) {
                    if (e instanceof Wolf && !((Wolf) e).isTamed()) {
                        wolfCount++;
                        ((Wolf) e).setTarget(player);
                    }
                }
                String msg = String.format("Vahsi kurda vuruldu -> 16m icindeki %d kurt suru saldirisina gecti.", wolfCount);
                player.sendMessage(String.format("§8[§cKurt Surusu§8] §7Vahsi kurda vurdunuz! 16 bloktaki §c%d kurt §7saldiriya gecti.", wolfCount));
                logToFile("KURT_ALARM", msg);
            }
        } else if (victim instanceof Bee) {
            double radius = 16.0;
            List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
            int beeCount = 0;
            for (Entity e : nearby) {
                if (e instanceof Bee) {
                    beeCount++;
                    ((Bee) e).setTarget(player);
                }
            }
            String msg = String.format("Ariya vuruldu -> 16m icindeki %d ari saldiriya gecti.", beeCount);
            player.sendMessage(String.format("§8[§eAri Alarmi§8] §7Ariya vurdunuz! 16 bloktaki §c%d ari §7saldiriya gecti.", beeCount));
            logToFile("ARI_ALARM", msg);
        }
    }

    private void playAlertSound(Player player) {
        Long last = lastSoundTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last == null || now - last > 1500) {
            lastSoundTime.put(player.getUniqueId(), now);
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
                player.sendMessage("§8[§6Radar§8] §e40 blok icindeki moblar:");
                List<Entity> nearby = player.getNearbyEntities(40.0, 20.0, 40.0);
                int count = 0;

                for (Entity e : nearby) {
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
        }

        boolean on = activeRadarPlayers.contains(pid);
        player.sendMessage("§8=== §6Mob Radar §8===");
        player.sendMessage("§7Durum: " + (on ? "§aAcik" : "§cKapali"));
        player.sendMessage("§f/radar ac §7- Ekran radarini acar");
        player.sendMessage("§f/radar kapat §7- Ekran radarini kapatir");
        player.sendMessage("§f/radar liste §7- Yakinlardaki moblari listeler");
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
