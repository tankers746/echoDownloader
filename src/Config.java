
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Tom
 */
public class Config {
    List<String> courseIDs;
    List<String> excludeVenues;
    Boolean downloaded;
    Boolean repeats;
    Date before;
    Date after;
    
    Config() {
        courseIDs = new ArrayList<>();
        excludeVenues = new ArrayList<>();
        downloaded = null;
        repeats = null;
        before = null;
        after = null;
        
    }
    
    Config(List courses, List vns, Boolean dld, Boolean rpt, Date bf, Date af) {
        this();
        if(courses != null) courseIDs = courses;
        if(vns != null) excludeVenues = vns;
        downloaded = dld;
        repeats = rpt;
        before = bf;
        after = af;
        
    }
    
    Config(Data d, HashMap<String, String> units, String path) {
        this();
        List<String> excludeUnits = new ArrayList<>();   
        
        if(path != null) {
            try {
                FileReader reader = new FileReader(path);
                BufferedReader bufferedReader = new BufferedReader(reader);               

                String line;            
                while ((line = bufferedReader.readLine()) != null) {
                    line = line.toLowerCase().trim();
                    String[] lineData = line.split("=");
                    if(lineData.length < 2) continue;
                    lineData[1] = lineData[1].replace("\"", "").trim();
                    switch(lineData[0].toLowerCase()) {
                        case "excludeunits" :
                            if(lineData[1] != null) {
                                excludeUnits = Arrays.asList(lineData[1].toUpperCase().split("\\s*,\\s*"));
                            }                        
                            break;
                        case "excludevenues" :
                            if(lineData[1] != null) {
                                excludeVenues = Arrays.asList(lineData[1].split("\\s*,\\s*"));
                            }
                            break;
                        case "ffmpeg" :
                            d.ffmpeg = lineData[1].split("\\.exe")[0];
                            break; 
                        case "before" :
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");  
                                if(lineData[1] != null) before = sdf.parse(lineData[1]);
                            } catch (ParseException ex) {
                                System.err.println("Error parsing before.");
                            }
                            break;
                        case "after" :
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm");  
                                if(lineData[1] != null) after = sdf.parse(lineData[1]+" 23:59");
                            } catch (ParseException ex) {
                                System.err.println("Error parsing after.");
                            }
                            break;
                        case "echobase" :
                            d.echoBase = lineData[1];
                            break;  
                        case "downloadsfolder" :
                            d.downloads = null;
                            //switch backslash to forward slash and remove quotations
                            String dir = lineData[1].replace("\\", "/");
                            File f = new File(dir);
                            if(f.exists() && f.isDirectory()) {
                                d.downloads = dir;
                            } else System.err.println("Downloads folder not valid.");
                            break;                      
                    }
                }
                bufferedReader.close();
            } catch(IOException Ex) {
                System.err.println("Failed to read config file check that the path is correct.");
            }
        }
        for(String unit : units.keySet()) {            
            if(!excludeUnits.contains(unit.toUpperCase())) {
                courseIDs.add(units.get(unit.toUpperCase()));
            }
        }
    }    
    
    private boolean containsVenue(String echoVenue, List<String> filterVenues) {
        for(String searchVenue : filterVenues) {
            if(echoVenue.toLowerCase().contains(searchVenue.toLowerCase())) return true;
        }
        return false;
    }    
    
    public ArrayList<Echo> filterEchoMap(HashMap<String, ArrayList<Echo>> sections) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        sections.keySet().stream()
                .forEach((k) -> filteredEchoes.addAll(filterEchoList(sections.get(k))));
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
    
    public List<Echo> filterEchoList(List<Echo> echoes) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        echoes.stream()
                .filter((e) -> (courseIDs.isEmpty() || courseIDs.contains(e.courseID)))
                .filter((e) -> (excludeVenues.isEmpty() || !containsVenue(e.venue, excludeVenues)))
                .filter((e) -> (repeats == null || repeats.equals(e.repeat)))
                .filter((e) -> (before == null || e.date.before(before)))
                .filter((e) -> (after == null || e.date.after(after)))                        
                .filter((e) -> (!filteredEchoes.contains(e)))
                .forEach(filteredEchoes::add);
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
        
}
