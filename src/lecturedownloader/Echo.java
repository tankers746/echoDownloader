/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author Tom
 */
public class Echo implements Serializable, Comparable<Echo> {
    
    String unit;
    Date date;
    String url;
    String venue;
    String thumbnail;
    int week;
    long fileSize;
    long duration;
    
    Echo(String u, Date d, String download, String v, String t, int w, long size, long dur) {
        unit = u;
        date = d;
        url = download;
        venue = v;
        thumbnail = t;
        week = w;
        fileSize = size;
        duration = dur;
    }

    public int compareTo(Echo other) {
        return this.date.compareTo(other.date);
}    
    
    Echo() {
        this(null, null, null, null, null, 0, 0, 0);
    }
    
}
