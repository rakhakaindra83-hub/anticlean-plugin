# AntiClean

Plugin **Paper 1.21** — anticheat lite dalam satu file Java:

- **Speed check** — gerak horizontal per-tick > 0.36 blok = flag + rollback posisi
- **Fly check** — naik lebih cepat dari 0.50 blok/tick saat sudah lama di udara = flag
- **NoFall check** — mendarat dari > 3.5 blok tanpa damage jatuh (mode Survival) = flag
- **Reach check** — memukul entity dari jarak mata > 3.35 blok = flag
- **Sistem VL** — tiap pelanggaran +2 violation; alert chat ke admin, kick otomatis di VL 30
- Command admin `/ac` + `/ac vl <player>` untuk pantau violation (permission `anticlean.admin`)

Anti-cheat-nya sengaja *lite*: satu listener + satu task tick, tanpa dependency packet-level. Tunables (ambang speed/fly/nofall/reach) berupa konstanta di kode — edit & rebuild, bukan config.yml (keputusan YAGNI).

## Build

```bash
bash build.sh   # javac + jar cf, plugin.yml di root JAR
```

Atau manual (lihat rincian di `docs/01-cara-pembuatan.md`):

```bash
javac --release 21 -cp "$LOCALAPPDATA/tools/mc-libs/paper-api-1.21.8.jar" -d build src/id/kuru/anticlean/AntiCheatPlugin.java
cp src/plugin.yml build/
jar cf AntiClean-1.0.0.jar -C build .
```

## Test

Headless via RCON — lihat `rcon_test.py` (butuh server lokal dengan `enable-rcon=true`, port 25586):

```bash
python rcon_test.py "ac vl Notch"
```

## Dokumentasi

| File | Isi |
|------|-----|
| `docs/01-cara-pembuatan.md` | Membuat project dari nol: struktur, plugin.yml, compile & pack JAR |
| `docs/02-fungsi-per-code.md` | Fungsi tiap class/method + potongan kode |
| `docs/03-sistem-command.md` | Command `/ac`, permission, contoh pemakaian |
| `docs/04-fitur-dan-effect.md` | Efek tiap check ke gameplay + angka yang mengaturnya |
