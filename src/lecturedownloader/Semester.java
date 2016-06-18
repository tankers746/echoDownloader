/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author Tom
 */
public class Semester implements Serializable {
    int value;    
    int sectionID;
    ArrayList<Echo> echoes;
    
    Semester(int semester) {
        value = semester;
        sectionID = 0;
        echoes = new ArrayList<Echo>();
    }
    
    
}
