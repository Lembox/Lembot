package models;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import me.philippheuer.twitch4j.model.Channel;

import java.util.ArrayList;
import java.util.List;

public class GuildStructure {
    private Long guild_id;
    private List<ChannelDels> twitch_channels;
    private List<String> game_filters;
    private List<ChannelEndpoint> channelsToBeAdded = new ArrayList<>();
    private List<ChannelDels> channelsToBeRemoved = new ArrayList<>();
    private List<String> gamesToBeAdded = new ArrayList<>();
    private List<String> gamesToBeRemoved = new ArrayList<>();
    private Long announce_channel;
    private Boolean cleanup = false;

    public GuildStructure(Long guild_id, List<ChannelDels> twitch_channels, List<String> game_filters, Long announce_channel) {
        this.guild_id = guild_id;
        this.twitch_channels = twitch_channels;
        this.game_filters = game_filters;
        this.announce_channel = announce_channel;
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

    public void addTwitch_channels(ChannelDels channelDels) {
        twitch_channels.add(channelDels);
    }

    public void removeTwitch_channels(ChannelDels channelDels) {
        twitch_channels.remove(channelDels);
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

    public List<String> getGame_filters() {
        return game_filters;
    }

    public void addGame_filter(String game) {
        game_filters.add(game);
    }

    public void removeGame_filter(String game) {
        game_filters.remove(game);
    }

    public void setGame_filters(List<String> game_filters) {
        this.game_filters = game_filters;
    }

    public List<ChannelEndpoint> getChannelsToBeAdded() {
        return channelsToBeAdded;
    }

    public void addChannelToBeAdded(ChannelEndpoint newChannel) { channelsToBeAdded.add(newChannel); }


    public List<ChannelDels> getChannelsToBeRemoved() {
        return channelsToBeRemoved;
    }

    public void addChannel(ChannelEndpoint channelEndpoint) {
        Channel c = channelEndpoint.getChannel();
        twitch_channels.add(new ChannelDels(channelEndpoint.getChannelId(), c.getName(), false, null, c.getStatus(), c.getGame(), 0, channelEndpoint));
    }

    public void removeChannel(ChannelDels channelDels) {
        twitch_channels.remove(channelDels);
    }

    public void addChannelToBeRemoved(ChannelDels newChannel) {
        channelsToBeRemoved.add(newChannel);
    }

    public void addGameFilter(String game) {
        game_filters.add(game);
    }

    public void removeGameFilter(String game) {
        game_filters.remove(game);
    }

    public void setChannelsToBeAdded(List<ChannelEndpoint> channelsToBeAdded) {
        this.channelsToBeAdded = channelsToBeAdded;
    }

    public void setChannelsToBeRemoved(List<ChannelDels> channelsToBeRemoved) {
        this.channelsToBeRemoved = channelsToBeRemoved;
    }

    public Boolean getCleanup() {
        return cleanup;
    }

    public void setCleanup(Boolean cleanup) {
        this.cleanup = cleanup;
    }

    public void addGameToBeAdded(String game) {
        this.gamesToBeAdded.add(game);
    }

    public void addGameToBeRemoved(String game) {
        this.gamesToBeRemoved.add(game);
    }

    public List<String> getGamesToBeAdded() {
        return gamesToBeAdded;
    }

    public List<String> getGamesToBeRemoved() {
        return gamesToBeRemoved;
    }

    public void setGamesToBeAdded(List<String> gamesToBeAdded) {
        this.gamesToBeAdded = gamesToBeAdded;
    }

    public void setGamesToBeRemoved(List<String> gamesToBeRemoved) {
        this.gamesToBeRemoved = gamesToBeRemoved;
    }
}
