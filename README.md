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

- güncel hava durumu için ikon gösterilmez. bunun yerine güncel hava durumuna göre arka plan ve hareketli efekt gösterilir.
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
- tahmin şeridi gelecek 24 saati 3 saatlik adımlarla 8 kutuda gösterir: üstte saat, altta ikon ve sıcaklık.
  - ikonlar [Weather Icons](https://github.com/erikflowers/weather-icons) yazı tipindendir (SIL OFL 1.1). yazı tipi uygulamayla gelir, internetten ikon indirilmez.
  - ikon openweathermap durum koduna göre seçilir, gece/gündüz ikon kodunun son harfinden alınır.
- deprem satırı: saatin altında, yakında hissedilmiş olabilecek en son deprem "büyüklük · uzaklık · tarih · yer" olarak gösterilir.
  - konum, hava durumu için girilen şehrin openweathermap koordinatıdır.
  - hissedilme uzaklığı büyüklüğe göre artar: 2.5 → 10 km, 3 → 25 km, 4 → 75 km, 5 → 150 km, 6 ve üstü → 300 km. ara değerler doğrusal hesaplanır.
  bu uzaklık içindeki depremlerden tarihi en yeni olan gösterilir. değerler kaba tahmindir.
  - önce AFAD'a bakılır (son birkaç haftayı verir). orada yoksa ya da AFAD'a ulaşılamazsa EMSC'ye bakılır (saatte bir yenilenir).
  - EMSC ilçe adı vermediği için yer adı openweathermap'ten alınır. bulunamazsa (deniz gibi) koordinat yazılır.
  - alt büyüklük ayarlar penceresinden (Earthquake Min Magnitude) değiştirilebilir, varsayılan 2.5'tir. yazı boyutu Earthquake Text Size ile ayarlanır.
  - AFAD (GlobalSign Root R46) ve EMSC (ISRG Root X1) kök sertifikaları eski cihazlarda bulunmadığı için uygulamayla gelir.
  android 4.1-4.4'te TLS 1.1/1.2 açılır. AFAD en az TLS 1.1 istediği için android 4.0'da yalnızca EMSC çalışır.
  - veri alınamazsa son gösterilen deprem korunur.
- efekt kare hızı ayarlar penceresinden (Effect FPS) değiştirilebilir. varsayılan değer 2'dir.
0 girilirse efekt tamamen kapanır, en fazla 30 kabul edilir. düşük değer eski cihazların ısınmasını ve pil kullanımını azaltır.
- sarsıntı alarmı: cihazın ivmeölçeriyle sarsıntı algılanırsa 3 saniye boyunca arka plan kırmızı olur,
yarım saniyede bir kısa beyaz ışık patlaması görünür ve bip sesi çalar. 3 saniye sonra normale döner ve algılama yeniden başlar.
  - tek bir değerin büyüklüğüne değil, sarsıntının ne kadar sürdüğüne bakılır. sarsıntı en az 3 saniye kesintisiz sürerse alarm verilir.
  - cihazın sakin ortamdaki gürültüsü sürekli ölçülür ve ölçülen değerden güçte çıkarılır: sarsıntı = √(ölçülen² − gürültü²).
  böylece sensörü gürültülü cihazlarda da aynı eşik kullanılabilir.
  - eşik ayarlar penceresinden (Shake Alert Threshold) girilir, değer 100'e bölünerek m/s² olur. varsayılan 4 (0.04 m/s²), 0 kapalıdır.
  küçük değer daha hassastır. eski sürümde kalan 10 değeri artık en az hassas ayar demektir, elle düşürülmelidir.
  - masaya vurma ve çarpma da sehpayı birkaç saniye sallayabildiği için alarm verebilir. bunlar depremden ayrılamaz.
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
