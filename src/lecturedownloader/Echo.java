/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;

import java.io.Serializable;

/**
 *
 * @author Tom
 */
public class Echo implements Serializable {
    
    String unit;
    String date;
    String url;
    String venue;
    String thumbnail;
    int week;
    long fileSize;
    
    Echo(String u, String d, String download, String v, String t, int w, long size) {
        unit = u;
        date = d;
        url = download;
        venue = v;
        thumbnail = t;
        week = w;
        fileSize = size;
    }
    
    Echo() {
        this(null, null, null, null, null, 0, 0);
    }
    
}
