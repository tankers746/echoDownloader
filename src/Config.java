
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
public class Config {
    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());
    Data data;
    List<String> excludeUnits;
    List<String> excludeVenues;
    Date before;
    Date after;
    String ffmpeg;
    String downloads;
    
    Config() {
        //first we load the saved data
        data = new Data();
        if (data.load() == false) { //checks if there is a saved table
            data.save();
        }
        excludeUnits = new ArrayList<>();
        excludeVenues = new ArrayList<>();
        before = null;
        after = null;
    }
    
    Config(String path) {
        this(); 
        
        if(path != null) {
            try {
                FileReader reader = new FileReader(path);
                BufferedReader bufferedReader = new BufferedReader(reader);               

                String line;            
                while ((line = bufferedReader.readLine()) != null) {
                    line = line.trim();
                    String[] lineData = line.split("=");
                    if(lineData.length < 2) continue;
                    lineData[1] = lineData[1].replaceAll("[\"“”]", "").trim();
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
                            ffmpeg = lineData[1].split("\\.exe")[0].replace("\\", "/").replaceFirst("^~",System.getProperty("user.home"));;
                            break; 
                        case "before" :
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");  
                                if(lineData[1] != null) before = sdf.parse(lineData[1]);
                            } catch (ParseException ex) {
                                LOGGER.log(Level.WARNING,"Error parsing before.");
                            }
                            break;
                        case "after" :
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm");  
                                if(lineData[1] != null) after = sdf.parse(lineData[1]+" 23:59");
                            } catch (ParseException ex) {
                                LOGGER.log(Level.WARNING,"Error parsing after.");
                            }
                            break;  
                        case "downloadsfolder" :
                            //switch backslash to forward slash and remove quotations
                            downloads = lineData[1].replace("\\", "/").replaceFirst("^~",System.getProperty("user.home"));
                            break;                      
                    }
                }
                bufferedReader.close();
            } catch(IOException Ex) {
                LOGGER.log(Level.SEVERE,"Failed to read config file, check that the path is correct.");
            }
        }

    }    
    
    private boolean containsVenue(String echoVenue, List<String> filterVenues) {
        for(String searchVenue : filterVenues) {
            if(searchVenue.equals("")) {
                if(echoVenue.equals(searchVenue)) return true;
            } else if(echoVenue.toLowerCase().contains(searchVenue.toLowerCase())) return true;
        }
        return false;
    }    
    
    public ArrayList<Echo> filterEchoes(Boolean downloaded) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        data.courseEchoes.keySet().stream()
                .forEach((k) -> filteredEchoes.addAll(filterEchoList(data.courseEchoes.get(k), downloaded)));
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
    
    private List<Echo> filterEchoList(List<Echo> echoes, Boolean downloaded) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();  
        echoes.stream()
                .filter((e) -> (downloaded == null || downloaded.equals(e.downloaded)))
                .filter((e) -> (excludeUnits.isEmpty() || !excludeUnits.contains(e.unit)))
                .filter((e) -> (excludeVenues.isEmpty() || !containsVenue(e.venue, excludeVenues)))
                .filter((e) -> (before == null || e.date.before(before)))
                .filter((e) -> (after == null || e.date.after(after)))                        
                .filter((e) -> (!filteredEchoes.contains(e)))
                .forEach(filteredEchoes::add);
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
    
    public void printFiltered() {
        ArrayList<Echo> echoList = filterEchoes(null);
        LOGGER.log(Level.INFO, "{0} lecture(s) found matching the filter.\n", echoList.size());
        for (Echo e : echoList) {
            String downloaded = "";
            if (e.downloaded) {
                downloaded = " [Downloaded]";
            }
            String venue = "N/A";
            if(e.venue != null) {
                String[] venueList = e.venue.split(",");
                venue = venueList[venueList.length - 1].split(" \\[")[0].trim();
            }
            String[] urlParts = e.url.split("\\.");
            String summary = String.format("%s - %s @ %s [%s]%s", e.unit, e.title, venue, urlParts[urlParts.length-1], downloaded);
            System.out.println(summary);
        }
    }

    public void setEchoesDownloaded(Boolean setValue) {
        ArrayList<Echo> echoList = filterEchoes(null);
        for(Echo e : echoList) {
            e.downloaded = setValue;
        }       
        data.save();
        LOGGER.log(Level.INFO,"Lectures matching filter have been set [Downloaded] = {0}\n", setValue); 
    }    
        
}
