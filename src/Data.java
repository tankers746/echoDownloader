
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Tom
 */
public class Data implements Serializable {
    HashMap<String, ArrayList<Echo>> courses;
    ArrayList<String> venues;
    String echoBase;
    String downloads;   
    String ffmpeg;
    
    Data() {
        courses = new HashMap<>();
        venues = new ArrayList<>();
        echoBase= "http://prod.lcs.uwa.edu.au:8080/";
        downloads = "";
        ffmpeg = "";
    } 

    public static final void saveObject(String filename, Object h) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(h);
            oos.close();
            fos.close();
        } catch (IOException e) {
            System.err.println("Error saving " + filename);
        }
    }

    public static final Object getObject(String filename) {
        Object obj = null;
        try {
            FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            obj = ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reading " + filename);
        }
        return obj;
    }    
}
