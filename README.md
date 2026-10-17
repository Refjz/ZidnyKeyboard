# ZidnyKeyboard

Keyboard Android yang bisa langsung menerjemahkan kalimat yang sudah diketik ke bahasa lain dengan satu tombol.
Penerjemahan berjalan di server Cloudflare (Workers AI) yang hanya melayani aplikasi asli dari Google Play. Lihat [server/README.md](server/README.md) untuk cara memasangnya.

Bahasa: Indonesia, Inggris, Prancis, Mandarin, Jepang.

## Cara pakai (rencana)

1. Pasang APK dari halaman Releases.
2. Buka aplikasi ZidnyKeyboard, aktifkan keyboard di Pengaturan, lalu pilih sebagai keyboard aktif.
3. Ketik kalimat di aplikasi chat mana pun, pilih bahasa tujuan, tekan tombol terjemah.

## Build

```
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/debug/`. Setiap push ke GitHub juga membuat APK otomatis (lihat tab Actions).
