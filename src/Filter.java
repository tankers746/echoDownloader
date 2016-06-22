
import java.io.BufferedReader;
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Tom
 */
public class Filter {
    List<Integer> sectionIDs;
    List<String> venues;
    Boolean downloaded;
    Boolean repeats;
    Date before;
    Date after;
    
    Filter() {
        sectionIDs = new ArrayList<>();
        venues = new ArrayList<>();
        downloaded = null;
        repeats = null;
        before = null;
        after = null;
        
    }      
    
    Filter(List sections, List vns, Boolean dld, Boolean rpt, Date bf, Date af) {
        this();
        if(sections != null) sectionIDs = sections;
        if(vns != null) venues = vns;
        downloaded = dld;
        repeats = rpt;
        before = bf;
        after = af;
        
    }
    
    Filter(String path, HashMap<String, int[]> units) {
        this();
        String[] unitCodes = new String[0];
        int semester = 0;
        try {
            FileReader reader = new FileReader(path);
            BufferedReader bufferedReader = new BufferedReader(reader);   
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");              
            
            String line;            
            while ((line = bufferedReader.readLine()) != null) {
                String[] lineData = line.split("=");
                switch(lineData[0]) {
                    case "semester" :
                        if(lineData[1].equals("1") || lineData[1].equals("2")); semester = Integer.parseInt(lineData[1])-1;
                        break;
                    case "units" :
                        if(lineData[1] != null) unitCodes = lineData[1].split(",");                        
                        break;
                    case "venues" :
                        if(lineData[1] != null) venues = Arrays.asList(lineData[1].split(","));
                        break;
                    case "repeats" :
                        repeats = parseBoolean(lineData[1]);
                        break;
                    case "downloaded" :
                        downloaded = parseBoolean(lineData[1]);
                        break;  
                    case "before" :
                        try {
                            if(lineData[1] != null) before = sdf.parse(lineData[1]);
                        } catch (ParseException ex) {
                            System.err.println("Error parsing before.");
                        }
                        break;
                    case "after" :
                        try {
                            if(lineData[1] != null) after = sdf.parse(lineData[1]);
                        } catch (ParseException ex) {
                            System.err.println("Error parsing after.");
                        }
                        break;                                     
                }
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Error reading file.");
        }
        for(String unit : unitCodes) { 
            if(units.containsKey(unit)) sectionIDs.add(units.get(unit)[semester]); 
        }
    }    
    
    private Boolean parseBoolean(String s) {
        if(s == null) return null;
        if(s.toLowerCase().equals("true")) return true;
        if(s.toLowerCase().equals("false")) return false;
        return null;
    }
    
    private boolean containsVenue(String echoVenue, List<String> filterVenues) {
        for(String searchVenue : filterVenues) {
            if(echoVenue.contains(searchVenue)) return true;
        }
        return false;
    }    
    
    public ArrayList<Echo> filterEchoMap(HashMap<Integer, ArrayList<Echo>> sections) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        sections.keySet().stream()
                .forEach((k) -> filteredEchoes.addAll(filterEchoList(sections.get(k))));
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
    
    public ArrayList<Echo> filterEchoList(List<Echo> echoes) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        echoes.stream()
                .filter((e) -> (sectionIDs.isEmpty() || sectionIDs.contains(e.sectionID)))
                .filter((e) -> (venues.isEmpty() || containsVenue(e.venue, venues)))
                .filter((e) -> (downloaded == null || downloaded.equals(e.downloaded)))
                .filter((e) -> (repeats == null || repeats.equals(e.repeat)))
                .filter((e) -> (before == null || e.date.before(before)))
                .filter((e) -> (after == null || e.date.after(after)))                        
                .filter((e) -> (!filteredEchoes.contains(e)))
                .forEach(filteredEchoes::add);
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
        
}
