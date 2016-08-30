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
    String name;    
    String url;
    String venue;
    String thumbnail;
    String courseID;
    String echoContent;
    int episode;
    Date date;
    long fileSize;
    long duration;
    Boolean downloaded;
    Boolean repeat;
    String uuid;
    
    Echo(String u, String title, String download, String v, String t, Date d, String cID, long size, long dur, Boolean dl, Boolean r, int ep, String uName, String id, String ec) {
        unit = u;
        name = title;
        url = download;
        venue = v;
        thumbnail = t;
        date = d;        
        courseID = cID;
        fileSize = size;
        duration = dur;
        downloaded = dl;
        repeat = r;
        episode = ep;
        unitName = uName;
        uuid = id;
        echoContent = ec;
    }

    public int compareTo(Echo other) {
        return this.date.compareTo(other.date);
    }

    public String getUUID() {
        return uuid;
    }    
    
    Echo() {
        this(null, null, null, null, null, null, null, 0, 0, false, false, 0, null, null, null);
    }
    
}
