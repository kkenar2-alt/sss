# Saha Sipariş Android

Bu paket, sadece Android için hazırlanmış saha satış / sipariş / dağıtım / cari / stok / alış / gider / envanter / LIFO kârlılık uygulaması başlangıç projesidir.

## İçinde hazır olanlar

- Android Kotlin kaynak proje
- Yerel SQLite veritabanı
- Cari / müşteri kartı
- Stok kartı
- Sipariş / satış girişi
- Dönem içi alış girişi
- Gider girişi
- Fire / düzeltme girişi
- Satış listesi
- Dönemsel envanter ve LIFO kârlılık raporu
- Cari konum kaydetme altyapısı
- Müşteri resmi sayısı / kamera açma altyapısı
- Kartvizit OCR bağlantı yeri
- Yapay zekâ rota planı için ilk rota sıralama altyapısı
- DİA entegrasyon notları

## Rapor formülü

```text
Kalan = Dönem Başı Stok
      + Dönem İçi Alışlar
      - Dönem İçi Satışlar
      - Fire / Düzeltme
```

Kâr hesabı:

```text
Kâr = Satış Tutarı - LIFO Maliyet
```

## Kurulum

1. ZIP dosyasını çıkarın.
2. Android Studio açın.
3. `Open` ile `SahaSiparisAndroid` klasörünü seçin.
4. Android Studio Gradle Sync yapacaktır.
5. SDK 36 yüklü değilse Android Studio yüklemeyi teklif eder.
6. Emülatör veya Android telefonda `Run` ile çalıştırın.

## Kullanılan teknoloji

- Kotlin
- Android SQLiteOpenHelper
- Native Android UI
- AGP 9.2.0
- Kotlin Android Plugin 2.4.0
- minSdk 23
- targetSdk 36

## Üretim sürümünde eklenecekler

### 1. DİA entegrasyonu

DİA kullanıcı adı, şifre, token ve şirket bilgileri APK içine yazılmamalıdır.
Güvenli yapı şöyle olmalıdır:

```text
Android App -> Güvenli API Servisi -> DİA
```

Android uygulama şu verileri senkronize etmelidir:

- Dönem başı stok
- Dönem içi alışlar
- Stok kartları
- Cari kartları
- Tedarikçiler
- Fiyat listeleri

### 2. Google Maps

Üretim sürümünde:

- Google Maps SDK for Android
- Routes API
- Route Optimization API

bağlanmalıdır. API anahtarı doğrudan APK içinde düz metin bırakılmamalıdır.

### 3. Kartvizit OCR

Üretim sürümünde:

- Google ML Kit Text Recognition v2
- ML Kit Document Scanner

ile kartvizit, cari kart, vergi levhası ve evrak fotoğraflarından otomatik veri okuma yapılmalıdır.

### 4. Fotoğraf yönetimi

Bu çekirdek sürüm kamera açar ve cari fotoğraf sayısını artırır. Üretim sürümünde:

- Dosya yolu
- Fotoğraf tipi
- Tarih/saat
- Kullanıcı
- GPS
- Bulut/merkez senkron

ayrı tabloda tutulmalıdır.

### 5. Yetkilendirme

Roller:

- Admin
- Satışçı
- Dağıtıcı
- Depocu
- Muhasebe

### 6. Offline çalışma

Satışçı internet yokken veri girebilmeli, internet gelince merkez/DİA senkronu yapılmalıdır.

## Önemli not

Bu paket APK değil, Android Studio projesidir. Bu ortamda Android SDK/Gradle olmadığı için APK burada derlenmedi. Android Studio'da açıp APK/AAB üretilebilir.
