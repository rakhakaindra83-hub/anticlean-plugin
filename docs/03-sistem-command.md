# 03 — Sistem Command AntiClean

Referensi lengkap semua perintah, sintaks, permission, dan perilakunya.

---

## Ringkasan

Plugin punya **satu command**: `/ac`. Seluruh logika command berada di executor lambda dalam `AntiCheatPlugin.onEnable()` — tanpa class executor terpisah.

```
/ac            → status ringkas
/ac vl <player> → lihat violation level pemain
```

| Subcommand | Sintaks | Fungsi | Butuh argumen |
|------------|---------|--------|---------------|
| *(tanpa)* | `/ac` | Status: jumlah data terlacak + hint pemakaian | — |
| `vl` | `/ac vl <nama>` | Tampilkan VL pemain target | Ya (nama online) |

## Permission

| Permission | Default | Mengatur |
|-----------|---------|----------|
| `anticlean.admin` | `op` | SELURUH command `/ac`. Dideklarasikan di plugin.yml (`commands.ac.permission`) sehingga server menolak non-admin **sebelum** kode executor dieksekusi; ada pengecekan ganda di dalam lambda. |
| `anticlean.alerts` | `op` | Bukan untuk command — menentukan siapa yang menerima alert chat `[AC] ... gagal <Check>` tiap ada pelanggaran. OP selalu menerima alert meski tanpa permission ini. |

Beri ke admin lain: `/lp user <nama> permission set anticlean.admin true` (LuckPerms) atau `/op`.

## Detail Per Perintah

### `/ac`

- Cek `anticlean.admin`; tanpa itu langsung return (diblok duluan oleh Bukkit lewat plugin.yml).
- Balasan satu baris:

```
[AntiClean] Online: 3 | /ac vl <player>
```

- Angka "Online" adalah `data.size()` — jumlah entri state yang sedang dilacak plugin (bertambah saat pemain pertama kali di-tick; tidak dibersihkan saat keluar, lihat catatan keterbatasan).

### `/ac vl <nama>`

1. Argumen harus tepat dua token: `vl` + nama.
2. Cari pemain via `Bukkit.getPlayer(nama)` — hanya cocok pemain yang **online**.
3. Balasan:

```
VL Notch: 4
```

- Pemain tidak ditemukan → `VL Nama: ?`.
- VL adalah angka akumulasi pelanggaran (+2 per flag); mencapai 30 → kick otomatis dan VL-nya reset ke 0. VL tidak meluruh sendiri — cuma turun lewat kick atau restart server.
- Pemakaian tipikal: alert `[AC] Steve gagal Fly (vy=0.72) vl=8` muncul di chat → admin jalankan `/ac vl Steve` sesekali untuk memantau apakah dia terus menumpuk VL mendekati kick.

## Contoh Pemakaian Nyata

```text
/ac                          # cek plugin hidup & jumlah pemain terlacak
/ac vl Steve                 # Steve kebagian flag? sekarang vl berapa
/ac vl Herobrine             # offline/tidak ada -> "VL Herobrine: ?"
python rcon_test.py "ac vl Steve"   # sama, dari console/headless via RCON
```

## Pesan Error & Edge Case

| Situasi | Perilaku |
|---------|----------|
| Non-admin pakai `/ac` | Blokir oleh permission check Bukkit ("I'm sorry but you do not have permission..."). |
| `/ac vl` tanpa nama / argumen lebih | Hanya tampil baris status, tidak error. |
| Target offline / salah ketik | `VL <nama>: ?` |
| Pemain online tapi belum punya entri data | Aman secara praktis — entri dibuat otomatis pada tick pertama setelah join. |

## Tab Completion

**Belum ada.** Executor dipasang sebagai lambda tanpa `TabCompleter`, jadi Bukkit tidak menyarankan apa pun saat mengetik. Karena subcommand-nya cuma satu (`vl`) dan argumennya nama pemain bebas, tab completion sengaja dilewati (YAGNI). Kalau nanti mau ditambah, polanya:

```java
getCommand("ac").setTabCompleter((s, c, a) ->
    a.length == 1 ? List.of("vl") : null);
```
