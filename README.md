# MobRadarTelemetry

Minecraft Paper, Purpur ve Spigot sunucuları için geliştirilmiş canlı mob algılama, takip radarı ve sürü alarm telemetri eklentisi.

## Özellikler

- **Canlı Mob Algılama ve Takip Bildirimi:** Herhangi bir düşman mob oyuncuyu hedef aldığında oyuncuya anlık mesafe, koordinat ve sesli uyarı iletir. Oyuncu mobun takip alanından çıktığında takibin bittiğini bildirir.
- **Sürekli Açık Ekran Radarı (HUD):** Action Bar üzerinden oyuncunun güvende olup olmadığını, kendisine kilitlenen mobları ve en yakın düşman mobun mesafesini yarım saniyede bir günceller.
- **Zombi Piglin Alarm Takibi:** Bir Zombi Pigline vurulduğunda 35 blokluk yarıçaptaki kaç piglinin uyarıldığını, öfke süresini ve konumlarını chatte listeler.
- **Kurt ve Arı Sürü Koruması:** Vahşi kurtlara veya arılara saldırıldığında yakındaki sürü bireylerinin kışkırtıldığını bildirir.
- **Varlık Filtreleme ve Kara Liste (`ignored-mobs`):** Yarasa, balçık veya istenmeyen mobları radar taramasından ve bildirimlerden tamamen hariç tutar.
- **Özelleştirilebilir Yapılandırma (`config.yml`):** Tüm menziller, kara liste, ses efektleri, mesaj şablonları, renkler ve güncelleme süreleri tamamen ayarlanabilir.
- **Telemetri Günlüğü:** Tüm algılama ve alarm olayları `plugins/MobRadarTelemetry/radar.log` dosyasına kaydedilir.

## Komutlar

- `/radar` - Radar durumunu ve kullanım menüsünü gösterir.
- `/radar ac` - Canlı Action Bar ekran radarını açar.
- `/radar kapat` - Ekran radarını kapatır.
- `/radar liste` - 40 blok civarındaki tüm mobları, mesafelerini ve koordinatlarını listeler.
- `/radar blacklist` - Yoksayılan ve radarda filtrelenen mob türlerini listeler.
- `/radar reload` - `config.yml` dosyasını oyunu durdurmadan anında yeniler (OP yetkisi).

*Alternatif komutlar:* `/mobradar`, `/telemetri`, `/mr`

## Yapılandırma (`config.yml`)

Eklenti ilk çalıştığında `plugins/MobRadarTelemetry/config.yml` dosyasını otomatik oluşturur:

- **Kara Liste (`ignored-mobs`):** `BAT`, `SLIME`, `MAGMA_CUBE` gibi radarda görünmesini istemediğiniz Minecraft EntityType isimlerini ekleyebilirsiniz.
- **HUD Ayarları:** Tarama yarıçapı, yenilenme hızı (tick) ve başlangıçta açık olma durumu.
- **Alarm Menzilleri:** Zombi Piglin (35m), Kurt (16m), Arı (16m) alarm mesafeleri ve öfke süreleri.
- **Ses Ayarları:** Zil sesi türü (`Sound` enum), ses seviyesi ve tonu.
- **Mesaj Şablonları:** Tüm chat ve HUD metinleri renk kodları (`&a`, `&c`, `&e` vb.) ile düzenlenebilir.

## Kurulum

1. `MobRadarTelemetry-1.1.0.jar` dosyasını sunucunuzun `plugins/` klasörüne atın.
2. Sunucuyu başlatın. `plugins/MobRadarTelemetry/config.yml` dosyası oluşacaktır.
3. Ayarları dilediğiniz gibi düzenleyip oyundan `/radar reload` yazabilirsiniz.

## Derleme (Build)

```bash
mvn clean package
```

## Lisans

Bu proje MIT Lisansı altında sunulmaktadır.
