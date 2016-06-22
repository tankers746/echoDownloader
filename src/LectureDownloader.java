/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    HashMap<String, int[]> units;
    Data d;

    LectureDownloader() {

        d = (Data) getObject("data.ser");
        if (d == null) { //checks if there is a saved table
            d = new Data();
            saveObject("data.ser", d);
        }

        //load the units hashMap (this contains all of the units and the sections, this needs updating every year)
        units = (HashMap<String, int[]>) getObject("units.ser");
        if (units == null) { //checks if there is a saved table
            units = new HashMap<>();
            saveObject("units.ser", units);
        }
    }

    public void setDownloads(String downloads) {
        d.downloads = downloads;
        saveObject("data.ser", d);
    }

    public void setEchoBase(String echoBase) {
        d.echoBase = echoBase;
        saveObject("data.ser", d);
    }

    public void downloadEcho(Echo e) {
        File f = new File(d.downloads + "/" + e.unit + "/" + e.name + ".m4v");
        try {
            System.out.println("Downloading lecture...");
            FileUtils.copyURLToFile(new URL(e.url), f);
            System.out.println("Downloaded '" + f.getName() + "' to " + f.getParent());
            e.downloaded = true;
            saveObject("data.ser", d);
        } catch (IOException ex) {
            System.err.println("Failed to download " + f.getName());
        }
    }

    public void downloadEchoes(List<Echo> echoes) {
        int queue = echoes.size();
        for (Echo e : echoes) {
            System.out.println(queue + " lectures in the download queue.");
            downloadEcho(e);
        }
    }

    public ArrayList<Echo> fetchFiltered(Filter f) {
        ArrayList<Echo> fetched = new ArrayList<>();
        for (int sectionID : f.sectionIDs) {
            
            fetched.addAll(fetchEchoes(sectionID));
        }
        return f.filterEchoList(fetched);
    }

    public final void saveObject(String filename, Object h) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(h);
            oos.close();
            fos.close();
        } catch (IOException e) {
            System.err.println("Error saving " + filename);
        }
    }

    public final Object getObject(String filename) {
        Object obj = null;
        try {
            FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            obj = ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reading " + filename);
        }
        return obj;
    }

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
    
    public void setAllEchoesDownloaded(HashMap<Integer, ArrayList<Echo>> s) {
        for(int key : s.keySet()) {
            ArrayList<Echo> echoes = s.get(key);
            for(Echo e : echoes) {
                e.downloaded = true;
            }
        }
        saveObject("data.ser", d);        
        System.out.println("All lectures have been set to [Downloaded]"); 
    }

    public List<Echo> fetchEchoes(int sectionID) {
        System.out.println("Fetching lectures...");
        ArrayList<Echo> echoes = d.sections.get(sectionID);
        //check if there are currently fetched echoes for that section
        if (echoes == null) {
            echoes = new ArrayList<>();
            d.sections.put(sectionID, echoes);
        }
        WebClient webClient = new WebClient();
        webClient.getOptions().setJavaScriptEnabled(false);

        UnexpectedPage json = null;
        try {
            HtmlPage page = webClient.getPage(d.echoBase + "/ess/portal/section/" + sectionID);
            String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
            json = webClient.getPage(d.echoBase + "/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999");
        } catch(IOException ex) {
            System.err.println("Failed to contanct the API.");
            return echoes;
        }
        //parse json data from the echo api
        JSONObject obj = new JSONObject(json.getWebResponse().getContentAsString());
        JSONObject section = obj.getJSONObject("section");
        String unit = section.getJSONObject("course").getString("identifier");
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");
        webClient.close();

        //work out how many new lectures we need to fetch
        final int totalResults = section.getJSONObject("presentations").getInt("totalResults");
        final int oldEchoes = d.sections.get(sectionID).size();
        int newEchoes = totalResults - oldEchoes;

        if (newEchoes == 0) {
            System.out.println("No new lectures to fetch.");
            return new ArrayList<>();
        }

        //fetch the new lectures
        for (int i = 0; i < newEchoes; i++) {
            Echo e = new Echo();

            //find a low thumbnail            
            JSONArray thumbnails = presentations.getJSONObject(i).getJSONArray("thumbnails");
            int k;
            for (k = 0; k < thumbnails.length(); k++) {
                if (thumbnails.getString(k).contains("low")) {
                    break;
                }
            }
            //temporarily store the thumbnail url so it can be downloaded in the next step
            e.thumbnail = thumbnails.getString(k);
            String echoContent = e.thumbnail.split("synopsis")[0];
            e.url = echoContent + "audio-vga.m4v";
            e.sectionID = sectionID;
            e.duration = presentations.getJSONObject(i).getLong("durationMS");
            e.unit = unit;
            String title = presentations.getJSONObject(i).getString("title");
            String lectureNumber = title.substring(title.length() - 5, title.length() - 3);
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
            e.name = String.format("%s - [%tm-%td] [L%s]%s", e.unit, e.date, e.date, lectureNumber, rep);

            d.sections.get(sectionID).add(e);
        }

        //make a new thread for downloading thumbnails
        Thread thread1 = new Thread() {
            public void run() {
                long t = System.currentTimeMillis();
                downloadThumbnails(d.sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Downloading thumbnails took " + (System.currentTimeMillis() - t) + " ms");
            }
        };

        //make another thread for getting filesizes
        Thread thread2 = new Thread() {
            public void run() {
                long t = System.currentTimeMillis();
                loadFileSizes(d.sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Loading filesizes took " + (System.currentTimeMillis() - t) + " ms");
            }
        };

        //make another thread for downloading xmls
        Thread thread3 = new Thread() {
            public void run() {
                long t = System.currentTimeMillis();
                loadVenues(d.sections.get(sectionID), oldEchoes, totalResults);
                System.out.println("Loading venues took " + (System.currentTimeMillis() - t) + " ms");
            }
        };

        // Start thumbnail and xml downloads
        thread1.start();
        thread2.start();
        thread3.start();

        try {
            // Wait for them both to finish
            thread1.join();
            thread2.join();
            thread3.join();            
        } catch (InterruptedException ex) {
            System.err.println("Failed to fetch echo information.");
        }


        saveObject("data.ser", d);
        System.out.println("Fetched " + newEchoes + " new " + unit + " lectures.\n");
        return echoes.subList(echoes.size() - newEchoes, echoes.size());
    }

    public void downloadThumbnails(ArrayList<Echo> echoes, int startIndex, int finishIndex) {
        //iterate over all the newly fetched echoes
        for (int k = startIndex; k < finishIndex; k++) {
            Echo e = echoes.get(k);
            String thumb = Math.abs((int) (e.unit + e.date + e.thumbnail.split("/low/")[1]).hashCode()) + ".jpg";
            try {
                FileUtils.copyURLToFile(new URL(e.thumbnail), new File("thumbs/" + thumb));
                e.thumbnail = thumb;
            } catch (IOException ex) {
                Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
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
    }

    public void loadVenues(ArrayList<Echo> echoes, int startIndex, int finishIndex) {
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
                while (streamReader.hasNext()) {
                    if (streamReader.isStartElement() && streamReader.getLocalName().equals("location")) {
                        break;
                    }
                    streamReader.next();
                }
                e.venue = streamReader.getElementText();
                //add the venue code to the name so we don't end up with duplicate filenames
                e.name = e.name + " " + "[" + e.venue.split("\\[")[1];
                if (!d.venues.contains(e.venue)) {
                    d.venues.add(e.venue);
                }
                in.close();
            } catch (Exception ex) {
                Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        saveObject("data.ser", d);
    }
    
    public void printLectures(ArrayList<Echo> echoes) {
        for (Echo e : echoes) {
            String downloaded = "";
            if (e.downloaded) {
                downloaded = " [Downloaded]";
            }
            System.out.println(e.name + " " + e.venue + downloaded);
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
