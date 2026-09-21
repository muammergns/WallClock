![Badge](https://hitscounter.dev/api/hit?url=https%3A%2F%2Fgithub.com%2Fmuammergns%2FWallClock&label=Visit&icon=award&color=%23198754&message=&style=for-the-badge&tz=Asia%2FIstanbul)

# WallClock
## Eski android cihazlar için masa saati uygulaması
- Atıl vaziyetteki android cihazları değerlendirmek amaçlandı.
- openweathermap.org ile hava durumu bilgisi alındı.
- api'nin sağladığı tüm diller ve metrikler destekleniyor.
- hava durumu özelliğini kullanabilmek için openweathermap.org sitesinden ücretsiz üyelik alınıp api key edinilebilir.
- uygulama üzerinden api key ve şehir(ilçeleri de destekliyor) girilerek hava durumu bilgisi alınabilir.

- cihazın dokunmatiği bozuk olabilir, sadece ayar girmek için harici klavye/mouse bağlanabilir.
- ayarlar penceresi kaydırılabilir, ekrana sığmayan alanlara kaydırarak ya da klavye/mouse ile ulaşılabilir.

- hava durumu ikonu gösterilmez. bunun yerine güncel hava durumuna göre arka plan ve hareketli efekt gösterilir.
5-10 metre uzaktan bakıldığında ne olduğu anlaşılsın diye efektler büyük ve az sayıdadır.
  - gündüz arka plan mavi, gece siyahtır. gece/gündüz bilgisi openweathermap ikon kodunun son harfinden (d/n) alınır.
  - efekt, ikon koduna göre seçilir:
    - 01: açık. gündüz efekt yok, gece yıldızlar.
    - 02, 03, 04: bulutlu. koda göre bulut sayısı artar.
    - 09, 10: yağmur. 09 daha seyrek ve kısadır.
    - 11: fırtına. yağmur ve seyrek, yumuşak şimşek.
    - 13: kar.
    - 50: sis.
  - açılışta ve veri alınamadığında arka plan siyahtır, efekt yoktur.
  - ikon indirilmediği için eski cihazlarda ikon kaynaklı SSL sorunu yaşanmaz.
- efekt kare hızı ayarlar penceresinden (Effect FPS) değiştirilebilir. varsayılan değer 2'dir.
0 girilirse efekt tamamen kapanır, en fazla 30 kabul edilir. düşük değer eski cihazların ısınmasını ve pil kullanımını azaltır.
- sarsıntı alarmı: cihazın ivmeölçeriyle sarsıntı algılanırsa 3 saniye boyunca arka plan kırmızı olur,
yarım saniyede bir kısa beyaz ışık patlaması görünür ve bip sesi çalar. 3 saniye sonra normale döner ve algılama yeniden başlar.
  - iki aşamalı algılama yapılır, biri yeterlidir: çok hassas salınım kontrolü (masayı hafifçe sallamak gibi ileri geri hareket)
  ve daha az hassas pik kontrolü (masaya vurmak gibi sert darbe).
  - hassasiyet ayarlar penceresinden (Shake Alert Sensitivity) 0-10 arası ayarlanır. 10 en hassas (varsayılan), 0 kapalıdır.
  sensör kalitesi cihazdan cihaza değiştiği için her cihazda ayrıca denenmelidir.
  - ses seviyesine uygulama müdahale etmez. alarm ses kanalının seviyesi cihazdan ayarlanmalıdır.
  - erken uyarı sistemi değildir, yalnızca sarsıntıyı algılar.
- hava durumu ve tahmin bilgisi alınamazsa son durum korunur. üst üste 3 denemede (30 dakika) alınamazsa
ekran açılıştaki haline döner: siyah arka plan, sıcaklık ve tahmin yazıları boş.

- eski android cihazlarda SSL problemi yaşanabildiğinden bağlantı şu şekilde kurulur:
  - önce güvenli https bağlantısı denenir.
  - https ile bağlanılamazsa http ile denenir. http ile veri gelirse sorunun bağlantıda değil SSL'de olduğu anlaşılır
  ve sonraki denemeler önce http ile yapılır. son başarılı yöntem bellekte tutulur, uygulama yeniden başlayınca https ile başlanır.
  - ikisiyle de bağlanılamazsa bağlantı sorunu vardır. üst üste 3 denemede (30 dakika) veri alınamazsa
  ekran açılıştaki haline (siyah arka plan, hava durumu yazıları boş) döner.
  - sunucu hata cevabı verirse (yanlış api key, bulunamayan şehir gibi) bu bağlantı sorunu sayılmaz, yöntem değiştirilmez.
  - android 9 ve üzeri cihazlarda sistem düz http bağlantısını varsayılan olarak engellediği için yedek yöntem çalışmaz, yalnızca https kullanılır.
- ancak: 
  - hava durumu bilgisi gibi önemsiz veriler alındığından
  - cihaz eski olduğu için kişisel veri bulundurulmadığından
  - ev ağında sorunsuz ve güvenli bir şekilde çalışabileceğinden
- SSL ile bağlanmak gerekli değildir.
