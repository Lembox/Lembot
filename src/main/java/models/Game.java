package models;

import java.net.URL;

public class Game {
    private URL box_art_url;
    private String id;
    private String name;

    public URL getBox_art_url() {
        return box_art_url;
    }

    public void setBox_art_url(URL box_art_url) {
        this.box_art_url = box_art_url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
