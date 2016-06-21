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
    String title;    
    String url;
    String venue;
    String thumbnail;
    Date date;
    int week;
    long fileSize;
    long duration;
    Boolean downloaded;
    
    Echo(String u, String name, String download, String v, String t, Date d, int w, long size, long dur, Boolean dl) {
        unit = u;
        title = name;
        url = download;
        venue = v;
        thumbnail = t;
        date = d;        
        week = w;
        fileSize = size;
        duration = dur;
        downloaded = dl;
    }

    public int compareTo(Echo other) {
        return this.date.compareTo(other.date);
}    
    
    Echo() {
        this(null, null, null, null, null, null, 0, 0, 0, false);
    }
    
}
