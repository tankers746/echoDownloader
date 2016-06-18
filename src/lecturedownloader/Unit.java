/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;

import java.io.Serializable;
import java.util.HashMap;

/**
 *
 * @author Tom
 */
public class Unit implements Serializable {
    String name;
    HashMap<Integer, Year> years;    
    
    Unit(String unit) {
        name = unit;
        years = new HashMap<Integer, Year>();
    }
    
}
