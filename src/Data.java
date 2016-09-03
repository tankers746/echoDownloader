
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger LOGGER = Logger.getLogger(Data.class.getName());
    final String filename = "data.ser"; 
    
    HashMap<String, String> units;    
    HashMap<String, ArrayList<Echo>> courseEchoes;
    
    Data() {
        units = new HashMap<>();        
        courseEchoes = new HashMap<>();
    } 

    public void save() {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            oos.close();
            fos.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving {0}", filename);
        }
    }

    public boolean load() {
        Object obj = null;
        try {
            FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            obj = ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Error reading {0}\n", filename);
        }
        Data loadedData = (Data) obj;
        if(loadedData != null) {
            courseEchoes = loadedData.courseEchoes;
            units = loadedData.units;
        }
        return (obj != null);
    }    
}
