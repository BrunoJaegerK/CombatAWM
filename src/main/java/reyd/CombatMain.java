package reyd;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import reyd.Class.DetailsPVP;
import reyd.Listener.PVPListener;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombatMain extends PluginBase {
    public final Map<String, DetailsPVP> cooldownH = new ConcurrentHashMap<>();
    private static CombatMain instance;
    public static String firstText, secondText;

    private Config pvpConfig, protectionConfig;
    private final Set<String> pvpDisabled = new HashSet<>(), pvpEnabledOverrides = new HashSet<>();
    private final Map<String, Long> newbieExpiry = new ConcurrentHashMap<>(), respawnExpiry = new ConcurrentHashMap<>(), toggleCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Deque<Long>>> killHistory = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();
        loadSettings();
        loadPvPStates();
        loadProtectionData();
        getServer().getPluginManager().registerEvents(new PVPListener(), this);
        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (DetailsPVP session : new ArrayList<>(cooldownH.values())) {
                if (session.decreaseCooldown() <= 0) cooldownH.remove(key(session.getPlayer()), session);
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : new ArrayList<>(newbieExpiry.entrySet())) {
                if (entry.getValue() <= now && newbieExpiry.remove(entry.getKey(), entry.getValue())) {
                    Player online = getServer().getPlayer(entry.getKey());
                    if (online != null) online.sendMessage(getConfig().getString("Messages.NewbieExpired", "§aYour newbie protection has expired."));
                }
            }
            respawnExpiry.entrySet().removeIf(e -> e.getValue() <= now);
        }, 20);
        getLogger().info("CombatAWM enabled.");
    }

    /**
     * Actualiza las opciones de versiones anteriores sin reemplazar el resto
     * de la configuración personalizada del servidor.
     */
    private void migrateConfig() {
        int version = getConfig().getInt("ConfigVersion", 1);
        if (version >= 2) return;

        // Valores corregidos solicitados: bloqueo de comandos y combate activo
        // desde el primer momento, sin que la protección de novatos lo silencie.
        getConfig().set("CombatTag.BlockCommands.Enabled", true);
        getConfig().set("NewbieProtection.Enabled", false);

        if (!getConfig().exists("AreaProtectionIntegration.Enabled")) {
            getConfig().set("AreaProtectionIntegration.Enabled", true);
        }
        if (!getConfig().exists("AreaProtectionIntegration.BlockCombatEntry")) {
            getConfig().set("AreaProtectionIntegration.BlockCombatEntry", true);
        }
        if (!getConfig().exists("AreaProtectionIntegration.CombatEntryBlockedAreas")) {
            getConfig().set("AreaProtectionIntegration.CombatEntryBlockedAreas",
                    Arrays.asList("lobby-pvp", "lobby-nopvp"));
        }
        getConfig().set("ConfigVersion", 2);
        saveConfig();
        getLogger().info("Configuración actualizada a la versión 2.");
    }

    private void loadSettings() {
        firstText = getConfig().getString("Messages.CombatBar.FirstText", "§4Combat time remaining: §c");
        secondText = getConfig().getString("Messages.CombatBar.SecondText", "\\n§fLast opponent: §2");
    }
    private void loadPvPStates() {
        pvpConfig = new Config(new File(getDataFolder(), "pvp.yml"), Config.YAML);
        pvpDisabled.clear(); pvpEnabledOverrides.clear();
        for (String n : pvpConfig.getStringList("disabled")) pvpDisabled.add(key(n));
        for (String n : pvpConfig.getStringList("enabled")) pvpEnabledOverrides.add(key(n));
    }
    private void loadProtectionData() {
        protectionConfig = new Config(new File(getDataFolder(), "protection.yml"), Config.YAML);
        newbieExpiry.clear();
        for (String n : protectionConfig.getSection("newbie").keySet()) newbieExpiry.put(key(n), protectionConfig.getLong("newbie." + n));
    }
    public void savePvPStates() {
        if (pvpConfig == null) return;
        pvpConfig.set("disabled", new ArrayList<>(pvpDisabled));
        pvpConfig.set("enabled", new ArrayList<>(pvpEnabledOverrides));
        pvpConfig.save();
    }
    public void saveProtectionData() {
        if (protectionConfig == null) return;
        for (String name : new ArrayList<>(protectionConfig.getSection("newbie").keySet())) protectionConfig.set("newbie." + name, null);
        for (Map.Entry<String, Long> e : newbieExpiry.entrySet()) protectionConfig.set("newbie." + e.getKey(), e.getValue());
        protectionConfig.save();
    }

    public boolean isPvPEnabled(Player p) {
        String n = key(p);
        if (pvpDisabled.contains(n)) return false;
        if (pvpEnabledOverrides.contains(n)) return true;
        return getConfig().getBoolean("PvP.DefaultEnabled", true);
    }
    public boolean togglePvP(Player p) {
        String n = key(p);
        if (isPvPEnabled(p)) { pvpEnabledOverrides.remove(n); pvpDisabled.add(n); removeCombatPair(p); savePvPStates(); return false; }
        pvpDisabled.remove(n); pvpEnabledOverrides.add(n); savePvPStates(); return true;
    }
    public boolean canTogglePvP(Player p) {
        if (p.hasPermission("combatawm.bypass.togglecooldown")) return true;
        long next = toggleCooldowns.getOrDefault(key(p), 0L);
        return System.currentTimeMillis() >= next;
    }
    public long getToggleCooldownLeft(Player p) { return Math.max(0, (toggleCooldowns.getOrDefault(key(p), 0L)-System.currentTimeMillis()+999)/1000); }
    public void setToggleCooldown(Player p) { toggleCooldowns.put(key(p), System.currentTimeMillis()+Math.max(0,getConfig().getInt("PvP.ToggleCooldownSeconds",15))*1000L); }
    /** Optional reflective integration with AreaProtection (no compile-time dependency). */
    private boolean areaProtectionWarningLogged = false;


    /**
     * Comprueba si el movimiento entra en una región de AreaProtection configurada
     * como zona segura frente al escape de combate. No depende de la bandera pvp:
     * estas regiones pueden permitir PvP y aun así impedir que un jugador escape allí.
     */
    public boolean isEnteringCombatRestrictedArea(cn.nukkit.level.Position from, cn.nukkit.level.Position to) {
        if (!getConfig().getBoolean("AreaProtectionIntegration.Enabled", true)
                || !getConfig().getBoolean("AreaProtectionIntegration.BlockCombatEntry", true)
                || to == null) return false;
        java.util.List<String> restricted = getConfig().getStringList("AreaProtectionIntegration.CombatEntryBlockedAreas");
        if (restricted == null || restricted.isEmpty()) return false;
        try {
            Object areaProtection = getServer().getPluginManager().getPlugin("AreaProtection");
            if (areaProtection == null || !Boolean.TRUE.equals(areaProtection.getClass().getMethod("isEnabled").invoke(areaProtection))) return false;
            java.lang.reflect.Method getAreaByName = areaProtection.getClass().getMethod("getAreaByName", String.class);
            for (String areaName : restricted) {
                Object area = getAreaByName.invoke(areaProtection, areaName);
                if (area == null) continue;
                boolean enters = Boolean.TRUE.equals(area.getClass()
                        .getMethod("isInArea", cn.nukkit.level.Position.class).invoke(area, to));
                if (!enters) continue;
                boolean wasInside = from != null && Boolean.TRUE.equals(area.getClass()
                        .getMethod("isInArea", cn.nukkit.level.Position.class).invoke(area, from));
                if (!wasInside) return true;
            }
            return false;
        } catch (Throwable error) {
            if (!areaProtectionWarningLogged) {
                areaProtectionWarningLogged = true;
                getLogger().warning("No se pudo consultar AreaProtection para bloquear la entrada a zonas durante combate: "
                        + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            // Si no se puede verificar la región, no bloqueamos movimientos ajenos.
            return false;
        }
    }

    public boolean isAreaProtectionPvPAllowed(Player attacker, Player victim) {
        if (!getConfig().getBoolean("AreaProtectionIntegration.Enabled", true)) return true;
        try {
            Object areaProtection = getServer().getPluginManager().getPlugin("AreaProtection");
            if (areaProtection == null || !Boolean.TRUE.equals(areaProtection.getClass().getMethod("isEnabled").invoke(areaProtection))) return true;

            java.lang.reflect.Method hasBypass = areaProtection.getClass().getMethod("hasBypassPerms", Player.class);
            boolean bypass = Boolean.TRUE.equals(hasBypass.invoke(areaProtection, attacker))
                    && getConfig().getBoolean("AreaProtectionIntegration.RespectBypass", true);
            if (bypass) return true;

            // AreaProtection puede tener regiones superpuestas. Su getAreaByPos()
            // devuelve solo la primera; aquí revisamos todas para no ignorar una
            // región interior que tenga pvp:false.
            java.lang.reflect.Field areasField = areaProtection.getClass().getField("areas");
            Object rawAreas = areasField.get(null);
            if (!(rawAreas instanceof Iterable)) return true;
            for (Object area : (Iterable<?>) rawAreas) {
                boolean containsAttacker = Boolean.TRUE.equals(area.getClass()
                        .getMethod("isInArea", cn.nukkit.level.Position.class).invoke(area, attacker.getPosition()));
                boolean containsVictim = Boolean.TRUE.equals(area.getClass()
                        .getMethod("isInArea", cn.nukkit.level.Position.class).invoke(area, victim.getPosition()));
                if (!containsAttacker && !containsVictim) continue;
                // No asumir que el mapa usa exactamente la clave "pvp":
                // algunas compilaciones de AreaProtection conservan el nombre
                // de la bandera dentro del objeto AreaFlag.
                Object rawFlags = area.getClass().getMethod("getFlags").invoke(area);
                if (!(rawFlags instanceof java.util.Map)) continue;
                boolean hasPvPFlag = false;
                boolean pvpAllowed = true;
                for (Object flag : ((java.util.Map<?, ?>) rawFlags).values()) {
                    if (flag == null) continue;
                    java.lang.reflect.Field nameField = flag.getClass().getField("name");
                    Object flagName = nameField.get(flag);
                    if (flagName == null || !"pvp".equalsIgnoreCase(String.valueOf(flagName))) continue;
                    hasPvPFlag = true;
                    java.lang.reflect.Field allowedField = flag.getClass().getField("allowed");
                    pvpAllowed = Boolean.TRUE.equals(allowedField.get(flag));
                    break;
                }
                // Compatibilidad con implementaciones que no exponen el nombre
                // en los valores pero sí aceptan isAllowed("pvp").
                if (!hasPvPFlag) {
                    try {
                        java.util.Map<?, ?> flags = (java.util.Map<?, ?>) rawFlags;
                        for (Object mapKey : flags.keySet()) {
                            if (mapKey != null && "pvp".equalsIgnoreCase(String.valueOf(mapKey))) {
                                hasPvPFlag = true;
                                pvpAllowed = Boolean.TRUE.equals(area.getClass()
                                        .getMethod("isAllowed", String.class).invoke(area, "pvp"));
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                        // Una región sin bandera PvP explícita no restringe el PvP.
                    }
                }
                if (hasPvPFlag && !pvpAllowed) return false;
            }
            return true;
        } catch (Throwable error) {
            if (!areaProtectionWarningLogged) {
                areaProtectionWarningLogged = true;
                getLogger().warning("AreaProtection integration could not query its API; PvP will fail open. " + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            return true;
        }
    }

    public boolean isWorldExcluded(Player p) { return getConfig().getStringList("ExcludedWorlds").contains(p.getLevel().getName()); }
    public boolean isCombatTagged(Player p) { return cooldownH.containsKey(key(p)); }
    public boolean isNewbieProtected(Player p) { return getConfig().getBoolean("NewbieProtection.Enabled", false) && newbieExpiry.getOrDefault(key(p),0L) > System.currentTimeMillis(); }
    public long getNewbieSeconds(Player p) { return Math.max(0,(newbieExpiry.getOrDefault(key(p),0L)-System.currentTimeMillis()+999)/1000); }
    public void beginNewbieProtection(Player p) {
        if (!getConfig().getBoolean("NewbieProtection.Enabled", true) || isWorldExcluded(p)) return;
        String n=key(p);
        long current = newbieExpiry.getOrDefault(n, 0L);
        if (current <= System.currentTimeMillis()) {
            long seconds=getConfig().getInt("NewbieProtection.DurationSeconds",600);
            if (seconds>0) { current=System.currentTimeMillis()+seconds*1000L; newbieExpiry.put(n,current); saveProtectionData(); }
        }
        if (current > System.currentTimeMillis()) p.sendMessage(getConfig().getString("Messages.NewbieStarted", "§aYou have newbie protection for §e{time}s§a.").replace("{time}", String.valueOf(Math.max(0,(current-System.currentTimeMillis()+999)/1000))));
    }
    public boolean disableNewbieProtection(Player p) {
        if (!getConfig().getBoolean("NewbieProtection.AllowPlayerDisable",true)) return false;
        return removeNewbieProtection(p);
    }
    public boolean removeNewbieProtection(Player p) {
        if (newbieExpiry.remove(key(p)) != null) { saveProtectionData(); return true; }
        return false;
    }
    public void grantRespawnProtection(Player p) {
        int seconds=getConfig().getInt("RespawnProtection.DurationSeconds",3);
        if (seconds>0) respawnExpiry.put(key(p),System.currentTimeMillis()+seconds*1000L);
    }
    public boolean isRespawnProtected(Player p) { return respawnExpiry.getOrDefault(key(p),0L)>System.currentTimeMillis(); }
    public boolean isSafeWorld(Player p) { return getConfig().getStringList("AntiBorderHopping.SafeWorlds").contains(p.getLevel().getName()) || isWorldExcluded(p); }

    public void startPVP(String name, Player opponent, Player player) {
        if (player==null || opponent==null || player.equals(opponent) || !getConfig().getBoolean("CombatTag.Enabled",true)) return;
        if (isWorldExcluded(player)||isWorldExcluded(opponent)||!isPvPEnabled(player)||!isPvPEnabled(opponent)||isNewbieProtected(player)||isNewbieProtected(opponent)) return;
        String k=key(player); DetailsPVP active=cooldownH.get(k);
        if(active!=null) active.reloadCooldown(opponent);
        else cooldownH.put(k,new DetailsPVP(player,opponent,getConfig().getInt("CombatTag.DurationSeconds",20)));
    }
    public void removeCombat(Player p) { if(p==null)return; DetailsPVP d=cooldownH.remove(key(p)); if(d!=null)d.removePlayer(); }
    public void removeCombatPair(Player p) {
        if(p==null)return; String k=key(p); DetailsPVP own=cooldownH.remove(k); Player opponent=own==null?null:own.getOpponent(); if(own!=null)own.removePlayer();
        if(opponent!=null){String ok=key(opponent);DetailsPVP linked=cooldownH.get(ok);if(linked!=null&&linked.getOpponent()!=null&&key(linked.getOpponent().getName()).equals(k)){cooldownH.remove(ok);linked.removePlayer();}}
        for(Map.Entry<String,DetailsPVP> e:new ArrayList<>(cooldownH.entrySet())) if(e.getValue().getOpponent()!=null&&key(e.getValue().getOpponent().getName()).equals(k)){cooldownH.remove(e.getKey());e.getValue().removePlayer();}
    }
    public boolean isKillLimitReached(Player killer, Player victim) {
        if (!getConfig().getBoolean("AntiKillAbuse.Enabled", true) || killer.hasPermission("combatawm.bypass.killabuse")) return false;
        Deque<Long> times = killHistory.getOrDefault(key(killer), Collections.emptyMap()).get(key(victim));
        if (times == null) return false;
        long now=System.currentTimeMillis(), window=getConfig().getInt("AntiKillAbuse.WindowSeconds",3600)*1000L;
        synchronized(times) { while(!times.isEmpty() && now-times.peekFirst()>window) times.pollFirst(); return times.size() >= getConfig().getInt("AntiKillAbuse.MaxKills",3); }
    }

    public boolean recordKill(Player killer, Player victim) {
        if(!getConfig().getBoolean("AntiKillAbuse.Enabled",true) || killer.hasPermission("combatawm.bypass.killabuse")) return false;
        String k=key(killer), v=key(victim); long now=System.currentTimeMillis(), window=getConfig().getInt("AntiKillAbuse.WindowSeconds",3600)*1000L;
        Map<String,Deque<Long>> victims=killHistory.computeIfAbsent(k,ignored->new ConcurrentHashMap<>());
        Deque<Long> times=victims.computeIfAbsent(v,ignored->new ArrayDeque<>());
        synchronized(times){while(!times.isEmpty()&&now-times.peekFirst()>window)times.pollFirst();times.addLast(now);}
        int count; synchronized(times){count=times.size();}
        int max=getConfig().getInt("AntiKillAbuse.MaxKills",3);
        if(count>=max){killer.sendMessage(getConfig().getString("Messages.KillAbuse","§cRepeatedly killing the same player is restricted."));return true;}
        if(count==max-1&&getConfig().getBoolean("AntiKillAbuse.WarnBeforeLimit",true))killer.sendMessage(getConfig().getString("Messages.KillAbuseWarning","§eWarning: one more kill on this player will reach the limit."));
        return false;
    }
    public String key(Player p){return key(p.getName());}
    public static String key(String name){return name.toLowerCase(Locale.ROOT);}

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (label.equalsIgnoreCase("pvp") || label.equalsIgnoreCase("pvptoggle")) action = "toggle";
        else if (label.equalsIgnoreCase("pvpstatus") || label.equalsIgnoreCase("tag")) action = "status";
        else if (label.equalsIgnoreCase("pvplist")) action = "list";
        else if (label.equalsIgnoreCase("newbie")) action = "newbie";
        if(action.equalsIgnoreCase("status")){
            if(!(sender instanceof Player)){sender.sendMessage("Use /combat reload from console.");return true;}
            Player p=(Player)sender;
            sender.sendMessage(getConfig().getString("Messages.Status","§ePvP: {pvp} §7| Combat: {combat}").replace("{pvp}",isPvPEnabled(p)?"§aON":"§cOFF").replace("{combat}",isCombatTagged(p)?"§cTAGGED":"§aSAFE"));
            if(isNewbieProtected(p))sender.sendMessage(getConfig().getString("Messages.NewbieStatus","§aNewbie protection: §e{time}s").replace("{time}",String.valueOf(getNewbieSeconds(p))));
            return true;
        }
        if(action.equalsIgnoreCase("toggle")){
            if(!(sender instanceof Player)){sender.sendMessage("In-game only.");return true;}
            Player p=(Player)sender;
            if(isCombatTagged(p)){p.sendMessage(getConfig().getString("Messages.ToggleInCombat","§cYou cannot toggle PvP while tagged in combat."));return true;}
            if(!canTogglePvP(p)){p.sendMessage(getConfig().getString("Messages.ToggleCooldown","§cWait {time}s before toggling PvP again.").replace("{time}",String.valueOf(getToggleCooldownLeft(p))));return true;}
            boolean enabled=togglePvP(p);setToggleCooldown(p);
            p.sendMessage(getConfig().getString(enabled?"Messages.PvPEnabled":"Messages.PvPDisabled",enabled?"§aPvP enabled.":"§cPvP disabled."));
            return true;
        }
        if(action.equalsIgnoreCase("newbie")){
            if(!(sender instanceof Player)){sender.sendMessage("In-game only.");return true;}
            Player p=(Player)sender;
            String newbieSub = label.equalsIgnoreCase("newbie") ? (args.length>0 ? args[0] : "") : (args.length>1 ? args[1] : "");
            if(newbieSub.equalsIgnoreCase("disable")){boolean changed=disableNewbieProtection(p);p.sendMessage(getConfig().getString(changed?"Messages.NewbieDisabled":"Messages.NewbieCannotDisable",changed?"§aProtection disabled.":"§cYou cannot disable protection or it is not active."));}
            else p.sendMessage(getConfig().getString("Messages.NewbieStatus","§aNewbie protection: §e{time}s").replace("{time}",String.valueOf(getNewbieSeconds(p))));
            return true;
        }
        if(action.equalsIgnoreCase("list")){
            StringBuilder b=new StringBuilder("§aPvP enabled: ");
            for(Player online:getServer().getOnlinePlayers().values())if(isPvPEnabled(online))b.append(online.getName()).append(", ");
            sender.sendMessage(b.toString());return true;
        }
        if(action.equalsIgnoreCase("untag")){
            if(!sender.hasPermission("combatawm.admin")){sender.sendMessage(getConfig().getString("Messages.NoPermission","§cNo permission."));return true;}
            if(args.length<2){sender.sendMessage("§e/combat untag <player>");return true;}
            Player target=getServer().getPlayer(args[1]);if(target==null){sender.sendMessage("§cPlayer not online.");return true;}removeCombatPair(target);sender.sendMessage("§aCombat tag cleared.");return true;
        }
        if(action.equalsIgnoreCase("reload")){
            if(!sender.hasPermission("combatawm.admin")){sender.sendMessage(getConfig().getString("Messages.NoPermission","§cNo permission."));return true;}
            reloadConfig();loadSettings();loadPvPStates();loadProtectionData();sender.sendMessage(getConfig().getString("Messages.Reloaded","§aReloaded."));return true;
        }
        sender.sendMessage("§e/combat <status|toggle|newbie|list|untag|reload>");return true;
    }
    @Override public void onDisable(){for(DetailsPVP d:new ArrayList<>(cooldownH.values()))d.removePlayer();cooldownH.clear();savePvPStates();saveProtectionData();instance=null;}
    public static CombatMain getInstance(){return instance;}
}
