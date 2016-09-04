/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author Tom
 */
public class Echo implements Serializable, Comparable<Echo> {
    String unit;
    String unitName;
    String title;    
    String url;
    String venue;
    String thumbnail;
    String echoBase;
    String contentDir;
    String streamDir;
    int episode;
    Date date;
    long fileSize;
    long duration;
    Boolean downloaded;
    String uuid;
    
    Echo(String u, String name, String download, String v, String t, Date d, String baseUrl, long size, long dur, Boolean dl, int ep, String uName, String id, String content, String stream) {
        unit = u;
        title = name;
        url = download;
        venue = v;
        thumbnail = t;
        date = d;        
        echoBase = baseUrl;
        fileSize = size;
        duration = dur;
        downloaded = dl;
        episode = ep;
        unitName = uName;
        uuid = id;
        contentDir = content;
        streamDir = stream;
    }

    @Override
    public int compareTo(Echo other) {
        return this.date.compareTo(other.date);
    }

    public String getUUID() {
        return uuid;
    }    
    
    Echo() {
        this(null, null, null, null, null, null, null, 0, 0, false, 0, null, null, null, null);
    }
    
}
