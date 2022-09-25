package com.ollethunberg.semihardcore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.postgresql.Driver;

public class Plugin extends JavaPlugin implements Listener {
  private static final Logger LOGGER = Logger.getLogger("semi-hardcore");
  Connection connection;
  SQLHelper sqlHelper;
  FileConfiguration config;

  public void onEnable() {
    loadConfig();
    LOGGER.info("semi-hardcore enabled");
    LOGGER.info("jdbc:postgresql://" + config.getString("database.ip") + ":" + config.getInt("database.port") + "/"
        + config.getString("database.database") + "?stringtype=unspecified");
    try {
      DriverManager.registerDriver(new Driver());
      connection = DriverManager.getConnection(
          "jdbc:postgresql://" + config.getString("database.ip") + ":" + config.getInt("database.port") + "/"
              + config.getString("database.database") + "?stringtype=unspecified",
          config.getString("database.username"), config.getString("database.password"));
      sqlHelper = new SQLHelper(connection);
      getServer().getPluginManager().registerEvents(this, this);

    } catch (SQLException e) {
      LOGGER.severe("Could not connect to database");
      e.printStackTrace();
    }
  }

  public String getBanReasonMessage(String reason, Timestamp bannedUntil) {
    switch (reason) {
      case "death":
        return "§c§lYou are dead\n§cThis is a semi-hardcore server which means that you will be temporary banned from the server until you are revived.\n§aYou will be revived at "
            + bannedUntil.toString() + "\n§l§6Don't want to wait? Visit §a§l§n§ofedcraft.com/shop";
      case "cheat":
        return "You have been banned from the server for cheating.";
      case "spam":
        return "You have been banned from the server for spamming.";
      default:
        return "You have been banned from the server for other reasons.";

    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onPlayerJoin(PlayerJoinEvent event) {
    String isPlayerInDatabaseSQL = "SELECT EXISTS ( SELECT FROM player WHERE uid = ? );";
    try {
      ResultSet rs = sqlHelper.query(isPlayerInDatabaseSQL, event.getPlayer().getUniqueId().toString());
      if (rs.next() && rs.getBoolean("exists")) {
        LOGGER.info("Player " + event.getPlayer().getName() + " is in database: " + rs.getBoolean(1));
        String playerBannedUntil = "SELECT banned_date + (banned_minutes * interval '1 minute') as banned_until, player_id, ban_reason FROM player_bans WHERE player_id = ? order by banned_date DESC;";
        ResultSet rsPlayerBannedUntil = sqlHelper.query(playerBannedUntil, event.getPlayer().getUniqueId().toString());
        rsPlayerBannedUntil.next();
        if (rsPlayerBannedUntil.getTimestamp("banned_until").after(new java.util.Date())) {

          event.getPlayer()
              .kickPlayer(getBanReasonMessage(rsPlayerBannedUntil.getString("ban_reason"),
                  rsPlayerBannedUntil.getTimestamp("banned_until")));

        }
      } else {
        // NationsPlus plugin takes care of inserting new players to the database.

      }

    } catch (SQLException e) {
      LOGGER.info("Could not check if player is in database");
      e.printStackTrace();
    }
  }

  // event handler when player dies
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    // get the player who died
    String victimUid = event.getEntity().getPlayer().getUniqueId().toString();
    // get the player who killed the player
    String killerUid = "";
    if (event.getEntity().getKiller() != null) {
      killerUid = event.getEntity().getKiller().getUniqueId().toString();
    }

    // Add the stats to the victim and the killer
    String addDeathToVictimSQL = "UPDATE player SET deaths = deaths + 1 WHERE uid = ?;";
    String addDeathToKillerSQL = "UPDATE player SET kills = kills + 1 WHERE uid = ?;";
    try {
      sqlHelper.update(addDeathToVictimSQL, victimUid);
      String updateBalanceSQL = "UPDATE player SET balance = 0 WHERE uid = ?;";
      sqlHelper.update(updateBalanceSQL, victimUid);
      if (!killerUid.equals("")) { // Sometimes, the killer can be null, for example if they are killed by /kill
                                   // command
        sqlHelper.update(addDeathToKillerSQL, killerUid);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    // Ban the victim for 48 hours for death
    String banVictimSQL = "INSERT INTO player_bans (player_id, banned_date, banned_minutes, ban_reason) VALUES (?, ?, ?, ?);";
    try {
      sqlHelper.update(banVictimSQL, victimUid, new Timestamp(new java.util.Date().getTime()),
          config.getInt("deathban.duration") * 60, "death");
      // Kick player
      event.getEntity().getPlayer().kickPlayer(getBanReasonMessage("death",
          new Timestamp(new java.util.Date().getTime() + (config.getInt("deathban.duration") * 60 * 60 * 1000))));
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public void onDisable() {
    getLogger().info("Nations plus disabled");
    try {
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public void loadConfig() {
    getConfig().options().copyDefaults(true);
    saveConfig();
    config = getConfig();
  }
}
