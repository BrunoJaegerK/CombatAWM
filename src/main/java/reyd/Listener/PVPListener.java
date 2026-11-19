package reyd.Listener;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import reyd.CombatMain;

import java.util.Locale;

public class PVPListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Entity source = event.getDamager();
        Player attacker = null;

        if (source instanceof Player) {
            attacker = (Player) source;
        } else if (source instanceof EntityProjectile) {
            Entity shooter = ((EntityProjectile) source).shootingEntity;
            if (shooter instanceof Player) attacker = (Player) shooter;
        }

        if (attacker == null || attacker.equals(victim)) return;

        CombatMain plugin = CombatMain.getInstance();
        if (plugin.isWorldExcluded(attacker) || plugin.isWorldExcluded(victim)) return;

        if (!plugin.isPvPEnabled(attacker) || !plugin.isPvPEnabled(victim)) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getConfig().getString("Messages.PvPDisabledForFight",
                    "§cPvP is disabled for one of the players."));
            return;
        }

        if (plugin.getConfig().getBoolean("CombatTag.TagOnlyWhenDamageIsApplied", true)
                && event.getFinalDamage() <= 0) return;

        plugin.startPVP(victim.getName(), attacker, victim);
        plugin.startPVP(attacker.getName(), victim, attacker);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        CombatMain plugin = CombatMain.getInstance();
        if (!plugin.isCombatTagged(event.getPlayer())
                || !plugin.getConfig().getBoolean("CombatTag.BlockCommands.Enabled", false)) return;

        String message = event.getMessage().trim();
        if (!message.startsWith("/")) return;
        String command = message.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);

        boolean whitelist = plugin.getConfig().getBoolean("CombatTag.BlockCommands.WhitelistMode", false);
        java.util.List<String> list = plugin.getConfig().getStringList("CombatTag.BlockCommands.List");
        boolean inList = false;
        for (String entry : list) {
            String normalized = entry.toLowerCase(Locale.ROOT).replace("/", "");
            if (command.equals(normalized) || command.startsWith(normalized + ":")) {
                inList = true;
                break;
            }
        }

        if ((whitelist && !inList) || (!whitelist && inList)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfig().getString("Messages.CommandBlocked",
                    "§cYou cannot use this command while in combat."));
        }
    }

    @EventHandler
    public void onQuitEvent(PlayerQuitEvent event) {
        CombatMain plugin = CombatMain.getInstance();
        if (plugin.isCombatTagged(event.getPlayer())) {
            plugin.cooldownH.get(CombatMain.key(event.getPlayer().getName())).quitGame();
        }
    }

    @EventHandler
    public void onDeathEvent(PlayerDeathEvent event) {
        CombatMain plugin = CombatMain.getInstance();
        plugin.removeCombatPair(event.getEntity());
    }
}
