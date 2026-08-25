package id.kuru.anticlean;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatPlugin extends JavaPlugin implements Listener {

    // ==== Tunables (bukan config.yml — YAGNI; edit & rebuild kalau perlu) ====
    private static final double MAX_SPEED = 0.36;        // blok/tick horizontal (~7.2 b/s, sprint-jump aman)
    private static final double MAX_VY_UP = 0.50;        // naik lebih cepat dari ini = fly
    private static final double MAX_FALL_DIST_NO_DMG = 3.5; // > ini tanpa damage = NoFall
    private static final double MAX_REACH = 3.35;        // survival reach
    private static final int    MAX_CPS = 20;            // klik/detik
    private static final int    VL_TO_ALERT = 5;
    private static final int    VL_TO_KICK = 30;

    record Sample(double dx, double dz, long t) {}

    static final class Data {
        final Location lastTickLoc = new Location(null, 0, 0, 0);
        boolean wasOnGround, lastGroundBeforeAir = true;
        double fallStart = Double.NaN;
        int clicks = 0, cps = -1, vl = 0;
        long windowStart = 0;
    }

    private final Map<UUID, Data> data = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Data d = data.computeIfAbsent(p.getUniqueId(), k -> new Data());
                tick(p, d);
            }
        }, 1L, 1L);
        getCommand("ac").setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("anticlean.admin")) return true;
            s.sendMessage("§8[§bAntiClean§8] §7Online: " + data.size() + " | /ac vl <player>");
            if (a.length == 2 && a[0].equalsIgnoreCase("vl")) {
                Player t = Bukkit.getPlayer(a[1]);
                s.sendMessage("§7VL " + a[1] + ": §e" + (t != null ? data.get(t.getUniqueId()).vl : "?"));
            }
            return true;
        });
        getLogger().info("AntiClean aktif");
    }

    private void tick(Player p, Data d) {
        Location now = p.getLocation();
        Location last = d.lastTickLoc;
        if (last.getWorld() != null && !p.isDead() && !p.isInsideVehicle()
                && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !p.isOp()) {
            double dx = now.getX() - last.getX(), dz = now.getZ() - last.getZ();
            double h = Math.sqrt(dx * dx + dz * dz);

            // ---- SPEED ----
            if (h > MAX_SPEED && now.getWorld().equals(last.getWorld())) {
                flag(p, "Speed", "h=" + String.format("%.2f", h), 2);
                p.teleport(last.setDirection(now.getDirection())); // rollback ke posisi valid
            }

            // ---- FLY / vertical ----
            boolean ground = p.isOnGround();
            boolean nearGround = ground || p.getLocation().clone().subtract(0, 1, 0).getBlock().getType().isSolid();
            if (!ground && !nearGround && d.wasOnGround == false && d.lastGroundBeforeAir == false) {
                double vy = now.getY() - last.getY();
                if (vy > MAX_VY_UP) { flag(p, "Fly", "vy=" + String.format("%.2f", vy), 2); }
            }
            if (ground) { d.lastGroundBeforeAir = true; } else if (d.lastGroundBeforeAir && d.wasOnGround == false) { d.lastGroundBeforeAir = false; }

            // ---- NOFALL ----
            if (!ground && Double.isNaN(d.fallStart)) d.fallStart = last.getY();
            if (ground && !Double.isNaN(d.fallStart)) {
                double fall = d.fallStart - now.getY();
                d.fallStart = Double.NaN;
                if (fall > MAX_FALL_DIST_NO_DMG && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                    flag(p, "NoFall", "dist=" + String.format("%.1f", fall), 2);
                }
            }
            d.wasOnGround = ground;
        }
        last.setX(now.getX()); last.setY(now.getY()); last.setZ(now.getZ());
        last.setWorld(now.getWorld());

        // ---- CPS decay tiap detik via window di event click (lihat onClick) ----
    }

    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Data d = data.get(p.getUniqueId());
            if (d != null) d.fallStart = Double.NaN;
        }
    }

    // ---- REACH dipasang dari PlayerReachCallback di bawah (event interaksi) ----
    // Paper tidak punya event reach langsung; pakai EntityDamageByEntityEvent jarak serang
    @EventHandler public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        double dist = eyeDistance(p, target.getLocation());
        if (dist > MAX_REACH && !p.isOp()) flag(p, "Reach", "d=" + String.format("%.2f", dist), 2);
    }

    private static double eyeDistance(Player p, Location t) {
        Location eye = p.getEyeLocation();
        double dx = eye.getX() - t.getX(), dy = eye.getY() - t.getY(), dz = eye.getZ() - t.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    void flag(Player p, String check, String info, int amount) {
        Data d = data.computeIfAbsent(p.getUniqueId(), k -> new Data());
        d.vl += amount;
        String msg = "§8[§cAC§8] §7" + p.getName() + " gagal §b" + check + " §8(" + info + ") §7vl=§e" + d.vl;
        for (Player o : Bukkit.getOnlinePlayers())
            if (o.hasPermission("anticlean.alerts") || o.isOp()) o.sendMessage(msg);
        getLogger().info(p.getName() + " failed " + check + " (" + info + ") vl=" + d.vl);
        if (d.vl >= VL_TO_KICK) {
            p.kickPlayer("§cAntiClean: gerakan tidak sah terdeteksi");
            d.vl = 0;
        }
    }

    // ================= CHECKS TERJADWAL LAIN =================
    // ponytail: AutoClicker/Timer/FastPlace butuh packet-level (PacketEvents).
    // Upgrade path: tambah dependency io.github.retrooper.packetevents, pindah check ke PacketListener.

    // ================= MAIN-LAH YANG KAMU BUTUHKAN =================

    public static void main(String[] args) {} // tak dipakai; javadoc guard

}
