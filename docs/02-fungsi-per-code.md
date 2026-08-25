# 02 — Fungsi Per Code (Penjelasan Baris Per Bagian)

Dokumentasi teknis: fungsi setiap file, class, dan method di source `AntiClean`. Semua kode berada di satu file: `src/id/kuru/anticlean/AntiCheatPlugin.java`.

---

## 1. Tunables (Konstanta Class)

Keputusan desain di komentar kode sendiri: *"bukan config.yml — YAGNI; edit & rebuild kalau perlu"*.

```java
private static final double MAX_SPEED = 0.36;           // blok/tick horizontal (~7.2 b/s)
private static final double MAX_VY_UP = 0.50;           // naik lebih cepat dari ini = fly
private static final double MAX_FALL_DIST_NO_DMG = 3.5; // > ini tanpa damage = NoFall
private static final double MAX_REACH = 3.35;           // survival reach
private static final int    MAX_CPS = 20;               // klik/detek (belum dipakai — lihat §8)
private static final int    VL_TO_KICK = 30;
```

Satuan speed/fly adalah **blok per tick** (20 tick/detik): sprint vanilla ±0.28 b/tick, sprint-jump puncak ±0.35 — makanya ambangnya 0.36.

## 2. State: `record Sample` & `class Data`

| Anggota | Isi | Fungsi |
|---------|-----|--------|
| `Sample(dx, dz, t)` | record kecil delta posisi + timestamp | Bekas cetakan sampai gerakan (saat ini belum dipakai oleh check mana pun). |
| `Data.lastTickLoc` | `Location` | Posisi pemain pada tick sebelumnya — acuan hitung delta dan rollback Speed. |
| `Data.wasOnGround`, `lastGroundBeforeAir` | boolean | Mesin status untuk Fly check: sudah berapa lama benar-benar melayang. |
| `Data.fallStart` | double (`NaN` = tidak jatuh) | Y saat mulai airborne — dasar hitung jarak jatuh untuk NoFall. |
| `Data.vl` | int | Violation level aktif pemain. |
| `data` | `Map<UUID, Data>` (ConcurrentHashMap) | Semua state di atas, per UUID. |

## 3. `onEnable()`

Urutan bootstrap:

1. Registrasi listener (`registerEvents`) — untuk event damage & pukulan.
2. Pasang task scheduler period **1 tick** yang loop semua pemain online → `tick(p, d)` dengan `computeIfAbsent` agar state dibuat malas.
3. Pasang executor lambda untuk command `/ac`.
4. Log `"AntiClean aktif"`.

## 4. `tick(Player p, Data d)` — Inti Plugin

Dijalankan tiap tick per pemain. Guard dulu: skip jika world belum diketahui, pemain mati, naik kendaraan, gamemode SPECTATOR, atau **OP**.

### Speed

```java
double h = Math.sqrt(dx * dx + dz * dz);
if (h > MAX_SPEED && now.getWorld().equals(last.getWorld())) {
    flag(p, "Speed", "h=" + String.format("%.2f", h), 2);
    p.teleport(last.setDirection(now.getDirection())); // rollback ke posisi valid
}
```

Delta horizontal per-tick melebihi 0.36 → flag + **rollback**: teleport kembali ke posisi tick lalu, tapi arah pandang pemain tetap dipertahankan (tidak pusing).

### Fly (mesin status melayang)

```java
if (!ground && !nearGround && !d.wasOnGround && !d.lastGroundBeforeAir) {
    double vy = now.getY() - last.getY();
    if (vy > MAX_VY_UP) flag(p, "Fly", "vy=" + String.format("%.2f", vy), 2);
}
```

`nearGround` = di tanah ATAU blok persis di bawah kaki solid (supaya lompatan/lompat-tangga tidak dicurigai). Check baru aktif setelah beberapa tick berturut-turut benar-benar di udara — begitu melayang stabil, kenaikan vertikal > 0.50 blok/tick dianggap fly (vanilla gravitasi membatasi naik ±0.42 saat awal lompat lalu melambat).

### NoFall

```java
if (!ground && Double.isNaN(d.fallStart)) d.fallStart = last.getY(); // mulai jatuh
if (ground && !Double.isNaN(d.fallStart)) {                          // mendarat
    double fall = d.fallStart - now.getY();
    d.fallStart = Double.NaN;
    if (fall > MAX_FALL_DIST_NO_DMG && p.getGameMode() == GameMode.SURVIVAL) {
        flag(p, "NoFall", "dist=" + String.format("%.1f", fall), 2);
    }
}
```

Catat Y saat mulai airborne; saat mendarat bandingkan. Jarak jatuh > 3.5 blok dalam mode Survival → flag. Diakhiri update `d.wasOnGround = ground`.

## 5. `onDamage(EntityDamageEvent)` — Reset NoFall

```java
if (e.getCause() == DamageCause.FALL) {
    Data d = data.get(p.getUniqueId());
    if (d != null) d.fallStart = Double.NaN;
}
```

Saat FALL damage sah terjadi, penanda jatuh dibuang. Tujuannya supaya jatuh legal (yang memang kena damage) tidak masuk hitungan NoFall.

## 6. `onHit(EntityDamageByEntityEvent)` — Reach Check

Paper tidak punya event "reach" langsung, jadi dipasang di event serangan:

```java
double dist = eyeDistance(p, target.getLocation());
if (dist > MAX_REACH && !p.isOp()) flag(p, "Reach", "d=" + String.format("%.2f", dist), 2);
```

Jarak diukur dari **mata penyerang** ke lokasi target (3D, via `eyeDistance()`), bukan ke hitbox — pendekatan lite; serangan sprint-jarak-ekstrem bisa sesekali false positive.

## 7. `flag(Player p, String check, String info, int amount)` — Sistem VL

1. `d.vl += amount` (semua check memanggil dengan amount 2).
2. Broadcast alert chat ke semua pemain ber-permission `anticlean.alerts` atau OP:
   `§8[§cAC§8] §7<Nama> gagal §b<Check> §8(<info>) §7vl=§e<VL>` — contoh: `[AC] Steve gagal Speed (h=0.87) vl=6`.
3. Log baris yang sama ke console/log file.
4. `vl >= 30` → `p.kickPlayer("§cAntiClean: gerakan tidak sah terdeteksi")` lalu VL di-reset ke 0.

Tidak ada peluruhan VL (decay) — VL hanya turun lewat kick atau restart server.

## 8. Yang Sengaja Belum Ada (catatan `ponytail:` di kode)

- **AutoClicker / Timer / FastPlace** butuh inspeksi paket jaringan — komentar upgrade path: tambah dependency `io.github.retrooper.packetevents` lalu pindahkan check ke `PacketListener`. Konstanta `MAX_CPS` dan `record Sample` adalah bekas fondasi itu (belum dipakai).
- Import `PlayerQuitEvent` / `PlayerTeleportEvent` / `Vector` ada di source tapi belum dipakai — entri `data` juga belum dibersihkan saat pemain keluar (satu entri kecil per UUID selama runtime).

## 9. `src/plugin.yml`

| Key | Nilai | Arti |
|-----|-------|------|
| `name` | `AntiClean` | Nama plugin, tampil di `/plugins`. |
| `main` | `id.kuru.anticlean.AntiCheatPlugin` | Class yang diinstansiasi server saat enable. |
| `version` | `1.0.0` | Versi. |
| `api-version` | `'1.20'` | Target API minimal. |
| `description` | Anticheat lite - speed/fly/nofall/reach + VL system | Deskripsi. |
| `commands.ac` | `permission: anticlean.admin` | Server memblokir non-admin sebelum kode executor jalan. |
| `permissions.anticlean.admin` / `.alerts` | `default: op` | Akses command & penerima alert. |

## 10. `rcon_test.py`

Klien RCON socket Python (~25 baris): login type 3 → kirim command type 2 dari argv (default `ac vl Notch`) → cetak respons. Untuk uji headless tanpa client game.
