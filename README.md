# WallClock
## Eski android cihazlar için masa saati uygulaması
- Atıl vaziyetteki android cihazları değerlendirmek amaçlandı.
- openweathermap.org ile hava durumu bilgisi alındı.
- api'nin sağladığı tüm diller ve metrikler destekleniyor.
- hava durumu özelliğini kullanabilmek için openweathermap.org sitesinden ücretsiz üyelik alınıp api key edinilebilir.
- uygulama üzerinden api key ve şehir(ilçeleri de destekliyor) girilerek hava durumu bilgisi alınabilir.

- cihazın dokunmatiği bozuk olabilir, sadece ayar girmek için harici klavye/mouse bağlanabilir.

- android 5.0 ve alt versiyonlarda SSL problemi yaşandığından güvenli https bağlantısı kurulamıyor.
bu yüzden http ile bağlantı kurulmaktadır.
- ancak: 
  - hava durumu bilgisi gibi önemsiz veriler alındığından
  - cihaz eski olduğu için kişisel veri bulundurulmadığından
  - ev ağında sorunsuz ve güvenli bir şekilde çalışabileceğinden
- SSL ile bağlanmak gerekli değildir.
