
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
    HashMap<Integer, ArrayList<Echo>> sections;
    ArrayList<String> venues;
    String echoBase;
    String downloads;     
    
    Data() {
        sections = new HashMap<>();
        venues = new ArrayList<>();
        echoBase= "";
        downloads = "";
    }
}
