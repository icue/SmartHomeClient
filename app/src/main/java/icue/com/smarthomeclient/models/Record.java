package icue.com.smarthomeclient.models;

import java.io.Serializable;

/**
 * Created by Icue on 2017/4/28.
 */

public class Record implements Serializable {
    private String image, audio, timestamp;
    public Record() {
        image = "";
        audio = "";
        timestamp = "";
    }
    public Record(String image, String timestamp) {
        this.image = image;
        this.timestamp = timestamp;
    }
    public Record(String image, String timestamp, String audio) {
        this(image,timestamp);
        this.audio = audio;
    }
    public String getImage(){
        return image;
    }
    public String getAudio(){
        return audio;
    }
    public String getTimestamp(){
        return timestamp;
    }
}