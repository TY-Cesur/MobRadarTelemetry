# 📡 MobRadarTelemetry

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20--1.21%2B-brightgreen.svg)](https://papermc.io)
[![Platform](https://img.shields.io/badge/Platform-Purpur%20%7C%20Paper%20%7C%20Spigot-orange.svg)](https://purpurmc.org)
[![Author](https://img.shields.io/badge/Author-DLGaddar-blue.svg)](https://github.com/DLGaddar)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**MobRadarTelemetry**, Minecraft için geliştirilmiş gerçek zamanlı mob algılama karesi (hitbox/bounding box), radar takip ve Zombi Piglin sürü alarm telemetri eklentisidir (plugin).

---

## 🎯 Özellikler

### 1. 👁️ Canlı Mob Algılama & Radar
- Herhangi bir düşman mob (Zombi, İskelet, Creeper, Örümcek, Enderman vb.) oyuncuyu algılayıp radarına aldığı anda:
  - Ekranda zil sesiyle birlikte uyarı belirir:  
    `🚨 [RADAR] Zombi sizi 18.4 blok mesafeden ALGILADI! (X: 120, Y: 64, Z: -250)`
- Oyuncu kaçıp mob'un takip/görüş menzilinden çıktığı an:  
  `🟢 [RADAR] Zombi takibi bıraktı! Radarından ÇIKTINIZ. (Mesafe: 36.2 blok)`

### 2. ⚡ Zombi Piglin Alarm & Sürü Yayılımı
- Bir Zombi Piglin'e vurulduğunda 35 blokluk küresel AABB alanındaki tüm sürüyü tarar:  
  `⚡ [ZOMBİ PİGLİN ALARMI] 1 Piglin'e vurdunuz!`  
  `-> 35 blok yarıçapındaki 5 adet Zombi Piglin'e HABER VERİLDİ!`  
  `-> Tüm sürü öfkelendi ve size kilitlendi! (Öfke süresi: ~30s)`  
  *(Alarm alan her bir piglinin koordinatı ve mesafesi detaylı olarak listelenir).*

### 3. 🐺 Kurt & 🐝 Arı Sürüsü Koruması
- Vahşi bir kurda vurulduğunda 16 bloktaki sürü saldırısını bildirir.
- Arıya vurulduğunda veya kovan bozulduğunda kovan savunmasını bildirir.

### 4. 📺 Sürekli Açık Canlı Radar (HUD)
- Ekranın altındaki Action Bar üzerinde her yarım saniyede bir güncellenen canlı durum göstergesi:
  - **Tehdit yoksa:** `[RADAR] 🟢 Güvendesin | Çevrede: 2 mob`
  - **Yaklaşan varsa:** `[⚠️ RADAR] En Yakın: Zombi (8.2b) | Çevrede: 4 mob`
  - **Sana kilitlenen varsa:** `[🚨 HEDEF ALINDIN!] İskelet seni izliyor! (12.2b)`

### 5. 📜 Canlı Dosya Günlüğü (Telemetry Logging)
- Tüm olaylar milisaniyesine kadar `plugins/MobRadarTelemetry/radar_live.log` dosyasına kaydedilir.

---

## ⌨️ Komutlar

| Komut | Açıklama |
|---|---|
| `/radar` | Radar menüsü ve durumunu gösterir |
| `/radar ac` / `/radar on` | Sürekli açık canlı ekran HUD'ını (Action Bar) açar |
| `/radar kapat` / `/radar off` | Ekran HUD'ını kapatır |
| `/radar liste` | 40 blok civarındaki tüm mob'ları, mesafelerini ve koordinatlarını listeler |

*Alternatif komut takma adları: `/mobradar`, `/telemetri`, `/mr`*

---

## 📥 Kurulum

1. [Releases](../../releases) bölümünden en son `MobRadarTelemetry-1.1.0.jar` dosyasını indirin.
2. Sunucunuzun `plugins/` klasörüne atın.
3. Sunucuyu başlatın veya `/reload confirm` yazın.
4. Oyuna girip `/radar ac` yazarak canlı radarın tadını çıkarın!

---

## 🛠️ Derleme (Build)

```bash
# Maven ile derleme:
mvn clean package

# veya hazır .jar dosyasını releases/ klasöründen doğrudan kullanın.
```

---

## 📄 Lisans
Bu proje [MIT Lisansı](LICENSE) altında lisanslanmıştır.
