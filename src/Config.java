
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
    List<Integer> sectionIDs;
    List<String> venues;
    Boolean downloaded;
    Boolean repeats;
    Date before;
    Date after;
    
    Config() {
        sectionIDs = new ArrayList<>();
        venues = new ArrayList<>();
        downloaded = null;
        repeats = null;
        before = null;
        after = null;
        
    }      
    
    Config(List sections, List vns, Boolean dld, Boolean rpt, Date bf, Date af) {
        this();
        if(sections != null) sectionIDs = sections;
        if(vns != null) venues = vns;
        downloaded = dld;
        repeats = rpt;
        before = bf;
        after = af;
        
    }
    
    Config(String path, echoDownloader ld) throws FileNotFoundException, IOException {
        this();
        String[] unitCodes = new String[0];
        int semester = 0;
        FileReader reader = new FileReader(path);
        BufferedReader bufferedReader = new BufferedReader(reader);               

        String line;            
        while ((line = bufferedReader.readLine()) != null) {
            line = line.toLowerCase().replaceAll("\\s","");
            String[] lineData = line.split("=");
            if(lineData.length < 2) continue;
            lineData[1] = lineData[1].replace("\"", "");
            switch(lineData[0].toLowerCase()) {
                case "semester" :
                    if(lineData[1].equals("1") || lineData[1].equals("2")); semester = Integer.parseInt(lineData[1])-1;
                    break;
                case "units" :
                    if(lineData[1] != null) unitCodes = lineData[1].split(",");                        
                    break;
                case "venues" :
                    if(lineData[1] != null) {
                        venues = Arrays.asList(lineData[1].split(","));
                    }
                    break;
                case "repeats" :
                    repeats = parseBoolean(lineData[1]);
                    break;
                case "downloaded" :
                    downloaded = parseBoolean(lineData[1]);
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
                    ld.d.echoBase = lineData[1];
                    break;  
                case "downloadsfolder" :
                    //switch backslash to forward slash and remove quotations
                    String dir = lineData[1].replace("\\", "/");
                    File f = new File(dir);
                    if(f.exists() && f.isDirectory()) {
                        ld.d.downloads = dir;
                    } else System.err.println("Downloads folder not valid.");
                    break;                      
            }
        }
        for(String unit : unitCodes) {            
            if(ld.units.containsKey(unit.toUpperCase())) {
                sectionIDs.add(ld.units.get(unit.toUpperCase())[semester]);
            } else {
                System.out.println(unit + " (Semester " + semester+1 + ") is not in the list of fetchable units, add its section number using -a.");
            } 
        }
        bufferedReader.close();
    }    
    
    private Boolean parseBoolean(String s) {
        if(s == null) return null;
        if(s.toLowerCase().equals("true")) return true;
        if(s.toLowerCase().equals("false")) return false;
        return null;
    }
    
    private boolean containsVenue(String echoVenue, List<String> filterVenues) {
        for(String searchVenue : filterVenues) {
            if(echoVenue.toLowerCase().contains(searchVenue)) return true;
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
