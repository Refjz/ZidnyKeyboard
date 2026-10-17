# Kebijakan Privasi ZidnyKeyboard

_Terakhir diperbarui: 17 Oktober 2026 (terjemahan lewat server)_

ZidnyKeyboard adalah aplikasi keyboard Android yang bisa menerjemahkan teks yang Anda ketik. Halaman ini menjelaskan data apa yang diproses aplikasi dan bagaimana caranya.

## Ringkasan

- Teks yang Anda ketik **hanya dikirim saat Anda menekan Terjemahkan** (atau saat fitur terjemahan pesan disalin aktif), ke server terjemahan ZidnyKeyboard di Cloudflare. Teks diterjemahkan lalu langsung dibuang, tidak disimpan.
- Selain itu, teks yang Anda ketik tidak dikirim ke mana pun.
- Aplikasi ini **tidak** memakai akun atau pelacakan analitik dari kami.
- Layar pengaturan aplikasi menampilkan **iklan dari Google AdMob**. Keyboard-nya sendiri tidak menampilkan iklan, dan teks yang Anda ketik tidak pernah diberikan ke AdMob.
- Pengaturan Anda (tema, ukuran, bahasa tujuan) hanya disimpan di HP Anda.

## Data yang diproses

**Teks yang diketik.** Sebagai keyboard, ZidnyKeyboard menerima teks yang Anda ketik agar bisa memasukkannya ke kolom teks. Saat Anda menekan tombol Terjemahkan, teks di kolom tersebut dibaca dan dikenali bahasanya di perangkat Anda dengan Google ML Kit, lalu dikirim lewat koneksi terenkripsi (HTTPS) ke server terjemahan ZidnyKeyboard yang berjalan di Cloudflare Workers dan diterjemahkan dengan model Cloudflare Workers AI. Server hanya memakai teks untuk membuat terjemahan dan tidak menyimpannya. Cloudflare memproses data ini sesuai kebijakannya: https://www.cloudflare.com/privacypolicy/

**Saran kata dan kata yang dipelajari.** Untuk memberi saran kata dan memperbaiki salah ketik, aplikasi mencocokkan kata yang sedang Anda ketik dengan daftar kata yang ada di dalam aplikasi. Kata yang sering Anda ketik dicatat beserta jumlah pemakaiannya di penyimpanan lokal aplikasi agar bisa disarankan lagi. Catatan ini tidak pernah dikirim ke mana pun, dan tidak dibuat di kolom kata sandi, email, atau alamat web.

**Teks yang disalin (clipboard).** Jika fitur "Terjemahkan pesan yang disalin" aktif, saat keyboard terbuka aplikasi membaca teks yang baru Anda salin lalu mengirimnya ke server terjemahan yang sama untuk diterjemahkan ke bahasa Indonesia. Teks tersebut tidak disimpan. Fitur ini mati secara bawaan dan bisa diatur di pengaturan.

**Suara.** Jika Anda menekan tombol mikrofon, suara Anda diproses oleh layanan pengenal suara bawaan HP (biasanya milik Google) untuk diubah menjadi teks. Layanan tersebut bisa memproses suara secara online sesuai kebijakan privasi penyedianya. ZidnyKeyboard sendiri tidak merekam atau menyimpan suara Anda.

**Pengaturan.** Pilihan tema, tinggi tombol, ukuran huruf, getar, baris angka, dan bahasa tujuan disimpan di penyimpanan lokal aplikasi di HP Anda. Data ini terhapus jika aplikasi dihapus.

**Pemeriksaan keaslian aplikasi.** Setiap permintaan terjemahan menyertakan token Google Play Integrity. Token ini dibuat oleh Google Play di HP Anda dan berisi informasi apakah aplikasi asli dari Google Play dan apakah perangkat lolos pemeriksaan keamanan Google. Server kami mengirim token ini ke Google untuk dibaca, lalu hanya memakai hasilnya untuk memutuskan apakah terjemahan boleh diberikan. Token tidak berisi teks Anda dan tidak disimpan. Lihat https://policies.google.com/privacy

## Layanan pihak ketiga

Aplikasi ini memakai Google ML Kit (Language Identification) yang berjalan di perangkat, Google Play Integrity, dan Cloudflare Workers / Workers AI untuk terjemahan. Menurut dokumentasi Google, ML Kit dapat mengirim data penggunaan dan diagnostik secara terbatas ke Google untuk mengukur kinerja dan memperbaiki layanannya. Data ini tidak berisi teks yang Anda ketik. Informasi lebih lanjut: https://developers.google.com/ml-kit/terms dan https://policies.google.com/privacy

## Iklan (Google AdMob)

Layar pengaturan ZidnyKeyboard menampilkan iklan banner dari Google AdMob. Untuk menampilkan dan mengukur iklan, AdMob dapat mengumpulkan data seperti ID iklan perangkat, alamat IP, informasi perangkat, dan interaksi dengan iklan, sesuai kebijakan Google: https://policies.google.com/technologies/ads dan https://policies.google.com/privacy. Di wilayah yang mewajibkan persetujuan (misalnya Uni Eropa dan Inggris), aplikasi akan meminta persetujuan Anda terlebih dahulu. Anda dapat mereset atau mematikan personalisasi iklan lewat pengaturan Google di HP Anda. Iklan tidak pernah ditampilkan di keyboard, dan teks yang Anda ketik tidak dibagikan ke AdMob.

## Izin aplikasi

- **Internet:** untuk mengirim teks yang ingin diterjemahkan ke server terjemahan dan untuk iklan di layar pengaturan.
- **Mikrofon:** hanya saat Anda menekan tombol mikrofon untuk mengetik dengan suara.

## Anak-anak

Aplikasi ini tidak ditujukan secara khusus untuk anak-anak dan tidak dengan sengaja mengumpulkan data pribadi anak.

## Perubahan

Jika kebijakan ini berubah, versi terbaru akan dipasang di halaman ini dengan tanggal pembaruan yang baru.

## Kontak

Pertanyaan tentang kebijakan ini bisa diajukan melalui halaman Issues proyek: https://github.com/Refjz/ZidnyKeyboard/issues

---

# ZidnyKeyboard Privacy Policy (English)

_Last updated: 17 October 2026_

ZidnyKeyboard is an Android keyboard that can translate the text you type.

- Text is sent only when you tap Translate (or when "translate copied messages" is on): it goes over HTTPS to the ZidnyKeyboard translation server on Cloudflare Workers, is translated with Cloudflare Workers AI, and is discarded, not stored (Cloudflare policy: https://www.cloudflare.com/privacypolicy/). Language detection runs on the device.
- Each translation request carries a Google Play Integrity token that tells the server whether the app is the genuine Google Play version on a device that passes Google's checks. The server sends the token to Google to read it and does not store it.
- The app has no accounts and no analytics of its own.
- The settings screen (not the keyboard) shows banner ads from Google AdMob, which may collect the advertising ID, IP address, device information and ad interactions under Google's policies (https://policies.google.com/technologies/ads). Consent is asked where the law requires it. Typed text is never shared with AdMob.
- Your settings (theme, size, target language) are stored only on your device and are deleted when you uninstall the app.
- Google ML Kit may send limited usage and diagnostic data (not your typed text) to Google; see https://developers.google.com/ml-kit/terms and https://policies.google.com/privacy
- Words you type often are counted on your device so they can be suggested again; this list never leaves the device and is not kept for password, e-mail or web address fields.
- If "translate copied messages" is on (it is off by default), text you copy is read while the keyboard is open and sent to the same translation server. It is not stored.
- The microphone is used only when you tap the mic button; speech is converted to text by your phone's speech recognition service (usually Google), which may process it online under its own privacy policy.
- Permissions: Internet (translation server and ads) and microphone (for voice typing).
- Contact: https://github.com/Refjz/ZidnyKeyboard/issues
