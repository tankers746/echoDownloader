/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lecturedownloader;


import com.google.common.base.Predicate;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.json.*;


/**
 *
 * @author Tom
 */
public class LectureDownloader {

private static final int YEAR = 2016;
private static final int TIMEOUT = 20;
static WebDriver driver;
static WebDriverWait wait;
HashMap<String, Unit> units;

    LectureDownloader() throws Exception {
        units = new HashMap<>();
        units = (HashMap<String, Unit>) getObject("units.ser");
        if (units == null){ //checks if there is a saved table
            units = new HashMap<>();
            saveObject("units.ser", units);
        }        
        
        //getUnits();
        //printUnits();
        
        
        String course = "CITS3001";
        fetchLectures(units.get(course).years.get(2016).semester1);
        for(int i = 0; i < units.get(course).years.get(2016).semester1.echoes.size(); i++) {
            Echo lecture = units.get(course).years.get(2016).semester1.echoes.get(i);

            
            /*
            if(lecture[0].equals(lectures.get(i+1)[0])) {
                if(Long.parseLong(lecture[2]) < Long.parseLong(lectures.get(i+1)[2])) {
                    lecture = lectures.get(i+1);  
                }
                i++;
            }
            */
            //File f = new File(course + " - " + lecture[0] + ".m4v");
            //URL u = new URL(lecture[1]);
            //FileUtils.copyURLToFile(u, f);
            
            System.out.println(lecture.date + " - " + lecture.fileSize + " bytes - " + lecture.venue + " - " + lecture.thumbnail);
       }

        
        
    }


    public void saveObject(String filename, Object h){
    	try{
    		FileOutputStream fos = new FileOutputStream(filename);
    		ObjectOutputStream oos = new ObjectOutputStream(fos);
    		oos.writeObject(h);
    		oos.close();
    		fos.close();
    	} catch(Exception e){
                e.printStackTrace();
    		System.err.println("Error saving hash map");
    	}
    }
    
    public Object getObject(String filename){
    	Object obj = null;
    	try{
    		FileInputStream fis = new FileInputStream(filename);
    		ObjectInputStream ois = new ObjectInputStream(fis);
    		obj = ois.readObject();
    		ois.close();
    		fis.close();
    	} catch(Exception e){
                //e.printStackTrace();
    		System.err.println("Error reading hash table");
    	}
    	return obj;
    }        

    public static void startDriver() {
        DesiredCapabilities caps = new DesiredCapabilities();
        String[] phantomArgs = new  String[] {
            "--webdriver-loglevel=NONE"
        };
        caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, phantomArgs);        
        caps.setJavascriptEnabled(true);
        caps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, "phantomjs.exe");
        driver = new PhantomJSDriver(caps);
        wait = new WebDriverWait(driver, TIMEOUT);
    }
    
    public void printUnits() {
        for (String unit : units.keySet()) {
           Unit u = units.get(unit);
           System.out.println("Semester 1: " + u.years.get(2016).semester1.sectionID + " Semester 2: " + u.years.get(2016).semester2.sectionID); 
           /* 
           Unit u = new Unit(unit);
           u.years.put(2016, new Year(2016));
           u.years.get(2016).semester1.sectionID = unitsOld.get(unit)[0];
           u.years.get(2016).semester2.sectionID = unitsOld.get(unit)[1];
           units.put(unit, u);          
           System.out.println("Semester 1: " + u.years.get(2016).semester1.sectionID + " Semester 2: " + u.years.get(2016).semester2.sectionID); 
            */
        }
        //saveObject("units.ser", units);
    }
    
    public void fetchLectures(Semester sem) throws Exception {
        startDriver(); 

        int amount = 999;
        //Load the page to create a session so we can use the API & get the api section id
        driver.get("http://prod.lcs.uwa.edu.au:8080/ess/portal/section/"+sem.sectionID);  
        WebElement iframe = driver.findElements(By.tagName("iframe")).get(0);
        String apiSectionID = iframe.getAttribute("src").split("/section/")[1].split("\\?api")[0];

        //parse json data from the echo api
        driver.get("http://prod.lcs.uwa.edu.au:8080/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999" + amount);
        JSONObject obj = new JSONObject(driver.findElement(By.tagName("body")).getText());
        JSONObject section = obj.getJSONObject("section");
        String unit = section.getJSONObject("course").getString("identifier");
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");
        
        //work out how many new lectures we need to fetch
        int totalEchoes = section.getJSONObject("presentations").getInt("totalResults");
        int newEchoes = totalEchoes - sem.echoes.size();
        
        
        
        //load the server so we can grab the filesizes
        driver.get("http://media.lcs.uwa.edu.au");
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        
        for (int i = 0; i < newEchoes; i++)
        {
            Echo e = new Echo();
            
            //find a low thumbnail            
            JSONArray thumbnails = presentations.getJSONObject(i).getJSONArray("thumbnails");
            int k;
            for(k = 0; k<thumbnails.length(); k++) {
                if(thumbnails.getString(k).contains("low")) break;
            }
            final String thumbnail = thumbnails.getString(k);
            String echoContent = thumbnail.split("synopsis")[0];
            e.url = echoContent + "audio-vga.m4v";
            e.week = presentations.getJSONObject(i).getInt("week");
            e.date = presentations.getJSONObject(i).getString("startTime");            
            //make a new thread for downloading thumbnails
            Thread thread1 = new Thread () {
                public void run () {
                    String thumb = Math.abs((int)(unit+e.date+thumbnail.split("/low/")[1]).hashCode()) + ".jpg";
                    try {  
                        FileUtils.copyURLToFile(new URL(thumbnail), new File("thumbs/" + thumb));
                        e.thumbnail = thumb;
                    } catch (IOException ex) {
                        Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };            

            //make another thread for downloading xmls and getting filesize
            Thread thread2 = new Thread () {
                public void run () {                
                    //get echo details from the presentation.xml
                    try {
                        InputStream in = new URL(echoContent + "presentation.xml").openStream();
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
                        in.close();                         
                    } catch (Exception ex) {
                        Logger.getLogger(LectureDownloader.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    //get the filesize for the echo
                    e.fileSize = (long)((JavascriptExecutor) driver).executeAsyncScript(
                        "var callback = arguments[arguments.length - 1];" +
                        "var url = '" + e.url + "';" +
                        "var xhr = new XMLHttpRequest();" +
                        "xhr.open('HEAD', url, true);" +
                        "xhr.onreadystatechange = function() {" +
                        "    callback(parseInt(xhr.getResponseHeader('Content-Length')));" +
                        "};" +
                        "xhr.send();");            
                }

            }; 
            // Start thumbnail and xml downloads
            thread1.start();
            thread2.start();

            // Wait for them both to finish
            thread1.join();
            thread2.join();
            
            sem.echoes.add(e);
        }
        saveObject("units.ser", units);
        driver.quit();
        System.out.println("Fetched " + newEchoes + " new lectures.");
        //return echoList;
    }    
    
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
                if(units.containsKey(unit)) {
                    int[] sectionIDs = units.get(unit);
                    sectionIDs[semester] = sectionID;
                    units.put(unit, sectionIDs);
                } else {
                    int[] sectionIDs = new int[2];
                    sectionIDs[semester] = sectionID;
                    units.put(unit, sectionIDs);   
                }
                saveHashTable();                
            }
        }
        driver.quit();
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        LectureDownloader ld = new LectureDownloader();
        

    }  
    
}
