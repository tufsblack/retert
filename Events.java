package nl.beneluxpvp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Alle gebeurtenissen: menu's beschermen, wedstrijdregels, aanmelden en afmelden. */
final class Events implements Listener {
    private final BeneluxPvP pl;

    Events(BeneluxPvP pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------ aanmelden en afmelden

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        pl.rules().joined(id);
        pl.stats().getOrCreate(id, p.getName());
        pl.sweep(p);
        if (!pl.matches().inMatch(id) && pl.states().has(id)) pl.states().restoreOnJoin(p);
        Bukkit.getScheduler().runTaskLater(pl, () -> pl.rewards().evaluate(p), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        pl.matches().quit(p);
        pl.queue().remove(id);
        pl.requests().clearFor(id);
        pl.rules().forget(id);
        pl.prompts.remove(id);
        pl.sweep(p);
        if (pl.stats().dirty) pl.stats().save();
    }

    // ------------------------------------------------------------ menu's: klikken en bescherming tegen pakken en dupen

    private static boolean menuOpen(InventoryClickEvent e) {
        return e.getView().getTopInventory().getHolder() instanceof Menu;
    }

    /** Alles in een menu is geblokkeerd: elke klik, ook in je eigen inventaris, shift-klik, cijfertoetsen, F en Q. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (menuOpen(e)) {
            e.setCancelled(true);
            Inventory top = e.getView().getTopInventory();
            Inventory clicked = e.getClickedInventory();
            if (clicked != null && clicked.equals(top) && top.getHolder() instanceof Menu m) {
                try {
                    m.click(e, p);
                } catch (RuntimeException ex) {
                    pl.getLogger().warning("Fout in menu: " + ex);
                    p.closeInventory();
                }
            }
            return;
        }
        if (pl.items().isMenuItem(e.getCursor()) || pl.items().isMenuItem(e.getCurrentItem())) {
            e.setCancelled(true);
            pl.sweepSoon(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || pl.items().isMenuItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (menuOpen(e) || pl.items().isMenuItem(e.getCursor()) || pl.items().isMenuItem(e.getCurrentItem())) {
            e.setCancelled(true);
            pl.sweepSoon(p);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Menu) {
            pl.sweep(p);
            pl.sweepSoon(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (pl.items().isMenuItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getItemDrop().remove();
            pl.sweepSoon(e.getPlayer());
            return;
        }
        if (pl.matches().inMatch(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (pl.items().isMenuItem(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
            return;
        }
        if (e.getEntity() instanceof Player p && pl.matches().inMatch(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopper(InventoryMoveItemEvent e) {
        if (pl.items().isMenuItem(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (pl.items().isMenuItem(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    /** Deelnemers openen geen kisten, werkbanken, enderkisten enz. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Menu) return;
        if (pl.matches().inMatch(p.getUniqueId()) && !p.hasPermission(BeneluxPvP.BYPASS)) e.setCancelled(true);
    }

    // ------------------------------------------------------------ chat (voor de menu's)

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        BeneluxPvP.Prompt pr = pl.prompts.remove(p.getUniqueId());
        if (pr == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (!p.isOnline()) return;
            if (text.equalsIgnoreCase("annuleer") || text.equalsIgnoreCase("cancel")) {
                pl.say(p, "<gray>Geannuleerd.");
                if (pr.back() != null) pr.back().run();
                return;
            }
            try {
                pr.done().accept(p, text);
            } catch (RuntimeException ex) {
                pl.getLogger().warning("Fout bij verwerken van chat-invoer: " + ex);
                pl.say(p, "<red>Er ging iets mis. Probeer het opnieuw.");
            }
        });
    }

    // ------------------------------------------------------------ wedstrijdregels

    private static boolean hasTotem(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand(), off = inv.getItemInOffHand();
        return (main != null && main.getType() == Material.TOTEM_OF_UNDYING) || (off != null && off.getType() == Material.TOTEM_OF_UNDYING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        Player victim = ent instanceof Player pv ? pv : null;
        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent ed) {
            Entity d = ed.getDamager();
            if (d instanceof Player pd) attacker = pd;
            else if (d instanceof Projectile pr && pr.getShooter() instanceof Player sp) attacker = sp;
        }
        Match mv = victim == null ? null : pl.matches().of(victim.getUniqueId());
        Match ma = attacker == null ? null : pl.matches().of(attacker.getUniqueId());
        if (attacker != null && victim != null && attacker.getUniqueId().equals(victim.getUniqueId())) attacker = null; // eigen schade (bijv. parel)
        if (attacker != null) {
            if (ma != null && victim != null && mv != ma) {
                e.setCancelled(true);
                return;
            }
            if (mv != null && ma != mv) {
                e.setCancelled(true);
                return;
            }
        }
        if (mv == null) return;
        if (mv.declared || mv.state != Match.State.FIGHT) {
            e.setCancelled(true);
            return;
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0 && !hasTotem(victim)) {
            if (pl.matches().lethal(victim)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Match m = pl.matches().of(p.getUniqueId());
        if (m == null) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        pl.matches().died(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Location to = pl.matches().respawn(e.getPlayer());
        if (to != null) e.setRespawnLocation(to);
    }

    private static Location frozen(Location f, Location t) {
        return new Location(f.getWorld(), f.getX(), f.getY(), f.getZ(), t.getYaw(), t.getPitch());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Match m = pl.matches().of(id);
        if (m == null) m = pl.matches().watchedBy(id);
        if (m == null) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || f.getWorld() == null) return;
        if (m.has(id) && m.state == Match.State.COUNTDOWN && !m.declared) {
            if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) e.setTo(frozen(f, t));
            return;
        }
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ()) return;
        Region r = m.arena.region();
        if (r == null || t.getWorld() == null) return;
        String w = t.getWorld().getName();
        // Alleen zijwaarts is een muur; omhoog (windcharge, mace) en omlaag blijven vrij.
        if (!r.containsXZ(w, t.getX(), t.getZ()) && r.containsXZ(w, f.getX(), f.getZ())) e.setTo(frozen(f, t));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && c != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Match m = pl.matches().of(e.getPlayer().getUniqueId());
        if (m == null) return;
        Region r = m.arena.region();
        Location to = e.getTo();
        if (r == null || to == null || to.getWorld() == null) return;
        if (!r.containsXZ(to.getWorld().getName(), to.getX(), to.getZ())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!pl.busy(p) || p.hasPermission(BeneluxPvP.BYPASS)) return;
        String msg = e.getMessage();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String first = msg.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = first.indexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        List<String> ok = pl.getConfig().getStringList("commando.toegestaan");
        for (String s : ok) if (s.equalsIgnoreCase(first)) return;
        e.setCancelled(true);
        pl.say(p, "<red>Dat kan nu niet. <gray>Stoppen: <white>/pvp leave");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && pl.matches().inMatch(p.getUniqueId()) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    // ------------------------------------------------------------ bouwen in arena's

    /** Mag deze speler hier blokken veranderen? Buiten arena's bemoeien we ons er niet mee. */
    boolean canBuild(Player p, Location l) {
        ArenaDef a = pl.arenas().arenaAt(l);
        if (a == null || p.hasPermission(BeneluxPvP.BYPASS)) return true;
        Match m = pl.matches().of(p.getUniqueId());
        return m != null && m.arena == a && m.state == Match.State.FIGHT && !m.declared && a.build;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        ArenaDef a = pl.arenas().arenaAt(e.getLocation());
        if (a != null && !a.build) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        ArenaDef a = pl.arenas().arenaAt(e.getBlock().getLocation());
        if (a != null && !a.build) e.blockList().clear();
    }
}
