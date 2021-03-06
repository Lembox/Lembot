package models;

import com.github.twitch4j.helix.domain.User;
import core.Lembot;
import core.StreamAnnouncer;

import java.util.*;

public class GuildStructure {
    private Long guild_id;
    private List<ChannelDels> twitch_channels;
    private Map<String, String> game_filters;
    private Long announce_channel;
    private Boolean cleanup;
    private Integer message_style;
    private StreamAnnouncer announcer;
    private Lembot lembot;

    public GuildStructure(Long guild_id, List<ChannelDels> twitch_channels, Map<String, String> game_filters, Long announce_channel, Lembot lembot) {     // for new guilds
        this.guild_id = guild_id;
        this.twitch_channels = twitch_channels;
        this.game_filters = game_filters;
        this.announce_channel = announce_channel;
        this.cleanup = false;
        this.message_style = 1;
        this.lembot = lembot;
    }

    public GuildStructure(Long guild_id, Long announce_channel, Boolean cleanup, Integer message_style) {       // necessary for DB reads
        this.guild_id = guild_id;
        this.announce_channel = announce_channel;
        this.cleanup = cleanup;
        this.message_style = message_style;
    }

    public Long getGuild_id() {
        return guild_id;
    }

    public void setGuild_id(Long guild_id) {
        this.guild_id = guild_id;
    }

    public List<ChannelDels> getTwitch_channels() {
        return twitch_channels;
    }

    public void setTwitch_channels(List<ChannelDels> twitch_channels) {
        this.twitch_channels = twitch_channels;
    }

    public Long getAnnounce_channel() {
        return announce_channel;
    }

    public void setAnnounce_channel(Long announce_channel) {
        this.announce_channel = announce_channel;
    }

    public Map<String, String> getGame_filters() {
        return game_filters;
    }

    public void setGame_filters(Map<String, String> game_filters) {
        this.game_filters = game_filters;
    }

    public void addChannel(User u) {
        twitch_channels.add(new ChannelDels(u.getId(), u.getDisplayName(), false, null, "title", "game",0L, 0));
        sortChannels();
    }

    public void removeChannel(ChannelDels channelDels) {
        twitch_channels.remove(channelDels);
    }

    public void addGameFilter(String gameID, String game) {
        game_filters.put(gameID, game);
        sortGames();
    }

    public void removeGameFilter(String gameID) {
        game_filters.remove(gameID);
    }

    public Lembot getLembot() {
        return lembot;
    }

    public void setLembot(Lembot lembot) {
        this.lembot = lembot;
    }

    public Boolean getCleanup() {
        return cleanup;
    }

    public void setCleanup(Boolean cleanup) {
        this.cleanup = cleanup;
    }

    public StreamAnnouncer getAnnouncer() {
        return announcer;
    }

    public void setAnnouncer(StreamAnnouncer announcer) {
        this.announcer = announcer;
    }

    public Integer getMessage_style() {
        return message_style;
    }

    public void setMessage_style(Integer message_style) {
        this.message_style = message_style;
    }

    private void sortGames() {
        game_filters = new MapUtil().sortByValue(game_filters);
    }

    private void sortChannels() {
        twitch_channels.sort(new ChannelComparator());
    }

    private class ChannelComparator implements Comparator<ChannelDels>
    {
        public int compare(ChannelDels c1, ChannelDels c2)
        {
            return c1.getName().compareTo(c2.getName());
        }
    }

    public class MapUtil {
        public <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
            List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
            list.sort(Map.Entry.comparingByValue());

            Map<K, V> result = new LinkedHashMap<>();
            for (Map.Entry<K, V> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }

            return result;
        }
    }
}

