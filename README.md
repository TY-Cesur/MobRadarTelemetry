# MobRadarTelemetry

Minecraft Paper, Purpur ve Spigot sunucuları için geliştirilmiş canlı mob algılama, takip radarı ve sürü alarm telemetri eklentisi.

## Özellikler

- **Canlı Mob Algılama ve Takip Bildirimi:** Herhangi bir düşman mob oyuncuyu hedef aldığında oyuncuya anlık mesafe, koordinat ve sesli uyarı iletir. Oyuncu mobun takip alanından çıktığında takibin bittiğini bildirir.
- **Sürekli Açık Ekran Radarı (HUD):** Action Bar üzerinden oyuncunun güvende olup olmadığını, kendisine kilitlenen mobları ve en yakın düşman mobun mesafesini yarım saniyede bir günceller.
- **Zombi Piglin Alarm Takibi:** Bir Zombi Pigline vurulduğunda 35 blokluk yarıçaptaki kaç piglinin uyarıldığını, öfke süresini ve konumlarını chatte listeler.
- **Kurt ve Arı Sürü Koruması:** Vahşi kurtlara veya arılara saldırıldığında yakındaki sürü bireylerinin kışkırtıldığını bildirir.
- **Telemetri Günlüğü:** Tüm algılama ve alarm olayları `plugins/MobRadarTelemetry/radar.log` dosyasına kaydedilir.

## Komutlar

- `/radar` - Radar durumunu ve kullanım menüsünü gösterir.
- `/radar ac` - Canlı Action Bar ekran radarını açar.
- `/radar kapat` - Ekran radarını kapatır.
- `/radar liste` - 40 blok civarındaki tüm mobları, mesafelerini ve koordinatlarını listeler.

*Alternatif komutlar:* `/mobradar`, `/telemetri`, `/mr`

## Kurulum

1. `MobRadarTelemetry-1.1.0.jar` dosyasını sunucunuzun `plugins/` klasörüne atın.
2. Sunucuyu başlatın veya konsoldan eklentiyi yükleyin.
3. Oyuna girip `/radar` komutunu kullanarak radarı yönetebilirsiniz.

## Derleme (Build)

```bash
mvn clean package
```

## Lisans

Bu proje MIT Lisansı altında sunulmaktadır.
