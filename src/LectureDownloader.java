/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;

import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.io.FileUtils;
import org.json.*;



/**
 *
 * @author Tom
 */
public class LectureDownloader {

private static final int YEAR = 2016;

HashMap<Integer, ArrayList<Echo>> sections;
HashMap<String, int[]> units;
ArrayList<String> venues;

    LectureDownloader() throws Exception {
        
        //load the sections hashMap (this contains all of the units and the sections, this needs updating every year)
        units = (HashMap<String, int[]>) getObject("units.ser");
        if (units == null){ //checks if there is a saved table
            units = new HashMap<>();
            saveObject("units.ser", units);
        }  
        
        //load all of the echoes
        sections = (HashMap<Integer, ArrayList<Echo>>) getObject("sections.ser");
        if (sections == null){ //checks if there is a saved table
            sections = new HashMap<>();
            saveObject("sections.ser", units);
        }   
        
        //load all of the venues
        venues = (ArrayList<String>) getObject("venues.ser");
        if (venues == null){ //checks if there is a saved table
            venues = new ArrayList<>();
            saveObject("venues.ser", units);
        }         
        
            
        
        //getUnits();
        //printUnits();
        
        
        String course = "ENSC1002";
        int section = units.get(course)[0];
        //fetchEchoes(section, true);
        Integer[] sectionIDs = {units.get("MATH1001")[0], units.get("CITS3001")[0], units.get("ENSC1002")[0]};
        String[] v = {"Crawley: PHYSICS, Ross Lecture Theatre [245:G41]", "Crawley: CHEMISTRY - LECTURE THEATRES, Tattersall Lecture Theatre [210:G106]"};
        
        ArrayList<Echo> filteredEchoes = filterEchoes(sectionIDs, v, null, false);
        
        for(Echo lecture : filteredEchoes) {
            System.out.println(lecture.title + " - " + lecture.date.toString() + " - " + lecture.fileSize + " bytes - " + lecture.venue + " - " + lecture.thumbnail);
        }
        for(String venue : venues) System.out.println(venue);
        
    }


    public final void saveObject(String filename, Object h){
    	try{
    		FileOutputStream fos = new FileOutputStream(filename);
    		ObjectOutputStream oos = new ObjectOutputStream(fos);
    		oos.writeObject(h);
    		oos.close();
    		fos.close();
    	} catch(Exception e){
    		System.err.println("Error saving " + filename);
    	}
    }
    
    public final Object getObject(String filename){
    	Object obj = null;
    	try{
    		FileInputStream fis = new FileInputStream(filename);
    		ObjectInputStream ois = new ObjectInputStream(fis);
    		obj = ois.readObject();
    		ois.close();
    		fis.close();
    	} catch(IOException | ClassNotFoundException e){
    		System.err.println("Error reading " + filename);
    	}
    	return obj;
    }        

    
    /*
    public void printUnits() {
        for (String unit : unitsOld.keySet()) {
           //Unit u = units.get(unit);
           //System.out.println("Semester 1: " + u.years.get(2016).semester1.sectionID + " Semester 2: " + u.years.get(2016).semester2.sectionID); 
           int[] s = new int[2];
           s[0] = unitsOld.get(unit)[0];
           s[1] = unitsOld.get(unit)[1];
           sections.put(unit, s);          
           System.out.println("Semester 1: " + s[0] + " Semester 2: " + s[1]); 
           
        }
        saveObject("sections.ser", sections);
    }*/
    
    public ArrayList<Echo> filterEchoes(Integer[] sectionIDs, String[] venues, Boolean downloaded, Boolean repeats) {
        ArrayList<Echo> filteredEchoes = new ArrayList<>();
        //if no sectionIDs are specified, use fetch them all
        if(sectionIDs == null) {
            Set<Integer> keys = sections.keySet();
            sectionIDs = keys.toArray(new Integer[keys.size()]);
        }
        for(int sectionID : sectionIDs) {
            ArrayList<Echo> echoes = sections.get(sectionID);
            if(echoes == null) continue;
            for(Echo e : echoes) {
                if(venues != null && !Arrays.asList(venues).contains(e.venue)) {
                    continue;
                }
                if(downloaded != null && !downloaded.equals(e.downloaded)) {
                    continue;
                }
                if(repeats != null && !repeats.equals(e.title.toLowerCase().contains("repeat"))) {
                    continue;
                }
                if(!filteredEchoes.contains(e)) filteredEchoes.add(e);
            }
        }
        Collections.sort(filteredEchoes, Collections.reverseOrder());
        return filteredEchoes;
    }
    
    public void fetchEchoes(int sectionID, boolean repeats) throws Exception {
        //check if there are currently fetched echoes for that section
        if(!sections.containsKey(sectionID)) {
            sections.put(sectionID, new ArrayList<Echo>());
        }
        WebClient webClient = new WebClient();
        webClient.getOptions().setJavaScriptEnabled(false);

        HtmlPage page = webClient.getPage("http://prod.lcs.uwa.edu.au:8080/ess/portal/section/"+sectionID);
        String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
        UnexpectedPage json = webClient.getPage("http://prod.lcs.uwa.edu.au:8080/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999");
        
        //parse json data from the echo api
        JSONObject obj = new JSONObject(json.getWebResponse().getContentAsString());
        JSONObject section = obj.getJSONObject("section");
        String unit = section.getJSONObject("course").getString("identifier");
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");
        webClient.close();
        
        //work out how many new lectures we need to fetch
        final int totalResults = section.getJSONObject("presentations").getInt("totalResults");
        final int oldEchoes = sections.get(sectionID).size();
        int newEchoes = totalResults - oldEchoes;
        
        if(newEchoes == 0) {
            System.out.println("No new lectures to fetch.");
            return;
        }
        
        //fetch the new lectures
        for (int i = 0; i < newEchoes; i++)
        {
            Echo e = new Echo();

            //find a low thumbnail            
            JSONArray thumbnails = presentations.getJSONObject(i).getJSONArray("thumbnails");
            int k;
            for(k = 0; k<thumbnails.length(); k++) {
                if(thumbnails.getString(k).contains("low")) break;
            }
            //temporarily store the thumbnail url so it can be downloaded in the next step
            e.thumbnail = thumbnails.getString(k);
            String echoContent = e.thumbnail.split("synopsis")[0];
            e.url = echoContent + "audio-vga.m4v";
            e.week = presentations.getJSONObject(i).getInt("week");
            e.duration = presentations.getJSONObject(i).getLong("durationMS");
            e.title = presentations.getJSONObject(i).getString("title").toLowerCase();
            e.unit = unit;
            String startTime = presentations.getJSONObject(i).getString("startTime");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            e.date = sdf.parse(startTime);
            sections.get(sectionID).add(e);
        }
 
        
        //make a new thread for downloading thumbnails
        Thread thread1 = new Thread () {
            public void run () {
                long t = System.currentTimeMillis();                
                downloadThumbnails(sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Downloading thumbnails took " + (System.currentTimeMillis() - t) + " ms"); 
            }
        };            

        //make another thread for getting filesizes
        Thread thread2 = new Thread () {
            public void run () {        
                long t = System.currentTimeMillis();
                loadFileSizes(sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Loading filesizes took " + (System.currentTimeMillis() - t) + " ms");  
                }
        };
        
        //make another thread for downloading xmls
        Thread thread3 = new Thread () {
            public void run () {       
                long t = System.currentTimeMillis();                
                loadVenues(sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Loading venues took " + (System.currentTimeMillis() - t) + " ms"); 
                }
        };         
        
        // Start thumbnail and xml downloads
        thread1.start();
        thread2.start();
        thread3.start();

        // Wait for them both to finish
        thread1.join();
        thread2.join();
        thread3.join();


        saveObject("sections.ser", sections);
        System.out.println("Fetched " + newEchoes + " new lectures.");
    }

    public void downloadThumbnails(ArrayList<Echo> echoes, int startIndex, int finishIndex) {
        //iterate over all the newly fetched echoes
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);
            String thumb = Math.abs((int)(e.unit+e.date+e.thumbnail.split("/low/")[1]).hashCode()) + ".jpg";
            try {  
                FileUtils.copyURLToFile(new URL(e.thumbnail), new File("thumbs/" + thumb));
                e.thumbnail = thumb;
            } catch (IOException ex) {
                Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void loadFileSizes(ArrayList<Echo> echoes, int startIndex, int finishIndex)  {
        WebClient webClient = new WebClient();
        //very important: getting the filesize does not work with out setting the AJAX controller   
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        //load the server so we can grab the filesizes
        HtmlPage media = null;
        try {
            media = webClient.getPage("http://media.lcs.uwa.edu.au/echocontent");
        } catch (IOException ex) {
            Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
        }
        //iterate over all the newly fetched echoes        
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);
            
            //politely ask the server for the filesize
            e.fileSize = Long.parseLong(media.executeJavaScript(
                "var size = 0;" +
                "var url = '" + e.url + "';" +
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('HEAD', url, true);" +
                "xhr.onreadystatechange = function() {" +
                "    size = xhr.getResponseHeader('Content-Length');" +
                "};" +
                "xhr.send();size;").getJavaScriptResult().toString());    
        }
        webClient.close();
    }
    
    public void loadVenues(ArrayList<Echo> echoes, int startIndex, int finishIndex)  {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();   
        //iterate over all the newly fetched echoes        
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);
            //get echo details from the presentation.xml
            try {
                InputStream in = new URL(e.url.replace("audio-vga.m4v", "presentation.xml")).openStream();
                XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);                     
                streamReader.nextTag(); // Advance to session-info
                streamReader.nextTag(); // Advance to presentation-properties             
                while(streamReader.hasNext()) {
                    if(streamReader.isStartElement() && streamReader.getLocalName().equals("location")){      
                        break;
                    }  
                    streamReader.next();
                }
                e.venue = streamReader.getElementText();
                if(!venues.contains(e.venue)) venues.add(e.venue);
                in.close();                         
            } catch (Exception ex) {
                Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        saveObject("venues.ser", venues);
    }    
    
    private static void disableLogs() {
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF); 
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

    }    
    /*
    public void getUnits() throws IOException {
        
        startDriver();   
        int sectionID;
        for(sectionID = 174557; sectionID < 184018; sectionID++) {

            System.out.println("SectionID: " + sectionID);
            driver.get("http://prod.lcs.uwa.edu.au:8080/ess/portal/section/"+sectionID);
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            if(iframes.size() > 0) {
                driver.switchTo().frame(iframes.get(0));               
            } else continue;
            
            WebElement course = null;            
            try {

                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("course-info")));
            } catch(Exception e) {
                System.err.println("Error loading page, restarting driver.");
                driver.quit();                
                startDriver();  
                sectionID -= 1;
                continue;
            }
            if(course != null && course.getText().contains(Integer.toString(YEAR))) {
                System.out.println("Course is: " + course.getText());       
                int semester = 0;
                if(course.getText().contains("semester 1") || course.getText().contains("Semester 1") ) {
                    semester = 0;
                } else if(course.getText().contains("semester 2") || course.getText().contains("Semester 2") ) {
                    semester = 1;
                }                                
                String unit = course.getText().substring(course.getText().length()-9,course.getText().length()-1);
                if(sections.containsKey(unit)) {
                    int[] sectionIDs = sections.get(unit);
                    sectionIDs[semester] = sectionID;
                    sections.put(unit, sectionIDs);
                } else {
                    int[] sectionIDs = new int[2];
                    sectionIDs[semester] = sectionID;
                    sections.put(unit, sectionIDs);   
                }
                saveObject("sections.ser", sections);                
            }
        }
        driver.quit();
    }
    */

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        disableLogs();
        LectureDownloader ld = new LectureDownloader();
        

    }  
    
}
