package core;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import models.ChannelDels;
import models.GuildStructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBHandler {
    private String path;

    private Logger dbLogger = LoggerFactory.getLogger(DBHandler.class);
    private Connection conn;

    private Connection connect() {
        Connection conn = null;
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);    // enable foreign key constraints
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + path);
            ds.setConfig(config);
            conn = ds.getConnection();
            dbLogger.info("New connection to database established");
        }
        catch (SQLException e) {
            dbLogger.error("Connection to database could not be established", e);
        }
        return conn;
    }

    DBHandler(String path) {
        if (path == null) {
            if (System.getProperty("os.name").contains("Windows")) {
                this.path = System.getProperty("user.dir") + "\\bot.db";
            }
            else {
                this.path = System.getProperty("user.dir") + "/bot.db";
            }
        }
        else {
            this.path = path;
        }
        init();
    }

    private void init() {
        conn = connect();

        try (PreparedStatement s_init1 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY NOT NULL, name STRING NOT NULL, announce_channel INTEGER, cleanup INTEGER, message_style INTEGER);")) {
            s_init1.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Initialization of guild table failed", se);
        }

        try (PreparedStatement s_init2 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS games (guild_id INTEGER NOT NULL, game STRING NOT NULL, PRIMARY KEY(guild_id, game), FOREIGN KEY (guild_id) REFERENCES guilds ON UPDATE CASCADE ON DELETE CASCADE);")) {
            s_init2.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Initialization of game table failed", se);
        }

        try (PreparedStatement s_init3 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS maintainers (guild_id INTEGER NOT NULL, user_id INTEGER NOT NULL, PRIMARY KEY(guild_id, user_id), FOREIGN KEY (guild_id) REFERENCES guilds ON UPDATE CASCADE ON DELETE CASCADE);")) {
            s_init3.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Initialization of maintainer table failed", se);
        }
    }

    public void addTableForGuild(Guild guild) {
        String table_name = "g" + guild.getId().asLong();

        try (PreparedStatement s_add = conn.prepareStatement("CREATE TABLE IF NOT EXISTS " + table_name + " (channel_ID INTEGER PRIMARY KEY NOT NULL, name STRING NOT NULL, live INTEGER, post_id INTEGER, title STRING, game STRING, gameID INTEGER, offline_flag INTEGER);")) {
            s_add.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding table for guild {} (id: {}) failed", guild.getName(), guild.getId().asLong(), se);
        }
    }

    public void addOwnerAsMaintainer(Guild guild) {
        try (PreparedStatement s_addM = conn.prepareStatement("INSERT INTO maintainers VALUES(?, ?)")) {
            s_addM.setLong(1, guild.getId().asLong());
            s_addM.setLong(2, guild.getOwner().block().getId().asLong());

            s_addM.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding owner from guild {} (id: {}) as maintainer failed", guild.getName(), guild.getId().asLong(), se);
        }
    }

    public void addMaintainerForGuild(Message message, Long userID) {
        Guild guild = message.getGuild().block();

        try (PreparedStatement s_addM = conn.prepareStatement("INSERT INTO maintainers VALUES(?, ?)")) {
            s_addM.setLong(1, guild.getId().asLong());
            s_addM.setLong(2, userID);

            s_addM.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding maintainer {} for guild {} (id: {}) failed", userID, guild.getName(), guild.getId().asLong(), se);
        }
    }

    public List<Long> getMaintainers(Long guildID) {
        List<Long> result = new ArrayList<>();

        try (PreparedStatement s_getM = conn.prepareStatement("SELECT user_id FROM maintainers WHERE guild_id = ?")) {
            s_getM.setLong(1, guildID);

            try (ResultSet rs = s_getM.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("user_id"));
                }
            }
        }
        catch (SQLException se) {
            dbLogger.error("Getting maintainers for guild {} failed", guildID, se);
        }

        return result;
    }

    public Boolean isMaintainer(Message message) {
        Guild guild = message.getGuild().block();
        User author = message.getAuthor().get();

        try (PreparedStatement s_chk = conn.prepareStatement("SELECT user_id FROM maintainers WHERE guild_id = ? AND user_id = ?")) {
            s_chk.setLong(1, guild.getId().asLong());
            s_chk.setLong(2, author.getId().asLong());

            try (ResultSet rs = s_chk.executeQuery()) {
                return rs.next();
            }
        }
        catch (SQLException se) {
            dbLogger.error("Checking whether or not {} (id: {}) is maintainer for guild {} (id: {}) failed", author.getUsername(), author.getId().asLong(), guild.getName(), guild.getId().asLong(), se);
        }

        return false;
    }

    public void deleteMaintainerForGuild(Message message, Long userID) {
        Guild guild = message.getGuild().block();

        try (PreparedStatement s_delM = conn.prepareStatement("DELETE FROM maintainers WHERE guild_id = ? AND user_id = ?")) {
            s_delM.setLong(1, guild.getId().asLong());
            s_delM.setLong(2, userID);

            s_delM.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Deleting maintainer {} for guild {} (id: {}) failed", userID, guild.getName(), guild.getId().asLong(), se);
        }
    }

    public void addGuild(Guild guild) {
        try (PreparedStatement s_addG = conn.prepareStatement("INSERT INTO guilds VALUES (?,?,?,?,?)")) {
            s_addG.setLong(1, guild.getId().asLong());
            s_addG.setString(2, guild.getName());
            s_addG.setLong(3, guild.getChannels().blockFirst().getId().asLong());
            s_addG.setInt(4, 0);
            s_addG.setInt(5, 1); // embed by default

            s_addG.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding guild {} (id: {}) failed", guild.getName(), guild.getId().asLong(), se);
        }
    }

    public Long getGuild(Guild guild) {
        try (PreparedStatement s_getG = conn.prepareStatement("SELECT id FROM guilds WHERE id = ?")) {
            s_getG.setLong(1, guild.getId().asLong());

            try (ResultSet rs = s_getG.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        catch (SQLException se) {
            dbLogger.error("Getting guild id for guild {} (id: {}) failed", guild.getName(), guild.getId().asLong(), se);
        }
        return null;
    }

    public void changeMessageStyle(Long guildID, Integer value) {
        try (PreparedStatement s_chgM = conn.prepareStatement("UPDATE guilds SET message_style = ? WHERE id = ?")) {
            s_chgM.setInt(1, value);
            s_chgM.setLong(2, guildID);

            s_chgM.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Changing message style for guild {} failed", guildID, se);
        }
    }

    public Boolean updateCleanup(Long guildID) {
        int value = 0;
        boolean result = true;

        try (PreparedStatement s_updCr = conn.prepareStatement("SELECT cleanup FROM guilds WHERE id = ?")) {
            s_updCr.setLong(1, guildID);

            try (ResultSet rs = s_updCr.executeQuery()) {
                if (rs.next()) {
                    value = rs.getInt("cleanup");
                }

                if (value == 0) {
                    result = true;
                    value = 1;
                }
                else {
                    result = false;
                    value = 0;
                }
            }

            try (PreparedStatement s_updC = conn.prepareStatement("UPDATE guilds SET cleanup = ? WHERE id = ?")) {
                s_updC.setInt(1, value);
                s_updC.setLong(2, guildID);

                s_updC.executeUpdate();
            }
        }
        catch (SQLException se) {
            dbLogger.error("Updating cleanup for guild {} failed", guildID);
        }

        return result;
    }

    void updateName(Long guildID, Long channelID, String name) {
        String table_name = "g" + guildID;
        try (PreparedStatement s_updN = conn.prepareStatement("UPDATE " + table_name + " SET name = ? WHERE channel_ID = ?")) {
            s_updN.setString(1, name);
            s_updN.setLong(2, channelID);

            s_updN.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Updating channel name to {} of channel {} in guild {} failed", name, channelID, guildID, se);
        }
    }

    public void addChannelForGuild(Message message, Long channelID, String channelName) {
        Guild guild = message.getGuild().block();
        String table_name = "g" + guild.getId().asLong();

        try (PreparedStatement s_addC = conn.prepareStatement("INSERT INTO " + table_name + "(channel_id, name) VALUES(?, ?)")) {
            s_addC.setLong(1, channelID);
            s_addC.setString(2, channelName);

            s_addC.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding channel {} (id: {}) for guild {} (id: {}) failed", channelName, channelID, guild.getName(), guild.getId().asLong(), se);
        }
    }

    List<GuildStructure> getGuilds() {
        List<GuildStructure> result = new ArrayList<>();

        try (PreparedStatement s_getG = conn.prepareStatement("SELECT id, announce_channel, cleanup, message_style FROM guilds")) {
            try (ResultSet rs = s_getG.executeQuery()) {
                while (rs.next()) {
                    result.add(new GuildStructure(rs.getLong("id"), rs.getLong("announce_channel"), rs.getInt("cleanup") == 1, rs.getInt("message_style")));
                }
            }
        }
        catch (SQLException se) {
            dbLogger.error("Getting all the guilds failed", se);
        }

        return result;
    }

    public void setAnnounceChannel(Long guildID, Long channelID) {
        try (PreparedStatement s_setA = conn.prepareStatement("UPDATE guilds SET announce_channel = ? WHERE id = ?")) {
            s_setA.setLong(1, channelID);
            s_setA.setLong(2, guildID);

            s_setA.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Setting the announce channel to {} for guild {} failed", channelID, guildID, se);
        }
    }

    void updateChannelForGuild(Long guildID, Long channelID, String channelName, Integer live, Long postID, String title, String game, Long gameID, Integer offline_flag) {
        String table_name = "g" + guildID;
        try (PreparedStatement s_updC = conn.prepareStatement("UPDATE " + table_name + " SET name = ?, live = ?, post_id = ?, title = ?, game = ?, gameID = ?, offline_flag = ? WHERE channel_id = ?")) {
            s_updC.setString(1, channelName);
            s_updC.setInt(2, live);
            s_updC.setLong(3, postID);
            s_updC.setString(4, title);
            s_updC.setString(5, game);
            s_updC.setLong(6, gameID);
            s_updC.setInt(7, offline_flag);
            s_updC.setLong(8, channelID);

            s_updC.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Updating channel {} (id: {}) for guild {} failed", channelName, channelID, guildID, se);
        }
    }

    public List<ChannelDels> getChannelsForGuild(Long guildID) {
        List<ChannelDels> result = new ArrayList<>();
        String table_name = "g" + guildID;

        try (PreparedStatement s_getC = conn.prepareStatement("SELECT * FROM " + table_name + " ORDER BY name COLLATE NOCASE")) {
            try (ResultSet rs = s_getC.executeQuery()) {
                while (rs.next()) {
                    ChannelDels cd = new ChannelDels(rs.getLong("channel_id"), rs.getString("name"), rs.getInt("live") == 1, rs.getLong("post_id"), rs.getString("title"), rs.getString("game"), rs.getLong("gameID"), rs.getInt("offline_flag"));
                    result.add(cd);
                }
            }
        }
        catch (SQLException se) {
            dbLogger.error("Getting channels for guild {} failed", guildID, se);
        }
        return result;
    }

    public List<String> getGamesForGuild(Long guildID) {
        List<String> result = new ArrayList<>();

        try (PreparedStatement s_getG = conn.prepareStatement("SELECT game FROM games WHERE guild_id = ? ORDER BY 1 COLLATE NOCASE")) {
            s_getG.setLong(1, guildID);

            try (ResultSet rs = s_getG.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("game"));
                }
            }
        }
        catch (SQLException se) {
            dbLogger.error("Getting game filters for guild {} failed", guildID, se);
        }

        return result;
    }

    public void addGameForGuild(Guild guild, String game) {
        try (PreparedStatement s_addG = conn.prepareStatement("INSERT INTO games VALUES(?, ?)")) {
            s_addG.setLong(1, guild.getId().asLong());
            s_addG.setString(2, game);

            s_addG.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Adding game filter {} for guild {} (id: {}) failed", game, guild.getName(), guild.getId().asLong(), se);
        }
    }

    public void deleteGameForGuild(Guild guild, String game) {
        try (PreparedStatement s_delG = conn.prepareStatement("DELETE FROM games WHERE guild_id = ? AND game = ?")) {
            s_delG.setLong(1, guild.getId().asLong());
            s_delG.setString(2, game);

            s_delG.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Deleting game filter {} for guild {} (id: {}) failed", game, guild.getName(), guild.getId().asLong(), se);
        }
    }

    public void deleteChannelForGuild(Guild guild, Long channelID) {
        String table_name = "g" + guild.getId().asLong();

        try (PreparedStatement s_delC = conn.prepareStatement("DELETE FROM " + table_name + " WHERE channel_id = ?")) {
            s_delC.setLong(1, channelID);

            s_delC.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Deleting channel {} for guilg {} (id: {}) failed", channelID, guild.getName(), guild.getId().asLong(), se);
        }
    }

    private void deleteTableForGuild(Long guildID) {
        String table_name = "g" + guildID;

        try (PreparedStatement s_delG = conn.prepareStatement("DROP TABLE " + table_name)) {
            s_delG.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Deleting table for guild {} failed", guildID, se);
        }
    }

    private void deleteGuild(Long guildID) {
        try (PreparedStatement s_delG = conn.prepareStatement("DELETE FROM guilds WHERE id = ?")) {
            s_delG.setLong(1, guildID);

            s_delG.executeUpdate();
        }
        catch (SQLException se) {
            dbLogger.error("Deleting guild {} failed", guildID, se);
        }
    }

    public void removeGuild(Long guildID) {
        deleteGuild(guildID);
        deleteTableForGuild(guildID);
    }
}
