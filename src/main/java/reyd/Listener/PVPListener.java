package reyd.Listener;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import reyd.CombatMain;

import java.util.Locale;

public class PVPListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        CombatMain.getInstance().beginNewbieProtection(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if(event.isCancelled() || !(event.getEntity() instanceof Player)) return;
        Player victim=(Player)event.getEntity();
        Player attacker=getAttacker(event.getDamager());
        CombatMain plugin=CombatMain.getInstance();

        if(plugin.isRespawnProtected(victim)) {
            event.setCancelled(true);
            if(attacker!=null)attacker.sendMessage(plugin.getConfig().getString("Messages.RespawnProtected","§cThat player is protected after respawning."));
            return;
        }
        if(plugin.isNewbieProtected(victim)) {
            event.setCancelled(true);
            if(attacker!=null)attacker.sendMessage(plugin.getConfig().getString("Messages.NewbieProtectedTarget","§cThat player has newbie protection."));
            return;
        }
        if(attacker==null) return;
        if(plugin.isRespawnProtected(attacker)||plugin.isNewbieProtected(attacker)) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getConfig().getString("Messages.ProtectedCannotAttack","§cYou cannot attack while protected."));
            return;
        }
        if(attacker.equals(victim))return;
        if(plugin.isWorldExcluded(attacker)||plugin.isWorldExcluded(victim))return;

        if(!plugin.isPvPEnabled(attacker)||!plugin.isPvPEnabled(victim)) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getConfig().getString("Messages.PvPDisabledForFight","§cPvP is disabled for one of the players."));
            return;
        }
        if(plugin.getConfig().getBoolean("AntiKillAbuse.Enabled",true)
                && event.getFinalDamage() >= victim.getHealth()
                && plugin.isKillLimitReached(attacker,victim)) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getConfig().getString("Messages.KillAbuse","§cRepeatedly killing the same player is restricted."));
            return;
        }
        if(plugin.getConfig().getBoolean("CombatTag.TagOnlyWhenDamageIsApplied",true)&&event.getFinalDamage()<=0)return;
        plugin.startPVP(victim.getName(),attacker,victim);
        plugin.startPVP(attacker.getName(),victim,attacker);
    }

    private Player getAttacker(Entity source) {
        if(source instanceof Player)return (Player)source;
        if(source instanceof EntityProjectile) {
            Entity shooter=((EntityProjectile)source).shootingEntity;
            if(shooter instanceof Player)return (Player)shooter;
        }
        return null;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        CombatMain plugin=CombatMain.getInstance();
        Player player=event.getPlayer();
        String msg=event.getMessage().trim();
        if(plugin.isCombatTagged(player)&&plugin.getConfig().getBoolean("CombatTag.BlockCommands.Enabled",false)) {
            String cmd=msg.startsWith("/")?msg.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT):msg;
            boolean whitelist=plugin.getConfig().getBoolean("CombatTag.BlockCommands.WhitelistMode",false);
            boolean matched=false;
            for(String entry:plugin.getConfig().getStringList("CombatTag.BlockCommands.List")) {
                String normalized=entry.toLowerCase(Locale.ROOT).replace("/","");
                if(cmd.equals(normalized)||cmd.startsWith(normalized+":")||cmd.startsWith(normalized+" ")) {matched=true;break;}
            }
            if((whitelist&&!matched)||(!whitelist&&matched)) {
                if(player.hasPermission("combatawm.bypass.commands"))return;
                event.setCancelled(true);
                player.sendMessage(plugin.getConfig().getString("Messages.CommandBlocked","§cYou cannot use this command while in combat."));
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        CombatMain plugin=CombatMain.getInstance();
        Player p=event.getPlayer();
        if(!plugin.isCombatTagged(p)||!plugin.getConfig().getBoolean("AntiBorderHopping.Enabled",false)||p.hasPermission("combatawm.bypass.borderhopping"))return;
        if(event.getTo()!=null&&plugin.getConfig().getStringList("AntiBorderHopping.SafeWorlds").contains(event.getTo().getLevel().getName())) {
            event.setCancelled(true);
            p.sendMessage(plugin.getConfig().getString("Messages.BorderHoppingBlocked","§cYou cannot escape combat by entering a safe world."));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p=event.getPlayer();CombatMain plugin=CombatMain.getInstance();
        if(plugin.isCombatTagged(p)&&plugin.getConfig().getBoolean("CombatTag.BlockPlace.Enabled",false)&&!p.hasPermission("combatawm.bypass.actions")) {
            event.setCancelled(true);p.sendMessage(plugin.getConfig().getString("Messages.ActionBlocked","§cYou cannot do that while in combat."));
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p=event.getPlayer();CombatMain plugin=CombatMain.getInstance();
        if(plugin.isCombatTagged(p)&&plugin.getConfig().getBoolean("CombatTag.BlockBreak.Enabled",false)&&!p.hasPermission("combatawm.bypass.actions")) {
            event.setCancelled(true);p.sendMessage(plugin.getConfig().getString("Messages.ActionBlocked","§cYou cannot do that while in combat."));
        }
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p=event.getPlayer();CombatMain plugin=CombatMain.getInstance();
        if(plugin.isCombatTagged(p)&&plugin.getConfig().getBoolean("CombatTag.BlockInteract.Enabled",false)&&!p.hasPermission("combatawm.bypass.actions")) {
            event.setCancelled(true);p.sendMessage(plugin.getConfig().getString("Messages.ActionBlocked","§cYou cannot do that while in combat."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CombatMain plugin=CombatMain.getInstance();
        if(plugin.isCombatTagged(event.getPlayer()))plugin.cooldownH.get(CombatMain.key(event.getPlayer().getName())).quitGame();
    }
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        CombatMain plugin=CombatMain.getInstance();
        Player victim=event.getEntity();
        EntityDamageEvent last=victim.getLastDamageCause();
        Player killer=last instanceof EntityDamageByEntityEvent?getAttacker(((EntityDamageByEntityEvent)last).getDamager()):null;
        if(killer!=null&&killer!=victim)plugin.recordKill(killer,victim);
        plugin.removeCombatPair(victim);
        plugin.grantRespawnProtection(victim);
        if(plugin.getConfig().getBoolean("NewbieProtection.RemoveOnDeath",true))plugin.disableNewbieProtection(victim);
    }
}
