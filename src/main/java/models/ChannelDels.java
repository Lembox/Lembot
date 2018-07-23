package models;

public class ChannelDels {
    private Long channelID;
    private String name;
    private Boolean live;
    private Long postID;
    private String title;
    private String game;
    private Integer offline_flag;       // offline-flag to give streamers 3 minutes time to reconnect before announcing again

    public ChannelDels(Long channelID, String name, Boolean live, Long postID, String title, String game, Integer offline_flag) {
        this.channelID = channelID;
        this.name = name;
        this.live = live;
        this.postID = postID;
        this.title = title;
        this.game = game;
        this.offline_flag = offline_flag;
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
}
