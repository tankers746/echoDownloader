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
public class Year implements Serializable {
    int value;
    Semester semester1;
    Semester semester2;
    
    Year(int year) {
        value = year;
        semester1 = new Semester(1);
        semester2 = new Semester(2);
    }
    
}
