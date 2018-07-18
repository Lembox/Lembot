package core;

import models.ChannelDels;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBHandler {
    private String path;

    private Connection connect() {
        Connection conn = null;
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);    // enable foreign key constraints
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + path);
            ds.setConfig(config);
            conn = ds.getConnection();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }

    DBHandler(String path) {
        this.path = path;
        init();
    }

    DBHandler() {
        if (System.getProperty("os.name").contains("Windows")) {
            this.path = System.getProperty("user.dir") + "\\bot.db";
        }
        else {
            this.path = System.getProperty("user.dir") + "/bot.db";
        }

        init();
    }

    private void init() {

        try (Connection conn = connect()) {
            PreparedStatement s_init1 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY NOT NULL, name STRING NOT NULL, announce_channel INTEGER, cleanup INTEGER);");
            PreparedStatement s_init2 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS games (guild_id INTEGER NOT NULL, game STRING NOT NULL, PRIMARY KEY(guild_id, game), FOREIGN KEY (guild_id) REFERENCES guilds ON UPDATE CASCADE ON DELETE CASCADE);");
            PreparedStatement s_init3 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS maintainers (guild_id INTEGER NOT NULL, user_id INTEGER NOT NULL, PRIMARY KEY(guild_id, user_id), FOREIGN KEY (guild_id) REFERENCES guilds ON UPDATE CASCADE ON DELETE CASCADE);");

            s_init1.execute();
            s_init2.execute();
            s_init3.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public Boolean checkTableForGuild(IGuild guild) {
        int i = 1;

        try (Connection conn = connect()) {
            PreparedStatement s_chk = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?;");
            s_chk.setString(1, "g" + guild.getLongID());

            ResultSet rs = s_chk.executeQuery();

            while (rs.next()) {
                i++;
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return i > 1;
    }

    public void addTableForGuild(IGuild guild) {
        try (Connection conn = connect()) {
            String table_name = "g" + guild.getLongID();

            PreparedStatement s_add = conn.prepareStatement("CREATE TABLE IF NOT EXISTS " + table_name + " (channel_ID INTEGER PRIMARY KEY NOT NULL, name STRING NOT NULL, live INTEGER, post_id INTEGER, title STRING, game STRING, offline_flag INTEGER);");
            s_add.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void addOwnerAsMaintainer(IGuild guild) {
        try (Connection conn = connect()) {
            PreparedStatement s_addM = conn.prepareStatement("INSERT INTO maintainers VALUES(?, ?)");
            s_addM.setLong(1, guild.getLongID());
            s_addM.setLong(2, guild.getOwnerLongID());

            s_addM.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void addMaintainerForGuild(IMessage message, Long userID) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            PreparedStatement s_addM = conn.prepareStatement("INSERT INTO maintainers VALUES(?, ?)");
            s_addM.setLong(1, guild.getLongID());
            s_addM.setLong(2, userID);

            s_addM.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public List<Long> getMaintainers(Long guildID) {
        List<Long> result = new ArrayList<>();

        try (Connection conn = connect()) {
            PreparedStatement s_getM = conn.prepareStatement("SELECT user_id FROM maintainers WHERE guild_id = ?");
            s_getM.setLong(1, guildID);

            ResultSet rs = s_getM.executeQuery();

            while (rs.next()) {
                result.add(rs.getLong("user_id"));
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

    public Boolean isMaintainer(IMessage message) {
        IGuild guild = message.getGuild();
        int i = 1;

        try (Connection conn = connect()) {
            PreparedStatement s_chk = conn.prepareStatement("SELECT user_id FROM maintainers WHERE guild_id = ? AND user_id = ?");
            s_chk.setLong(1, guild.getLongID());
            s_chk.setLong(2, message.getAuthor().getLongID());

            ResultSet rs = s_chk.executeQuery();

            while (rs.next()) {
                i++;
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return i > 1;
    }

    public void deleteMaintainerForGuild(IMessage message, Long userID) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            PreparedStatement s_delM = conn.prepareStatement("DELETE FROM maintainers WHERE guild_id = ? AND user_id = ?");
            s_delM.setLong(1, guild.getLongID());
            s_delM.setLong(2, userID);

            s_delM.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void addGuild(IGuild guild) {
        try (Connection conn = connect()) {
            PreparedStatement s_addG = conn.prepareStatement("INSERT INTO guilds VALUES (?,?,?,?)");
            s_addG.setLong(1, guild.getLongID());
            s_addG.setString(2, guild.getName());
            s_addG.setLong(3, guild.getDefaultChannel().getLongID());
            s_addG.setInt(4, 0);

            s_addG.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public Long getGuild(IGuild guild) {
        try (Connection conn = connect()) {
            PreparedStatement s_getG = conn.prepareStatement("SELECT id FROM guilds WHERE id = ?");
            s_getG.setLong(1, guild.getLongID());

            ResultSet rs = s_getG.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
        return null;
    }

    public Boolean updateCleanup(Long guildID) {
        Integer value = 0;
        Boolean result = true;

        try (Connection conn = connect()) {
            PreparedStatement s_updCr = conn.prepareStatement("SELECT cleanup FROM guilds WHERE id = ?");
            s_updCr.setLong(1, guildID);

            ResultSet rs = s_updCr.executeQuery();

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

            PreparedStatement s_updC = conn.prepareStatement("UPDATE guilds SET cleanup = ? WHERE id = ?");
            s_updC.setInt(1, value);
            s_updC.setLong(2, guildID);

            s_updC.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

    public Boolean readCleanup(Long guildID) {
        try (Connection conn = connect()) {
            PreparedStatement s_updCr = conn.prepareStatement("SELECT cleanup FROM guilds WHERE id = ?");
            s_updCr.setLong(1, guildID);

            ResultSet rs = s_updCr.executeQuery();

            if (rs.next()) {
                return rs.getInt("cleanup") == 1;
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
        return false;
    }

    public void addChannelForGuild(IMessage message, Long channelID, String channelName) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            String table_name = "g" + guild.getLongID();
            PreparedStatement s_addC = conn.prepareStatement("INSERT INTO " + table_name + "(channel_id, name) VALUES(?, ?)");
            s_addC.setLong(1, channelID);
            s_addC.setString(2, channelName);

            s_addC.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public List<Long[]> getGuilds() {
        List<Long[]> result = new ArrayList<>();

        try (Connection conn = connect()) {
            PreparedStatement s_getG = conn.prepareStatement("SELECT id, announce_channel FROM guilds");

            ResultSet rs = s_getG.executeQuery();
            while (rs.next()) {
                result.add(new Long[] {rs.getLong("id"), rs.getLong("announce_channel")});
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

    public Long getAnnounceChannel(Long guildID) {
        try (Connection conn = connect()) {
            PreparedStatement s_getA = conn.prepareStatement("SELECT announce_channel FROM guilds WHERE id = ?");
            s_getA.setLong(1, guildID);

            ResultSet rs = s_getA.executeQuery();
            if (rs.next()) {
                return rs.getLong("announce_channel");
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
        return null;
    }

    public void setAnnounceChannel(Long guildID, Long channelID) {
        try (Connection conn = connect()) {
            PreparedStatement s_setA = conn.prepareStatement("UPDATE guilds SET announce_channel = ? WHERE id = ?");
            s_setA.setLong(1, channelID);
            s_setA.setLong(2, guildID);

            s_setA.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    void updateChannelForGuild(Long guildID, Long channelID, String channelName, Integer live, Long postID, String title, String game, Integer offline_flag) {
        try (Connection conn = connect()) {
            String table_name = "g" + guildID;
            PreparedStatement s_updC = conn.prepareStatement("UPDATE " + table_name + " SET name = ?, live = ?, post_id = ?, title = ?, game = ?, offline_flag = ? WHERE channel_id = ?");
            s_updC.setString(1, channelName);
            s_updC.setInt(2, live);
            s_updC.setLong(3, postID);
            s_updC.setString(4, title);
            s_updC.setString(5, game);
            s_updC.setInt(6, offline_flag);
            s_updC.setLong(7, channelID);

            s_updC.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public List<ChannelDels> getChannelsForGuild(Long guildID) {
        List<ChannelDels> result = new ArrayList<>();

        try (Connection conn = connect()) {
            String table_name = "g" + guildID;
            PreparedStatement s_getC = conn.prepareStatement("SELECT * FROM " + table_name);

            ResultSet rs = s_getC.executeQuery();
            while (rs.next()) {


                ChannelDels cd = new ChannelDels(rs.getLong("channel_id"), rs.getString("name"), rs.getInt("live") == 1, rs.getLong("post_id"), rs.getString("title"), rs.getString("game"), rs.getInt("offline_flag"));
                result.add(cd);
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
        return result;
    }

    public List<String> getGamesForGuild(Long guildID) {
        List<String> result = new ArrayList<>();

        try (Connection conn = connect()) {
            PreparedStatement s_getG = conn.prepareStatement("SELECT game FROM games WHERE guild_id = ?");
            s_getG.setLong(1, guildID);

            ResultSet rs = s_getG.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("game"));
            }
        }
        catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

    public void addGameForGuild(IMessage message, String game) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            PreparedStatement s_addG = conn.prepareStatement("INSERT INTO games VALUES(?, ?)");
            s_addG.setLong(1, guild.getLongID());
            s_addG.setString(2, game);

            s_addG.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void deleteGameForGuild(IMessage message, String game) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            PreparedStatement s_delG = conn.prepareStatement("DELETE FROM games WHERE guild_id = ? AND game = ?");
            s_delG.setLong(1, guild.getLongID());
            s_delG.setString(2, game);

            s_delG.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void deleteChannelForGuild(IMessage message, Long channelID) {
        IGuild guild = message.getGuild();

        try (Connection conn = connect()) {
            String table_name = "g" + guild.getLongID();
            PreparedStatement s_delC = conn.prepareStatement("DELETE FROM " + table_name + " WHERE channel_id = ?");
            s_delC.setLong(1, channelID);

            s_delC.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    private void deleteTableForGuild(Long guildID) {
        try (Connection conn = connect()) {
            String table_name = "g" + guildID;
            PreparedStatement s_delG = conn.prepareStatement("DROP TABLE " + table_name);

            s_delG.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    private void deleteGuild(Long guildID) {
        try (Connection conn = connect()) {
            PreparedStatement s_delG = conn.prepareStatement("DELETE FROM guilds WHERE id = ?");
            s_delG.setLong(1, guildID);

            s_delG.execute();
        }
        catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void removeGuild(Long guildID) {
        deleteGuild(guildID);
        deleteTableForGuild(guildID);
    }
}
