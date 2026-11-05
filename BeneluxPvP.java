package nl.beneluxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/** Benelux PvP HQ: kits, arena's, wachtrij, duels, statistieken, ranglijst en cosmetics-beloningen in één plugin. */
public class BeneluxPvP extends JavaPlugin {
    static final String ADMIN = "beneluxpvp.admin", USE = "beneluxpvp.use", BYPASS = "beneluxpvp.bypass";
    static final String PREFIX = "<dark_red><bold>⚔ <gold><bold>BENELUX PVP <dark_gray>» <gray>";
    static final String TITLE = "<dark_red><bold>⚔ <gold><bold>Benelux PvP HQ";

    /** Wie typt er nu iets in de chat voor een menu? */
    record Prompt(BiConsumer<Player, String> done, Runnable back) {
    }

    private final MiniMessage mm = MiniMessage.miniMessage();
    private Stats stats;
    private Store store;
    private Settings settings;
    private Bridge bridge;
    private Items items;
    private Kits kits;
    private Arenas arenas;
    private States states;
    private Matches matches;
    private Queue queue;
    private Requests requests;
    private Rewards rewards;
    private Rules rules;
    private PlayerGuis playerGuis;
    private StaffGuis staffGuis;
    private Events events;
    private BukkitTask timer, secondTimer;
    private int ticks;
    /** De klok (voor tests aanpasbaar). */
    LongSupplier clock = System::currentTimeMillis;
    Random random = new Random();
    final Map<UUID, Prompt> prompts = new HashMap<>();

    long now() {
        return clock.getAsLong();
    }

    Random random() {
        return random;
    }

    Stats stats() { return stats; }
    Store store() { return store; }
    Settings settings() { return settings; }
    Bridge bridge() { return bridge; }
    Items items() { return items; }
    Kits kits() { return kits; }
    Arenas arenas() { return arenas; }
    States states() { return states; }
    Matches matches() { return matches; }
    Queue queue() { return queue; }
    Requests requests() { return requests; }
    Rewards rewards() { return rewards; }
    Rules rules() { return rules; }
    PlayerGuis playerGuis() { return playerGuis; }
    StaffGuis staffGuis() { return staffGuis; }
    Events events() { return events; }

    // ------------------------------------------------------------ aan en uit

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File dir = getDataFolder();
        dir.mkdirs();
        stats = new Stats(new File(dir, "spelers.tsv"), getLogger());
        store = new Store(new File(dir, "staff.yml"), getLogger());
        stats.load();
        store.load();
        settings = new Settings(this);
        bridge = new Bridge(this);
        items = new Items(this);
        kits = new Kits(this, new File(dir, "kits.yml"));
        arenas = new Arenas(this, new File(dir, "arenas.yml"));
        kits.load();
        arenas.load();
        states = new States(this, new File(dir, "states"));
        rewards = new Rewards(this);
        rules = new Rules(this, this::now);
        matches = new Matches(this);
        queue = new Queue(this);
        requests = new Requests(this);
        playerGuis = new PlayerGuis(this);
        staffGuis = new StaffGuis(this);
        events = new Events(this);
        getServer().getPluginManager().registerEvents(events, this);
        Cmds cmds = new Cmds(this);
        for (String c : List.of("pvp", "duel", "pvpstats", "pvptop", "kills", "pvpadmin")) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }
        timer = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        // Spelers die er al zijn (na /reload) tellen niet als "net gejoind"; en wie nog iets terug moet krijgen, krijgt dat nu.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (states.has(p.getUniqueId()) && !matches.inMatch(p.getUniqueId()) && !matches.isWatching(p.getUniqueId())) states.restoreOnJoin(p);
            }
            arenas.recover();
            rewards.evaluateAll();
        }, 40L);
        getLogger().info("Benelux PvP aan. Kits: " + kits.all().size() + ", arena's: " + arenas.all().size() + ", spelers met data: " + stats.size() + ".");
    }

    @Override
    public void onDisable() {
        if (timer != null) timer.cancel();
        if (matches != null) matches.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
            sweep(p);
        }
        prompts.clear();
        if (stats != null) stats.save();
        if (store != null) store.save();
        if (states != null) states.dropMemory();
    }

    private void tick() {
        arenas.tick();
        if (++ticks % 20 == 0) {
            matches.tick();
            queue.tick();
            requests.tick();
            if (ticks % 1200 == 0 && stats.dirty) stats.save();
        }
    }

    // ------------------------------------------------------------ tekst

    Component parse(String raw) {
        try {
            return mm.deserialize(raw);
        } catch (RuntimeException ex) {
            return Component.text(raw);
        }
    }

    void say(CommandSender to, String mini) {
        to.sendMessage(parse(PREFIX + mini));
    }

    String msg(String key, String def) {
        String s = getConfig().getString("berichten." + key, def);
        return s == null ? def : s;
    }

    /** Vraagt de speler iets in de chat te typen. Het volgende chatbericht gaat NIET naar de rest. */
    void ask(Player p, String question, BiConsumer<Player, String> done, Runnable back) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), new Prompt(done, back));
        say(p, question + " <dark_gray>(typ <white>annuleer<dark_gray> om te stoppen)");
    }

    // ------------------------------------------------------------ menu's

    void refreshOpenMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Gui g) {
                try {
                    g.refresh();
                } catch (RuntimeException e) {
                    getLogger().warning("Menu verversen mislukt: " + e);
                    p.closeInventory();
                }
            }
        }
    }

    /** Haalt alle menu-items uit de inventaris en van de cursor van deze speler. */
    void sweep(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (items.isMenuItem(inv.getItem(i))) inv.setItem(i, null);
        }
        if (items.isMenuItem(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    void sweepSoon(Player p) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player pp = Bukkit.getPlayer(id);
            if (pp != null && pp.isOnline()) {
                sweep(pp);
                pp.updateInventory();
            }
        }, 1L);
    }

    /** Is deze speler bezig (wedstrijd of kijken)? Dan werken de meeste menu's en commando's niet. */
    boolean busy(Player p) {
        return matches.inMatch(p.getUniqueId()) || matches.isWatching(p.getUniqueId());
    }
}
