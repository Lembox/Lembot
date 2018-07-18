package models;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;

public class ChannelDels {
    Long channelID;
    String name;
    Boolean live;
    Long postID;
    String title;
    String game;
    Integer offline_flag;       // offline-flag to give streamers 3 minutes time to reconnect before announcing again
    ChannelEndpoint channelEndpoint;

    public ChannelDels() { }

    public ChannelDels(Long channelID, String name, Boolean live, Long postID, String title, String game, Integer offline_flag) {
        this.channelID = channelID;
        this.name = name;
        this.live = live;
        this.postID = postID;
        this.title = title;
        this.game = game;
        this.offline_flag = offline_flag;
    }

    public ChannelDels(Long channelID, String name, Boolean live, Long postID, String title, String game, Integer offline_flag, ChannelEndpoint channelEndpoint) {
        this.channelID = channelID;
        this.name = name;
        this.live = live;
        this.postID = postID;
        this.title = title;
        this.game = game;
        this.offline_flag = offline_flag;
        this.channelEndpoint = channelEndpoint;
    }

    public Long getChannelID() {
        return channelID;
    }

    public void setChannelID(Long channelID) {
        this.channelID = channelID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getLive() {
        return live;
    }

    public void setLive(Boolean live) {
        this.live = live;
    }

    public Long getPostID() {
        return postID;
    }

    public void setPostID(Long postID) {
        this.postID = postID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public Integer getOffline_flag() {
        return offline_flag;
    }

    public void setOffline_flag(Integer offline_flag) {
        this.offline_flag = offline_flag;
    }

    public ChannelEndpoint getChannelEndpoint() {
        return channelEndpoint;
    }

    public void setChannelEndpoint(ChannelEndpoint channelEndpoint) {
        this.channelEndpoint = channelEndpoint;
    }
}
