/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.json.*;

/**
 *
 * @author Tom
 */
public class echoDownloader {

    //units (name, courseID)
    HashMap<String, String> units;
    Data d;
    BlackboardConnector bc;
    WebClient webClient;

    echoDownloader(WebClient wc, BlackboardConnector connector, Data dt, HashMap<String, String> unitData) {
        
        bc = connector;      
        webClient = wc;
        d = dt;
        units = unitData;

        if (units.isEmpty()) { //checks if there is a saved table
            System.out.println("No units found.");
            if(bc != null && webClient != null) {
                loadUnits();
                Data.saveObject("units.ser", units);

                for(String unit : units.keySet()) {
                    System.out.println("Found unit " + unit + " with course ID " + units.get(unit));
                }
            }
        }
        
    }
    
    public static void downloadEcho(Echo e, String downloads, String ffmpeg, boolean verbose) {
        boolean failed = false;                
        String ext = ".m4v";
        boolean audio = false;
        boolean m3u8 = false;
        
        //Check if the file needs to be constructed from a m3u8 playlist
        if(e.url.substring(e.url.length()-4, e.url.length()).equals("m3u8")) {    
            ext = ".mp4";
            m3u8 = true;
        }
        if(e.url.substring(e.url.length()-3, e.url.length()).equals("mp3")) {
            ext = ".mp4";
            audio = true;
        }        
        
        String basePath = downloads + "/" + e.unit + "/";
        String filename =  String.format("%s%s - S01E%02d - %s", basePath, e.unit, e.episode, e.name);
        File f = new File(filename + ext);
        int n = 1;
        while(f.exists()) {
            f = new File(filename + " (" + n++ + ")" + ext);
        }
        
        System.out.println("Downloading '" + f.getName() + "'...");        
        try {
            List<String> args = new ArrayList<>();
            args.add(ffmpeg);
            args.add("-i");
            args.add(e.url);
            
            if(audio) {
                args.add("-f");
                args.add("lavfi");                      
                args.add("-i");
                args.add("color=s=640x480:r=10");  
                args.add("-c:v");
                args.add("libx264");  
                args.add("-c:a");
                args.add("aac");                
                args.add("-shortest");                 
            } else {
                args.add("-c");
                args.add("copy");                
            }
            
            args.add("-metadata");
            args.add("show=" + e.unit + " - " + e.unitName);
            
            args.add("-metadata");
            args.add("title=" + e.name);          
            
            args.add("-metadata");
            args.add("episode_sort=" + e.episode);   

            //media type is tv show for iTunes
            args.add("-metadata");
            args.add("media_type=10");               
            
            if(m3u8) {               
                args.add("-bsf:a");
                args.add("aac_adtstoasc");   
            }
            
            args.add(filename + ext);
            
            new File(basePath).mkdirs();
            Process downloader = new ProcessBuilder(args).redirectErrorStream(true).start();
            
            int exitCode = -1;
            try (BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(downloader.getInputStream(), Charset.defaultCharset()));) {
                String line;
                while ((line = processOutputReader.readLine()) != null) {
                    if(verbose) System.out.println(line);
                }
                exitCode = downloader.waitFor();
            }
            
            if(exitCode == 0) {
                System.out.println("Succesfully downloaded to '" + f.getParent() + "'");
                e.downloaded = true;
            } else failed = true;
        } catch (IOException | InterruptedException ex) {
            failed = true;  
        }
        
        if(failed) {
            f.delete();
            System.out.println("Failed to download '" + f.getName() + "");            
        }
    }

    public static void downloadEchoes(List<Echo> echoes, String downloads, String ffmpeg, boolean verbose) {
        int queue = echoes.size();
        for (Echo e : echoes) {
            System.out.println("\n" + queue-- + " lecture(s) in the download queue.");
            downloadEcho(e, downloads, ffmpeg, verbose);
        }
    }
    
    public void loadUnits() {
        try {
            System.out.println("\nFetching units...");
            long t = System.currentTimeMillis();

            //Load the unit personalisation page to get list of units
            String href = bc.BBHome.getElementById("module:_3_1").getElementsByTagName("a").get(0).getAttribute("href");
            HtmlPage unitPage = webClient.getPage("https://" + bc.BBHome.getUrl().getHost() + "/" + href);
            
            //Iterate over the table of units
            Iterable<DomElement> rows = unitPage.getElementById("blockAttributes_table_jsListFULL_Student_23163_1_body").getChildElements();
            for(DomElement row : rows) {
                //iterate over all of the checkboxes to get the course id
                List<HtmlElement> inputs = row.getElementsByTagName("input");
                for(DomElement input : inputs) {
                    String id = input.getAttribute("id");
                    if(id.contains("course")) {
                        //separate the courseID from the rest of the id, e.g. (id = "amc.showcourseid._12892_1")
                        String title = input.getAttribute("title");
                        units.put(title.substring(0,8) , id.split("\\.")[2]);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
/*
    public void addUnit(int sectionID) {
        System.out.println("Fetching unit...");
        long t = System.currentTimeMillis();
        WebClient webClient = new WebClient();
        webClient.getOptions().setJavaScriptEnabled(false);
        UnexpectedPage json = null;
        try {
            HtmlPage page = webClient.getPage(d.echoBase + "/ess/portal/section/" + sectionID);            
            String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
            json = webClient.getPage(d.echoBase + "/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=1");
        } catch (Exception e) {
            System.err.println("Section does not exist.");
            return;
        }
        //parse json data from the echo api
        JSONObject obj = new JSONObject(json.getWebResponse().getContentAsString());
        JSONObject section = obj.getJSONObject("section");
        String unit = section.getJSONObject("course").getString("identifier");
        String name = section.getString("name").toLowerCase();
        webClient.close();

        int semester = 0;
        if (name.contains("semester 1")) {
            semester = 0;
        } else if (name.contains("semester 2")) {
            semester = 1;
        }

        if (units.containsKey(unit)) {
            units.get(unit)[semester] = sectionID;
        } else {
            int[] sectionIDs = new int[2];
            sectionIDs[semester] = sectionID;
            units.put(unit, sectionIDs);
        }
        System.out.println("Added " + unit + " - Semester " + semester+1);
        saveObject("units.ser", units);
        System.out.println("Fetching unit took " + (System.currentTimeMillis() - t) + " ms");
    }
    */
    
    public static void setEchoesDownloaded(ArrayList<Echo> echoes, Boolean setValue) {
        for(Echo e : echoes) {
            e.downloaded = setValue;
        }       
        System.out.println("\nLectures matching filter have been set [Downloaded] = " + setValue); 
    }
    
    private String getUnitName(String courseID) {
        String unitName = "";
        for(String unit : units.keySet()) {
            if(units.get(unit).equals(courseID)) {
                unitName = unit;
                break;
            }
        }
        return unitName;
    }
    
    public void fetch(Config c) {
        for (String courseID : c.courseIDs) {
            List<Echo> fetched = fetchEchoes(courseID);    
            String unit = getUnitName(courseID);
            if (fetched.isEmpty()) {
                System.out.println("No new " + unit + " lectures to fetch.");
            } else {
                System.out.println("Fetched " + fetched.size() + " new " + unit + " lectures.");                    
            }
            d.courses.get(courseID).addAll(fetched);
        }
    }    

    public ArrayList<Echo> fetchEchoes(String courseID) {
        System.out.println("\nFetching lectures...");
        ArrayList<Echo> echoes = d.courses.get(courseID);
        ArrayList<Echo> fetchedEchoes = new ArrayList<>();
        //check if there are currently fetched echoes for that section
        if (echoes == null) {
            echoes = new ArrayList<>();
            d.courses.put(courseID, echoes);
        }
        //get the JSON data from the API
        JSONObject obj = getAPIData(courseID);
        if(obj != null) {
            JSONObject section = obj.getJSONObject("section");
            fetchedEchoes = parseEchoes(courseID, section);            
        }
        return fetchedEchoes;
    }
    
    private JSONObject getAPIData(String courseID) {
        JSONObject obj = null; 
        //First we've gotta load the echo system through blackboard so we are authenticated and save the sectionid
        webClient.getOptions().setJavaScriptEnabled(true);  
        try {
            HtmlPage authenticatedEchoes= webClient.getPage("https://lms.uwa.edu.au/webapps/osc-BasicLTI-BBLEARN/window.jsp?course_id=" + courseID + "&id=lectur");
            int sectionID = Integer.parseInt(authenticatedEchoes.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0]);
            //then we disable javascript to speed things up
            webClient.getOptions().setJavaScriptEnabled(false);   
            //next we load the echoes again to get the sectionID that is used in the api
            HtmlPage page = webClient.getPage(d.echoBase + "/ess/portal/section/" + sectionID);
            String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
            UnexpectedPage json = webClient.getPage(d.echoBase + "/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999");
            obj = new JSONObject(json.getWebResponse().getContentAsString()); 
        } catch(IOException Ex) {
            System.err.println("Failed to get data from the API.");
        } 
        return obj;
    }    
    
    public ArrayList<Echo> parseEchoes(String courseID, JSONObject section) {
        ArrayList<Echo> parsedEchoes = new ArrayList<>();
        //get a list of all of the previously fetched unique echo IDs
        List<String> UUIDs = d.courses.get(courseID).stream().map(Echo::getUUID).collect(Collectors.toList());

        //http://prod.lcs.uwa.edu.au:8080/ess/client/api/sections/f1289ed4-77e3-434f-9e93-e9e2b8b472ec/presentations/c0aacd58-20f8-45b5-bc54-ee05080c4077/details.json
        
        String unit = section.getJSONObject("course").getString("identifier");
        String unitName = section.getJSONObject("course").getString("name").split("\\[")[0];
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");

        //fetch the new lectures
        for (int i = 0; i < presentations.length(); i++) {
            String uuid = presentations.getJSONObject(i).getString("uuid");
            //check if the lecture has already been fetched
            if(UUIDs.contains(uuid)) {
                continue;
            }
            Echo e = new Echo();
            e.uuid = uuid;

            //Check that there are thumbnails for that lecture, if there isn't then there is no video component to the lecture          
            JSONArray thumbnails = presentations.getJSONObject(i).getJSONArray("thumbnails");
            if(thumbnails.length() > 0) { 
                int k;
                for (k = 0; k < thumbnails.length(); k++) {
                    if (thumbnails.getString(k).contains("low")) {
                        break;
                    }
                }

                e.thumbnail = thumbnails.getString(k);
                e.echoContent = e.thumbnail.split("/synopsis/")[0];

                //Check if there is a downloadable m4v or use m3u8 playlist
                int responseCode = 404;
                String audiovga = e.echoContent + "/audio-vga.m4v";
                try {
                    URL u = new URL(audiovga);
                    HttpURLConnection huc =  (HttpURLConnection) u.openConnection(); 
                    huc.setRequestMethod("HEAD");
                    responseCode = huc.getResponseCode();
                } catch (IOException ex) {
                    Logger.getLogger(echoDownloader.class.getName()).log(Level.SEVERE, null, ex);
                }

                if(responseCode == HttpURLConnection.HTTP_OK) {
                    e.url = audiovga;
                } else {
                    e.url = "http://media.lcs.uwa.edu.au:1935/echo/_definst_/" + e.echoContent.split("/echocontent/")[1] + "/mp4:audio-vga-streamable.m4v/playlist.m3u8";                
                }
            //We can only download the audio file for the lecture
            } else {
                try {
                    HtmlPage audio = webClient.getPage("http://prod.lcs.uwa.edu.au:8080/ess/echo/presentation/" + e.uuid + "/media.mp3");
                    e.url = audio.getElementsByTagName("embed").get(0).getAttribute("src");
                    e.echoContent = e.url.replace("/audio.mp3", "");
                } catch (IOException ex) {
                    Logger.getLogger(echoDownloader.class.getName()).log(Level.SEVERE, null, ex);
                
                }
            }
            
            
            e.courseID = courseID;
            e.duration = presentations.getJSONObject(i).getLong("durationMS");
            e.unit = unit.toUpperCase();
            e.unitName = unitName;
            String title = presentations.getJSONObject(i).getString("title");
            String rep = "";
            if (title.toLowerCase().contains("repeat")) {
                e.repeat = true;
                rep = " [R]";
            }
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");                
                e.date = sdf.parse(presentations.getJSONObject(i).getString("startTime"));
            } catch (ParseException ex) {
                System.err.println("Error parsing date from API."); 
            }
            e.episode = presentations.length() - i;
            Calendar cal = Calendar.getInstance();
            cal.setTime(e.date);            
            e.name = String.format("%tB %te%s (%tA)%s", e.date, e.date, getDateSuffix(cal.get(Calendar.DAY_OF_MONTH)), e.date, rep);

            d.courses.get(courseID).add(e);
            loadVenue(e);
            parsedEchoes.add(e);
        }
        return parsedEchoes;
    }    

    public void loadVenue(Echo e) {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        //get echo details from the presentation.xml
        try {
            InputStream in = new URL(e.echoContent + "/presentation.xml").openStream();
            XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
            streamReader.nextTag(); // Advance to session-info
            streamReader.nextTag(); // Advance to presentation-properties             
            while (streamReader.hasNext()) {
                if (streamReader.isStartElement() && streamReader.getLocalName().equals("location")) {
                    break;
                }
                streamReader.next();
            }
            e.venue = streamReader.getElementText();
            //add the venue code to the name so we don't end up with duplicate filenames
            in.close();
        } catch (Exception ex) {
            Logger.getLogger(echoDownloader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    
    
    private String getDateSuffix(int day) { 
        switch (day) {
            case 1: case 21: case 31:
                   return ("st");

            case 2: case 22: 
                   return ("nd");

            case 3: case 23:
                   return ("rd");

            default:
                   return ("th");
        }
    }    

    /*
    public void downloadThumbnails(ArrayList<Echo> echoes, int startIndex, int finishIndex) {
        //iterate over all the newly fetched echoes
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);
            String thumb = Math.abs((int) (e.unit + e.date + e.thumbnail.split("/low/")[1]).hashCode()) + ".jpg";
            try {
                FileUtils.copyURLToFile(new URL(e.thumbnail), new File("thumbs/" + thumb));
                e.thumbnail = thumb;
            } catch (IOException ex) {
                Logger.getLogger(echoDownloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void loadFileSizes(ArrayList<Echo> echoes, int startIndex, int finishIndex) {
        WebClient webClient = new WebClient();
        //very important: getting the filesize does not work with out setting the AJAX controller   
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        //load the server so we can grab the filesizes
        HtmlPage media = null;
        try {
            String echoContent = echoes.get(0).url.split("echocontent")[0] + "echocontent";
            media = webClient.getPage(echoContent);
        } catch (Exception ex) {
            Logger.getLogger(echoDownloader.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        //iterate over all the newly fetched echoes        
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);

            //politely ask the server for the filesize
            e.fileSize = Long.parseLong(media.executeJavaScript(
                    "var size = 0;"
                    + "var url = '" + e.url + "';"
                    + "var xhr = new XMLHttpRequest();"
                    + "xhr.open('HEAD', url, true);"
                    + "xhr.onreadystatechange = function() {"
                    + "    size = xhr.getResponseHeader('Content-Length');"
                    + "};"
                    + "xhr.send();size;").getJavaScriptResult().toString());
        }
        webClient.close();
    }*/
    
    public static void printLectures(ArrayList<Echo> echoes) {
        System.out.println("\n" + echoes.size() + " lecture(s) found matching the filter.");
        for (Echo e : echoes) {
            String downloaded = "";
            if (e.downloaded) {
                downloaded = " [Downloaded]";
            }
            String[] venueList = e.venue.split(",");
            String venue = venueList[venueList.length - 1].split(" \\[")[0].trim();
            String summary = String.format("%s - %s @ %s%s", e.unit, e.name, venue, downloaded);
            System.out.println(summary);
        }
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
        new Cli(args).parse();       
    }

}
