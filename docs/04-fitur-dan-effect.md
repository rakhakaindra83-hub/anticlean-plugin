# 04 — Fitur & Effect In-Game AntiClean

Penjelasan gameplay: apa yang dirasakan pemain/admin dari setiap check, termasuk efek sampingnya.

**Penting:** AntiClean TIDAK punya config.yml — semua ambang adalah konstanta di `AntiCheatPlugin.java` (kolom "Diatur oleh" di bawah menunjuk konstanta tersebut). Mengubah nilai = edit kode → rebuild.

---

## Fitur 1 — Speed Check + Rollback

**Diatur oleh:** `MAX_SPEED = 0.36` (blok/tick horizontal)

### Cara kerja singkat
Tiap tick, plugin menghitung jarak horizontal yang ditempuh pemain dibanding posisi tick sebelumnya. Lebih dari 0.36 blok/tick (≈7.2 blok/detik) di world yang sama → flag Speed **dan** pemain langsung diteleport balik ke posisi tick lalu (arah pandang dipertahankan).

### Effect in-game

| Skenario | Vanilla | Dengan plugin |
|----------|---------|---------------|
| Sprint biasa / sprint-jump | ±5.6–7 blok/detik | lolos — ambang 0.36 sudah di atas puncak sprint-jump |
| Speed hack ringan | bergerak nyata lebih cepat | tiap tick "nempel di tempat" karena rollback; maju efektif tidak lebih cepat dari sah |
| Ender pearl / kereta / kuda | normal | kendaraan & teleport tidak dicek (guard `isInsideVehicle`) |
| Ice boat, slime bounce | cepat tapi vanilla | masih dalam batas; kasus ekstrem bisa false positive sesekali (+2 VL saja) |

Detail penting:
- Rollback membuat speed hack **tidak berguna**, bukan cuma dicatat — pelanggar tidak dapat keuntungan gerak sama sekali.
- Pemain OP dan spectator otomatis dilewati.
- Konsekuensi tiap flag: +2 VL, alert chat admin, dan rollback.

## Fitur 2 — Fly Check

**Diatur oleh:** `MAX_VY_UP = 0.50` (blok/tick naik)

### Cara kerja singkat
Setelah pemain benar-benar melayang beberapa tick berturut-turut (tidak di tanah, tidak ada blok solid persis di bawah kaki), kenaikan vertikal > 0.50 blok/tick dianggap terbang ilegal → flag Fly. Vanilla sendiri maksimal ±0.42 blok/tick di awal lompat lalu melambat karena gravitasi.

### Effect in-game

| Skenario | Vanilla | Dengan plugin |
|----------|---------|---------------|
| Lompat / lompat-lompat (bunny hop) | naik singkat lalu jatuh | lolos — mesin status butuh beberapa tick melayang murni sebelum mulai menghitung |
| Creative fly | bebas | tidak tersentuh (creative bukan spectator tapi vy creative fly < 0.5/tick; dan OP selalu skip) |
| Flight hack | melayang/naik terus | tiap tick kenaikan > 0.5 nambah VL; alert banjir ke admin sampai kick di VL 30 |
| Jetpack/elitra | didorong firework | dorongan firework vanilla < 0.5/tick — aman |

Detail penting:
- Check ini hanya **menandai**, tidak melakukan rollback vertikal — pemain fly tetap melayang sampai VL-nya mencapai 30 lalu di-kick.
- Berdiri di atas blok solid apa pun (termasuk tembok setinggi lutut) me-reset status "melayang" — meminimalkan false positive saat parkour.

## Fitur 3 — NoFall Check

**Diatur oleh:** `MAX_FALL_DIST_NO_DMG = 3.5` (blok), hanya mode Survival

### Cara kerja singkat
Plugin mencatat ketinggian Y saat pemain mulai jatuh. Saat mendarat, dihitung jarak jatuhnya. Jatuh > 3.5 blok tanpa pernah kena FALL damage → flag NoFall. Event damage jatuh yang sah me-reset penanda jatuh, jadi jatuh legal tidak salah flag.

### Effect in-game

| Skenario | Vanilla | Dengan plugin |
|----------|---------|---------------|
| Jatuh dari menara 20 blok | mati / sisa sedikit HP | identik — damage tetap datang, tidak flag |
| NoFall hack / spam water-bucket ml | nol damage dari jatuh jauh | +2 VL tiap mendarat jauh tanpa damage |
| Mendarat di jerami / air / slime | damage dikurangi/dihilangkan vanilla | mekanik vanilla tetap jalan; kalau event FALL damage tetap terpicu, tidak di-flag |
| Mode Adventure/Creative | — | hanya Survival yang dicek |

Detail penting:
- Ambang 3.5 ≈ batas aman vanilla (fall damage mulai di atas 3 blok).
- Ukuran jarak pakai delta Y start-jatuh vs Y mendarat — pendekatan lite, bukan penghitung fall distance internal Minecraft.

## Fitur 4 — Reach Check

**Diatur oleh:** `MAX_REACH = 3.35` (blok, dari mata penyerang)

### Cara kerja singkat
Saat pemain memukul LivingEntity (`EntityDamageByEntityEvent`), plugin mengukur jarak 3D mata penyerang ke lokasi target. Lebih dari 3.35 blok (dan bukan OP) → flag Reach.

### Effect in-game

| Skenario | Vanilla | Dengan plugin |
|----------|---------|---------------|
| PvP jarak dekat normal | reach ±3 blok | lolos |
| Reach hack (memukul dari 4–6 blok) | korban kena pukul "dari luar jangkauan" | pukulan tetap masuk (damage tidak dibatalkan) tapi VL terus naik → alert → kick |
| Memukul lewat tembok / hit lewat sudut | kadang sah secara client | bisa false positive (+2 VL); jarang dan tidak fatal |
| Panah, trident, potion | — | bukan melee `EntityDamageByEntityEvent` damager-player → tidak dicek |

Detail penting:
- Plugin ini TIDAK membatalkan pukulan jarak jauh — hanya mendeteksi dan menumpuk VL. Filosofi lite: biarkan sistem VL/kick yang menghukum, jangan ganggu combat legit.
- Jarak diukur ke titik lokasi target (bukan permukaan hitbox), makanya ambang diberi toleransi di atas 3.0.

## Sistem VL — Alert & Kick Otomatis

**Diatur oleh:** `VL_TO_KICK = 30`, tiap flag bernilai **+2**

### Cara kerja singkat

```java
d.vl += amount;                       // +2 per pelanggaran
// broadcast ke pemain anticlean.alerts / OP:
// [AC] Steve gagal Speed (h=0.87) vl=12
if (d.vl >= VL_TO_KICK) {             // 15 pelanggaran total
    p.kickPlayer("§cAntiClean: gerakan tidak sah terdeteksi");
    d.vl = 0;
}
```

### Effect in-game

| Aspek | Efek |
|-------|------|
| Bagi pemain biasa | main normal = tidak pernah melihat efek apa pun dari plugin. |
| Bagi pelanggar | 15 kali tertangkap (= VL 30) → ditendang dengan pesan merah. Setelah join lagi, VL mulai dari 0. |
| Bagi admin | alert chat real-time tiap flag (siapa, check apa, nilainya berapa, VL sekarang). Pantau lanjutan via `/ac vl <nama>`. |
| Console | setiap flag juga masuk log server — bisa diaudit belakangan. |

## Interaksi Antar-Fitur & Batasan

- Keempat check menulis ke **satu VL yang sama** per pemain — campuran cheat cepat terkumpul menuju kick.
- Guard global: pemain mati, naik kendaraan, spectator, atau OP tidak pernah dicek fitur mana pun.
- VL tidak meluruh; entri data pemain tidak dibersihkan saat keluar (satu entri kecil per UUID selama runtime).
- Belum ada: AutoClicker/Timer/FastPlace (butuh packet-level, lihat catatan upgrade path di `02-fungsi-per-code.md` §8).

## Cheat-Sheet Admin Sehari-hari

```text
/ac                        # plugin hidup? berapa pemain terlacak
/ac vl <nama>              # cek VL pemain yang barusan muncul alert
# ubah ambang? edit konstanta di src/.../AntiCheatPlugin.java lalu build ulang
```
