package com.antigravity.mobradar;

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

    // Mob ID -> Hedef Oyuncu ID
    private final Map<UUID, UUID> mobTargetMap = new HashMap<>();
    // Oyuncuların canlı radar HUD'ı açık mı? (Varsayılan olarak açık)
    private final Set<UUID> activeRadarPlayers = new HashSet<>();
    // Uyarı sesleri için cooldown
    private final Map<UUID, Long> lastSoundTime = new HashMap<>();

    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    @Override
    public void onEnable() {
        // Log dosyası hazırla
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        logFile = new File(getDataFolder(), "radar_live.log");

        // Event listener kaydet
        getServer().getPluginManager().registerEvents(this, this);

        // Komut yöneticileri kaydet
        if (getCommand("radar") != null) {
            getCommand("radar").setExecutor(this);
        }
        if (getCommand("mobradar") != null) {
            getCommand("mobradar").setExecutor(this);
        }

        // Zaten oyunda olan oyuncuların radarını otomatik aç
        for (Player p : Bukkit.getOnlinePlayers()) {
            activeRadarPlayers.add(p.getUniqueId());
            p.sendMessage("§a§l[MobRadar] §eCanlı radar HUD'ı otomatik açıldı! Çevreniz taranıyor...");
        }

        // HER YARIM SANİYEDE (10 tick) ÇALIŞAN SÜREKLİ AÇIK CANLI RADAR GÖREVİ
        getServer().getScheduler().runTaskTimer(this, this::runLiveRadarHud, 10L, 10L);

        logToFile("SYSTEM", "MobRadarTelemetry v1.1.0 baslatildi. Surekli acik radar HUD aktif!");
        getLogger().info("=================================================");
        getLogger().info("  [MobRadar] Canli Telemetri & Surekli Radar Acik!");
        getLogger().info("  Log: " + logFile.getAbsolutePath());
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        activeRadarPlayers.clear();
        mobTargetMap.clear();
        logToFile("SYSTEM", "MobRadarTelemetry durduruldu.");
        getLogger().info("[MobRadar] Sistem durduruldu.");
    }

    /**
     * Dosyaya ve konsola formatlı log basar
     */
    private synchronized void logToFile(String category, String message) {
        String time = dateFormat.format(new Date());
        String cleanMessage = ChatColor.stripColor(message);
        String logLine = String.format("[%s] [%-12s] %s", time, category, cleanMessage);

        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(logLine);
        } catch (IOException e) {
            getLogger().warning("Log dosyasina yazilamadi: " + e.getMessage());
        }
    }

    /**
     * Oyuncu oyuna girdiğinde radarı otomatik açar ve hoş geldin mesajı atar
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        activeRadarPlayers.add(player.getUniqueId());

        player.sendMessage("§a§l═════════ 📡 CANLI MOB RADARI ═════════");
        player.sendMessage("§eHoş geldin! §7Canlı mob algılama ve Zombi Piglin alarm telemetrisi devrede.");
        player.sendMessage("§7Sürekli açık radar: §aAÇIK §8(Ekranın altında canlı görünür)");
        player.sendMessage("§7Komutlar: §f/radar §8| §f/radar liste §8| §f/radar kapat");
        player.sendMessage("§a§l════════════════════════════════════════");
    }

    /**
     * SÜREKLİ AÇIK CANLI RADAR GÖREVİ (Action Bar HUD)
     * Her yarım saniyede bir ekranın altındaki Action Bar'ı günceller.
     */
    private void runLiveRadarHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!activeRadarPlayers.contains(player.getUniqueId())) {
                continue;
            }

            Location pLoc = player.getLocation();
            List<Entity> nearby = player.getNearbyEntities(40.0, 20.0, 40.0);

            int totalHostiles = 0;
            int targetedByCount = 0;
            String nearestMobName = null;
            double nearestDistance = Double.MAX_VALUE;
            String primaryThreatMob = null;
            double primaryThreatDist = Double.MAX_VALUE;

            for (Entity e : nearby) {
                if (e instanceof Monster) {
                    totalHostiles++;
                    double dist = Math.round(e.getLocation().distance(pLoc) * 10.0) / 10.0;

                    // En yakın mob kontrolü
                    if (dist < nearestDistance) {
                        nearestDistance = dist;
                        nearestMobName = getMobTurkishName(e.getType());
                    }

                    // Bu mob doğrudan oyuncuyu hedef alıyor mu?
                    if (e instanceof Mob) {
                        LivingEntity target = ((Mob) e).getTarget();
                        if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
                            targetedByCount++;
                            if (dist < primaryThreatDist) {
                                primaryThreatDist = dist;
                                primaryThreatMob = getMobTurkishName(e.getType());
                            }
                        }
                    }
                }
            }

            // Action Bar mesajını oluştur
            String hudText;

            // 1. DURUM: Bir veya daha fazla mob doğrudan oyuncuyu hedef almış!
            if (targetedByCount > 0) {
                hudText = String.format("§c§l[🚨 HEDEF ALINDIN!] §e%s seni izliyor! (%.1fb) §8| §7Hedef: §c%d mob §8| §7Çevrede: §e%d",
                        primaryThreatMob, primaryThreatDist, targetedByCount, totalHostiles);
            }
            // 2. DURUM: Yakında tehlikeli bir mob var (16 blok içi)
            else if (nearestDistance <= 16.0 && nearestMobName != null) {
                String color = nearestDistance <= 6.0 ? "§4§l" : "§6";
                hudText = String.format("%s[⚠️ RADAR] §eEn Yakın: §f%s (%.1fb) §8| §7Çevrede: §e%d mob",
                        color, nearestMobName, nearestDistance, totalHostiles);
            }
            // 3. DURUM: Güvenli alan
            else {
                if (totalHostiles == 0) {
                    hudText = "§a[RADAR] 🟢 Güvendesin §8(40b içinde düşman mob yok)";
                } else {
                    hudText = String.format("§a[RADAR] 🟢 Güvendesin §8| §7En yakın mob: %s (%.1fb) §8| §7Toplam: %d",
                            nearestMobName != null ? nearestMobName : "-", nearestDistance < 1000 ? nearestDistance : 0.0, totalHostiles);
                }
            }

            try {
                player.sendActionBar(Component.text(hudText));
            } catch (Throwable t) {
                // Alternatif fallback
                try {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(hudText));
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * MOB HEDEF ALMA VE ALGILAMA EVENT'İ
     * Zombi veya herhangi bir mob oyuncuyu gördüğünde veya radarından çıktığında tetiklenir!
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        LivingEntity target = event.getTarget();
        UUID mobId = entity.getUniqueId();
        String mobName = getMobTurkishName(entity.getType());

        // 1. DURUM: Mob bir Oyuncuyu Hedef Aldı (Radara Girdi / Algıladı)
        if (target instanceof Player) {
            Player player = (Player) target;
            Location mobLoc = entity.getLocation();
            Location playerLoc = player.getLocation();
            double distance = Math.round(mobLoc.distance(playerLoc) * 10.0) / 10.0;

            mobTargetMap.put(mobId, player.getUniqueId());

            String consoleMsg = String.format("🚨 %s sizi %.1f blok mesafeden ALGILADI! (Mob: X:%.0f Y:%.0f Z:%.0f | Oyuncu: %s)",
                    mobName, distance, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ(), player.getName());

            String chatMsg = String.format("§c§l[RADAR] §6%s §7sizi §e%.1f blok §7mesafeden §cALGILADI! §8(X:%.0f Y:%.0f Z:%.0f)",
                    mobName, distance, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ());

            player.sendMessage(chatMsg);

            // Ses uyarısı
            playAlertSound(player);

            getLogger().info(ChatColor.stripColor(consoleMsg));
            logToFile("HEDEF_ALINDI", consoleMsg);
        }
        // 2. DURUM: Mob Hedefi Bıraktı veya Kaybetti (Radarından Çıkıldı / Takip Bitti)
        else if (target == null && mobTargetMap.containsKey(mobId)) {
            UUID prevPlayerId = mobTargetMap.remove(mobId);
            Player prevPlayer = Bukkit.getPlayer(prevPlayerId);

            if (prevPlayer != null && prevPlayer.isOnline()) {
                double distance = Math.round(entity.getLocation().distance(prevPlayer.getLocation()) * 10.0) / 10.0;

                String consoleMsg = String.format("🟢 %s takibi birakti! Radarindan CIKTINIZ. (Mesafe: %.1f blok | Oyuncu: %s)",
                        mobName, distance, prevPlayer.getName());

                String chatMsg = String.format("§a§l[RADAR] §6%s §7takibi bıraktı! Radarından §aÇIKTINIZ§7. §8(Mesafe: %.1f blok)",
                        mobName, distance);

                prevPlayer.sendMessage(chatMsg);
                getLogger().info(ChatColor.stripColor(consoleMsg));
                logToFile("RADAR_CIKIS", consoleMsg);
            }
        }
    }

    /**
     * SALDIRI VE ZİNCİRLEME ALARM EVENT'İ
     * Zombi Piglin, Kurt, Arı gibi sürülere vurulduğunda diğerlerine nasıl haber verildiğini loglar!
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        Entity victim = event.getEntity();
        Location hitLoc = victim.getLocation();

        // 1. MEKANİK: ZOMBİ PİGLİN ALARMI (35 Blok Küresel Alan)
        if (victim instanceof PigZombie) {
            double alertRadius = 35.0;
            List<Entity> nearby = victim.getNearbyEntities(alertRadius, alertRadius, alertRadius);
            List<PigZombie> alertedPiglins = new ArrayList<>();

            for (Entity e : nearby) {
                if (e instanceof PigZombie && !e.getUniqueId().equals(victim.getUniqueId())) {
                    PigZombie pz = (PigZombie) e;
                    alertedPiglins.add(pz);
                    pz.setTarget(player);
                    pz.setAnger(600); // 30 saniye öfke
                }
            }

            int count = alertedPiglins.size();
            String logMsg = String.format("⚡ 1 Zombi Piglin'e vurdun! 35b yaricapindaki %d adet Piglin'e HABER VERILDI!", count);
            String chatMsg = String.format("§e§l[ZOMBİ PİGLİN ALARMI] §c1 Piglin'e vurdunuz!\n§7-> §e35 blok §7yarıçapındaki §c%d adet §7Zombi Piglin'e §cHABER VERİLDİ!\n§7-> Tüm sürü öfkelendi ve size kilitlendi! (Süre: ~30s)", count);

            player.sendMessage(chatMsg);
            playAlertSound(player);
            getLogger().warning(ChatColor.stripColor(logMsg));
            logToFile("PIGLIN_ALARM", logMsg);

            // Detaylı piglin koordinatları
            if (count > 0) {
                StringBuilder detail = new StringBuilder("   Haber Alan Piglinler:\n");
                for (int i = 0; i < Math.min(count, 5); i++) {
                    PigZombie pz = alertedPiglins.get(i);
                    double dist = Math.round(pz.getLocation().distance(hitLoc) * 10.0) / 10.0;
                    detail.append(String.format("   • Piglin #%d: Mesafe=%.1fb | Konum=(X:%.0f Y:%.0f Z:%.0f)\n",
                            (i + 1), dist, pz.getLocation().getX(), pz.getLocation().getY(), pz.getLocation().getZ()));
                }
                if (count > 5) {
                    detail.append(String.format("   • ... ve %d piglin daha!", count - 5));
                }
                logToFile("PIGLIN_DETAY", detail.toString());
            }
        }
        // 2. MEKANİK: KURT SÜRÜ ALARMI (16 Blok)
        else if (victim instanceof Wolf) {
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
                String msg = String.format("🐺 [KURT SÜRÜSÜ] Vahşi kurda vurdunuz! 16 bloktaki %d kurt sürü saldırısına geçti!", wolfCount);
                player.sendMessage("§c§l[SÜRÜ ALARMI] §7Vahşi kurda vurdunuz! §e16 bloktaki " + wolfCount + " kurt §csaldırıya geçti!");
                logToFile("KURT_ALARM", msg);
            }
        }
        // 3. MEKANİK: ARI KOVANI / ARI ALARMI (16 Blok)
        else if (victim instanceof Bee) {
            double radius = 16.0;
            List<Entity> nearby = victim.getNearbyEntities(radius, radius, radius);
            int beeCount = 0;
            for (Entity e : nearby) {
                if (e instanceof Bee) {
                    beeCount++;
                    ((Bee) e).setTarget(player);
                }
            }
            String msg = String.format("🐝 [ARI ALARMI] Arıya vurdunuz! 16 bloktaki %d arı iğne modunda saldırdı!", beeCount);
            player.sendMessage("§c§l[ARI ALARMI] §7Arıya vurdunuz! §e" + beeCount + " arı §csaldırıya geçti!");
            logToFile("ARI_ALARM", msg);
        }
    }

    private void playAlertSound(Player player) {
        Long last = lastSoundTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last == null || now - last > 1500) {
            lastSoundTime.put(player.getUniqueId(), now);
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.6f);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * /radar veya /mobradar komutu işleyicisi
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[MobRadar] Bu komut oyun icinden bir oyuncu tarafindan kullanilmalidir.");
            return true;
        }

        Player player = (Player) sender;
        UUID pid = player.getUniqueId();

        // 1. /radar ac veya /radar on
        if (args.length > 0 && (args[0].equalsIgnoreCase("ac") || args[0].equalsIgnoreCase("on"))) {
            activeRadarPlayers.add(pid);
            player.sendMessage("§a§l[MobRadar] §eCanlı sürekli radar HUD'ı §aAÇILDI! §7Ekranın altından anlık takip edebilirsiniz.");
            return true;
        }

        // 2. /radar kapat veya /radar off
        if (args.length > 0 && (args[0].equalsIgnoreCase("kapat") || args[0].equalsIgnoreCase("off"))) {
            activeRadarPlayers.remove(pid);
            player.sendMessage("§c§l[MobRadar] §7Canlı radar HUD'ı §cKAPATILDI. §7Tekrar açmak için: §e/radar ac");
            return true;
        }

        // 3. /radar liste veya /radar list
        if (args.length > 0 && (args[0].equalsIgnoreCase("liste") || args[0].equalsIgnoreCase("list"))) {
            player.sendMessage("§a§l═════════ 📋 ÇEVREDEKİ CANLI MOBLAR ═════════");
            List<Entity> nearby = player.getNearbyEntities(40.0, 20.0, 40.0);
            int count = 0;

            for (Entity e : nearby) {
                if (e instanceof Monster) {
                    count++;
                    double dist = Math.round(e.getLocation().distance(player.getLocation()) * 10.0) / 10.0;
                    String name = getMobTurkishName(e.getType());
                    Location loc = e.getLocation();

                    boolean isTargetingMe = false;
                    if (e instanceof Mob && ((Mob) e).getTarget() != null) {
                        isTargetingMe = ((Mob) e).getTarget().getUniqueId().equals(player.getUniqueId());
                    }

                    String targetTag = isTargetingMe ? " §c§l[SANA KİLİTLENDİ!]" : "";
                    player.sendMessage(String.format(" §c• %s §7(§e%.1fb§7) §8[X:%.0f Y:%.0f Z:%.0f]%s",
                            name, dist, loc.getX(), loc.getY(), loc.getZ(), targetTag));
                }
            }

            if (count == 0) {
                player.sendMessage(" §a✓ 40 blok civarında hiç düşman mob yok, tamamen güvendesin!");
            } else {
                player.sendMessage("§7Toplam §c" + count + " §7düşman mob tespit edildi.");
            }
            player.sendMessage("§a§l════════════════════════════════════════");
            return true;
        }

        // 4. Varsayılan Menü (/radar)
        boolean isHudOn = activeRadarPlayers.contains(pid);
        player.sendMessage("§a§l═════════ 📡 MOB RADAR MENÜSÜ ═════════");
        player.sendMessage("§7Sürekli Canlı HUD: " + (isHudOn ? "§aAÇIK (Aktif)" : "§cKAPALI"));
        player.sendMessage("§e/radar ac    §7-> Sürekli açık canlı ekran HUD'ını açar");
        player.sendMessage("§e/radar kapat §7-> Ekran HUD'ını kapatır");
        player.sendMessage("§e/radar liste §7-> 40 bloktaki tüm mobları mesafesiyle listeler");
        player.sendMessage("§7Log Dosyası: §fplugins/MobRadarTelemetry/radar_live.log");
        player.sendMessage("§a§l════════════════════════════════════════");
        return true;
    }

    private String getMobTurkishName(EntityType type) {
        switch (type) {
            case ZOMBIE: return "Zombi";
            case SKELETON: return "İskelet";
            case CREEPER: return "Creeper";
            case SPIDER: return "Örümcek";
            case CAVE_SPIDER: return "Mağara Örümceği";
            case ENDERMAN: return "Enderman";
            case PIGLIN: return "Piglin";
            case PIGLIN_BRUTE: return "Piglin Savaşçısı";
            case ZOMBIFIED_PIGLIN: return "Zombi Piglin";
            case WARDEN: return "Warden";
            case WITCH: return "Cadı";
            case PHANTOM: return "Phantom";
            case BLAZE: return "Blaze";
            case GHAST: return "Ghast";
            case WITHER_SKELETON: return "Wither İskeleti";
            case DROWNED: return "Boğulmuş (Drowned)";
            case HUSK: return "Çöl Zombisi (Husk)";
            case STRAY: return "Başıboş (Stray)";
            case SLIME: return "Slime";
            case MAGMA_CUBE: return "Magma Küpü";
            case WOLF: return "Kurt";
            case BEE: return "Arı";
            case IRON_GOLEM: return "Demir Golem";
            case ENDER_DRAGON: return "Ender Ejderhası";
            case WITHER: return "Wither";
            default: return type.name();
        }
    }
}
