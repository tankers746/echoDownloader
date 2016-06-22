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
    String name;    
    String url;
    String venue;
    String thumbnail;
    Date date;
    int sectionID;
    long fileSize;
    long duration;
    Boolean downloaded;
    Boolean repeat;
    
    Echo(String u, String title, String download, String v, String t, Date d, int sID, long size, long dur, Boolean dl, Boolean r) {
        unit = u;
        name = title;
        url = download;
        venue = v;
        thumbnail = t;
        date = d;        
        sectionID = sID;
        fileSize = size;
        duration = dur;
        downloaded = dl;
        repeat = r;
    }

    public int compareTo(Echo other) {
        return this.date.compareTo(other.date);
}    
    
    Echo() {
        this(null, null, null, null, null, null, 0, 0, 0, false, false);
    }
    
}
