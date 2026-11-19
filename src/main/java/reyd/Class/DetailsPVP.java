package reyd.Class;

import cn.nukkit.Player;
import cn.nukkit.utils.DummyBossBar;
import reyd.CombatMain;

public class DetailsPVP {

    private int time;
    private final Player thisPerson;
    private Player oppositePlayer;
    private DummyBossBar dummyBossBar;

    public DetailsPVP(Player thisPerson, Player oppositePlayer, int duration) {
        this.thisPerson = thisPerson;
        this.oppositePlayer = oppositePlayer;
        this.time = Math.max(1, duration);
        createBossBar();
    }

    private String getText() {
        return CombatMain.firstText + time + CombatMain.secondText + oppositePlayer.getName();
    }

    private void createBossBar() {
        dummyBossBar = new DummyBossBar.Builder(thisPerson)
                .text(getText())
                .build();
        thisPerson.createBossBar(dummyBossBar);
        updateBossBar();
    }

    public void reloadCooldown(Player newOppositePlayer) {
        this.time = Math.max(1, CombatMain.getInstance().getConfig().getInt("CombatTag.DurationSeconds", 20));
        this.oppositePlayer = newOppositePlayer;
        updateBossBar();
    }

    private void updateBossBar() {
        if (dummyBossBar == null) return;
        dummyBossBar.setText(getText());
        dummyBossBar.setLength(Math.max(0, time * 100f / Math.max(1,
                CombatMain.getInstance().getConfig().getInt("CombatTag.DurationSeconds", 20))));
    }

    public int decreaseCooldown() {
        if (time > 0) time--;
        if (time <= 0) {
            removePlayer();
            return 0;
        }
        updateBossBar();
        return time;
    }

    public Player getPlayer() {
        return thisPerson;
    }

    public Player getOpponent() {
        return oppositePlayer;
    }

    public void removePlayer() {
        if (dummyBossBar != null) {
            dummyBossBar.destroy();
            dummyBossBar = null;
        }
    }

    public void quitGame() {
        removePlayer();
        CombatMain.getInstance().removeCombatPair(thisPerson);
        if (CombatMain.getInstance().getConfig().getBoolean("CombatLog.KillOnLogout", true)) {
            thisPerson.kill();
        }
    }
}
