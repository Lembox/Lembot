package models;

public class ChannelDels {
    private String channelID;
    private String name;
    private Boolean live;
    private Long postID;
    private String title;
    private String game;
    private String gameID;
    private Integer offline_flag;       // offline-flag to give streamers 3 minutes time to reconnect before announcing again
    private Integer remove_flag = 0;     // if announcer can't detect the channel
    private String iconUrl;


    public ChannelDels(String channelID, String name, Boolean live, Long postID, String title, String game, String gameID, Integer offline_flag) {
        this.channelID = channelID;
        this.name = name;
        this.live = live;
        this.postID = postID;
        this.title = title;
        this.game = game;
        this.gameID = gameID;
        this.offline_flag = offline_flag;
    }

    public String getChannelID() {
        return channelID;
    }

    public void setChannelID(String channelID) {
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

    public String getGameID() {
        return gameID;
    }

    public void setGameID(String gameID) {
        this.gameID = gameID;
    }

    public Integer getOffline_flag() {
        return offline_flag;
    }

    public void setOffline_flag(Integer offline_flag) {
        this.offline_flag = offline_flag;
    }

    public Integer getRemove_flag() {
        return remove_flag;
    }

    public void setRemove_flag(Integer check_flag) {
        this.remove_flag = check_flag;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
}
