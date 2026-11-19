package reyd;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import reyd.Class.DetailsPVP;
import reyd.Listener.PVPListener;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CombatMain extends PluginBase {

    /** Active combat sessions. Keys are normalized player names. */
    public final HashMap<String, DetailsPVP> cooldownH = new HashMap<>();
    private static CombatMain instance;
    public static String firstText;
    public static String secondText;

    private Config pvpConfig;
    private final Set<String> pvpDisabled = new HashSet<>();
    private final Set<String> pvpEnabledOverrides = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();
        loadPvPStates();

        getServer().getPluginManager().registerEvents(new PVPListener(), this);
        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (DetailsPVP session : new HashSet<>(cooldownH.values())) {
                if (session.decreaseCooldown() <= 0) {
                    cooldownH.remove(key(session.getPlayer()));
                }
            }
        }, 20);

        getLogger().info("CombatAWM enabled.");
    }

    private void loadSettings() {
        firstText = getConfig().getString("Messages.CombatBar.FirstText", "§4Combat time remaining: §c");
        secondText = getConfig().getString("Messages.CombatBar.SecondText", "\n§fLast opponent: §2");
    }

    private void loadPvPStates() {
        File file = new File(getDataFolder(), "pvp.yml");
        pvpConfig = new Config(file, Config.YAML);
        pvpDisabled.clear();
        pvpEnabledOverrides.clear();
        for (String name : pvpConfig.getStringList("disabled")) pvpDisabled.add(name.toLowerCase(Locale.ROOT));
        for (String name : pvpConfig.getStringList("enabled")) pvpEnabledOverrides.add(name.toLowerCase(Locale.ROOT));
    }

    public void savePvPStates() {
        if (pvpConfig == null) return;
        pvpConfig.set("disabled", new HashSet<>(pvpDisabled));
        pvpConfig.set("enabled", new HashSet<>(pvpEnabledOverrides));
        pvpConfig.save();
    }

    public boolean isPvPEnabled(Player player) {
        String name = key(player);
        if (pvpDisabled.contains(name)) return false;
        if (pvpEnabledOverrides.contains(name)) return true;
        return getConfig().getBoolean("PvP.DefaultEnabled", true);
    }

    public boolean togglePvP(Player player) {
        String name = key(player);
        if (isPvPEnabled(player)) {
            pvpEnabledOverrides.remove(name);
            pvpDisabled.add(name);
            removeCombatPair(player);
            savePvPStates();
            return false;
        }
        pvpDisabled.remove(name);
        pvpEnabledOverrides.add(name);
        savePvPStates();
        return true;
    }

    public boolean isWorldExcluded(Player player) {
        return getConfig().getStringList("ExcludedWorlds").contains(player.getLevel().getName());
    }

    public boolean isCombatTagged(Player player) {
        return cooldownH.containsKey(key(player));
    }

    public void startPVP(String name, Player oppositePlayer, Player thisPlayer) {
        if (thisPlayer == null || oppositePlayer == null || thisPlayer.equals(oppositePlayer)) return;
        if (!getConfig().getBoolean("CombatTag.Enabled", true)) return;
        if (isWorldExcluded(thisPlayer) || isWorldExcluded(oppositePlayer)) return;
        if (!isPvPEnabled(thisPlayer) || !isPvPEnabled(oppositePlayer)) return;

        String playerKey = key(thisPlayer);
        DetailsPVP active = cooldownH.get(playerKey);
        if (active != null) {
            active.reloadCooldown(oppositePlayer);
        } else {
            cooldownH.put(playerKey, new DetailsPVP(thisPlayer, oppositePlayer,
                    getConfig().getInt("CombatTag.DurationSeconds", 20)));
        }
    }

    public void removeCombat(Player player) {
        if (player == null) return;
        DetailsPVP session = cooldownH.remove(key(player));
        if (session != null) session.removePlayer();
    }

    public void removeCombatPair(Player player) {
        if (player == null) return;
        DetailsPVP session = cooldownH.remove(key(player));
        if (session != null) {
            Player opponent = session.getOpponent();
            session.removePlayer();
            if (opponent != null) {
                DetailsPVP other = cooldownH.remove(key(opponent));
                if (other != null && other.getOpponent() != null && other.getOpponent().getName().equalsIgnoreCase(player.getName())) other.removePlayer();
            }
        }
    }

    private String key(Player player) {
        return player.getName().toLowerCase(Locale.ROOT);
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Use /combat reload from console.");
                return true;
            }
            Player player = (Player) sender;
            sender.sendMessage(getConfig().getString("Messages.Status", "§ePvP: {pvp} | Combat: {combat}")
                    .replace("{pvp}", isPvPEnabled(player) ? "§aON" : "§cOFF")
                    .replace("{combat}", isCombatTagged(player) ? "§cTAGGED" : "§aSAFE"));
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used in-game.");
                return true;
            }
            Player player = (Player) sender;
            boolean enabled = togglePvP(player);
            sender.sendMessage(getConfig().getString(enabled ? "Messages.PvPEnabled" : "Messages.PvPDisabled",
                    enabled ? "§aPvP enabled." : "§cPvP disabled."));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("combatawm.admin")) {
                sender.sendMessage(getConfig().getString("Messages.NoPermission", "§cYou don't have permission."));
                return true;
            }
            reloadConfig();
            loadSettings();
            sender.sendMessage(getConfig().getString("Messages.Reloaded", "§aCombatAWM configuration reloaded."));
            return true;
        }

        sender.sendMessage("§eUsage: /combat <status|toggle|reload>");
        return true;
    }

    @Override
    public void onDisable() {
        for (DetailsPVP session : new HashSet<>(cooldownH.values())) session.removePlayer();
        cooldownH.clear();
        savePvPStates();
        instance = null;
    }

    public static CombatMain getInstance() {
        return instance;
    }
}
