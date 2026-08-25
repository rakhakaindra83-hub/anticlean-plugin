# 01 — Cara Pembuatan Plugin AntiClean

Dokumentasi proses pembuatan plugin **AntiClean** (Paper 1.21) dari nol sampai JAR siap deploy.

---

## 1. Spesifikasi Tujuan

Anticheat *lite* untuk server kecil/SMP, dengan 4 check dasar + sistem hukuman:

| # | Fitur | Tujuan |
|---|-------|--------|
| 1 | Speed check | Deteksi gerak horizontal melebihi sprint-jump vanilla, plus rollback posisi |
| 2 | Fly check | Deteksi kenaikan vertikal mustahil saat melayang |
| 3 | NoFall check | Deteksi pemain jatuh jauh tapi tidak kena damage jatuh |
| 4 | Reach check | Deteksi pukulan dari jarak melebihi survival reach |

Prasyarat desain: **satu file Java** saja, tanpa dependency tambahan (tanpa Maven/Gradle/packet-lib), dan sistem VL sederhana — alert ke admin lalu kick otomatis di VL 30. Konfigurasi ambang sengaja TIDAK dibuat sebagai config.yml (YAGNI untuk anticheat lite); semua tunables berupa konstanta `static final` yang diedit bersama rebuild.

## 2. Persiapan Environment (Windows)

Alat yang dipakai (sama dengan pola project plugin sebelumnya):

| Alat | Lokasi | Fungsi |
|------|--------|--------|
| JDK 21 | `%LOCALAPPDATA%\tools\jdk-21*` | Wajib — MC 1.20.5+ butuh Java 21 |
| paper-api | `%LOCALAPPDATA%\tools\mc-libs\paper-api-1.21.8.jar` | API Bukkit/Paper untuk compile |

AntiClean hanya butuh API inti Bukkit (`JavaPlugin`, event, scheduler) — tidak menyentuh tipe Adventure secara langsung, jadi classpath-nya cuma satu jar paper-api. Tanpa Maven, tanpa shading.

## 3. Struktur Proyek

```
anticlean-plugin/
├── build.sh                          ← script build (javac + jar cf)
├── rcon_test.py                      ← klien RCON mini untuk uji headless
├── src/
│   ├── plugin.yml                    ← metadata plugin (HARUS di root JAR)
│   └── id/kuru/anticlean/
│       └── AntiCheatPlugin.java      ← main class + listener + check + command (semuanya)
└── build/                            ← output compile (classes + plugin.yml), input pack JAR
```

Keputusan desain: **satu file** karena totalnya ~150 baris — memecah jadi banyak listener hanya menambah file tanpa nilai.

## 4. Langkah Pembuatan

### Langkah 1 — plugin.yml

File pertama. Menentukan nama `AntiClean`, main class `id.kuru.anticlean.AntiCheatPlugin`, `api-version: '1.20'`, registrasi command `ac` dengan permission `anticlean.admin`, plus permission kedua `anticlean.alerts` untuk penerima alert chat. Keduanya default `op`. Detail isi ada di `03-sistem-command.md`.

### Langkah 2 — Kerangka main class

Satu class `AntiCheatPlugin extends JavaPlugin implements Listener`. Isi awalnya:

- Konstanta tunables di atas class (`MAX_SPEED`, `MAX_VY_UP`, `MAX_FALL_DIST_NO_DMG`, `MAX_REACH`, `VL_TO_KICK`, dst.) — lengkap dengan komentar satuan blok/tick.
- `record Sample(double dx, double dz, long t)` dan inner class `Data` untuk state per-pemain.
- `Map<UUID, Data> data = new ConcurrentHashMap<>()` — semua state anticheat per UUID.

### Langkah 3 — Task tick + checks gerakan

Di `onEnable()`, daftarkan task berulang period 1 tick yang memanggil `tick(p, d)` untuk tiap pemain online. Method `tick()` adalah inti plugin: menghitung delta posisi per-tick lalu menjalankan check **Speed**, **Fly**, dan **NoFall** (rincian algoritma di `docs/02-fungsi-per-code.md`). Guard-nya: pemain mati, naik kendaraan, spectator, atau OP → dilewati.

### Langkah 4 — Listener damage

Dua `@EventHandler`:
- `onDamage(EntityDamageEvent)` — reset penanda jatuh saat FALL damage sah terjadi, supaya jatuh legal tidak salah flag NoFall.
- `onHit(EntityDamageByEntityEvent)` — ukur jarak mata-penyerang ke target; lebih dari `MAX_REACH` → flag **Reach**.

### Langkah 5 — Sistem VL + command

Method `flag(player, check, info, amount)`: tambah VL, broadcast alert ke pemain ber-permission `anticlean.alerts` atau OP, log console, kick bila VL ≥ 30. Lalu pasang executor lambda untuk `/ac` (status + subcommand `vl`). Lengkap di `03-sistem-command.md`.

## 5. Build

Build memakai pola javac + jar (script `build.sh` ala project tweaks-plugin). Empat hal yang dilakukan:

```bash
TOOLS="$LOCALAPPDATA/tools"
JDK21=$(ls -d "$TOOLS"/jdk-21* | head -1)

# 1. Compile — output ke folder build/, classpath hanya paper-api
"$JDK21/bin/javac" --release 21 -encoding UTF-8 \
    -cp "$TOOLS/mc-libs/paper-api-1.21.8.jar" \
    -d build src/id/kuru/anticlean/AntiCheatPlugin.java

# 2. Salin plugin.yml ke root build/ — WAJIB ikut masuk JAR
cp src/plugin.yml build/

# 3. Pack JAR
"$JDK21/bin/jar" cf AntiClean-1.0.0.jar -C build .
```

Catatan penting:

- `plugin.yml` harus di **root JAR** — kalau ikut ke dalam folder paket, server tidak mengenali plugin.
- Classpath Windows memakai `;` kalau ada lebih dari satu jar (di sini cukup satu).
- `-encoding UTF-8` wajib karena pesan plugin memakai karakter § (section sign).
- Output compile lama di `build/` aman ditimpa; hapus manual kalau mau build benar-benar bersih.

Hasil: `AntiClean-1.0.0.jar` (~beberapa KB).

## 6. Pengujian Headless (RCON)

Tanpa buka game client:

1. `server.properties`: `enable-rcon=true`, `rcon.port=25586`, `rcon.password=...`.
2. Salin JAR ke `plugins/` lalu start server Paper.
3. Jalankan `python rcon_test.py "ac vl Notch"` — script login RCON, kirim command, cetak balasannya (VL pemain).

Skenario in-game yang layak dicek: sprint-jump normal tidak flag, creative fly tidak flag (OP/spectator otomatis dilewati), jatuh dari menara 10 blok tetap kena damage & tidak flag, `/ac vl <nama>` mengembalikan angka VL.

## 7. Deploy

Salin JAR ke folder `plugins/` server → restart. AntiClean tidak punya folder data/config — begitu aktif, task tick langsung berjalan dan log `"AntiClean aktif"` muncul di console.
