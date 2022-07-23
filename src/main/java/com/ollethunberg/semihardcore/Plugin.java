package com.ollethunberg.semihardcore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.postgresql.Driver;

public class Plugin extends JavaPlugin implements Listener {
  private static final Logger LOGGER = Logger.getLogger("semi-hardcore");
  Connection connection;
  SQLHelper sqlHelper;

  public void onEnable() {
    loadConfig();
    LOGGER.info("semi-hardcore enabled");
    try {
      DriverManager.registerDriver(new Driver());
      connection = DriverManager.getConnection(
          "jdbc:postgresql://localhost:5432/nationsplus?stringtype=unspecified", "postgres", "");
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
            + bannedUntil.toString() + "\n§l§6Don't want to wait? Visit §a§l§n§oshop.peepo.gg/revive";
      case "cheat":
        return "You have been banned from the server for cheating.";
      case "spam":
        return "You have been banned from the server for spamming.";
      default:
        return "You have been banned from the server for other reasons.";

    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
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
  }
}
