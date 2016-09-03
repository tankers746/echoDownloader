/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.*;

/**
 *
 * @author Tom
 */
public class Fetcher {
    private static final Logger LOGGER = Logger.getLogger(Fetcher.class.getName());
    final String echoBase = "http://prod.lcs.uwa.edu.au:8080";
    Config c;
    BlackboardConnector bc;

    Fetcher(Config config, String username, String password) {
        c = config;
        bc = new BlackboardConnector(username, password, new WebClient());   
    }
    
    public void fetch() {
        if(bc.loginLMS()) {        
            if (c.data.units.isEmpty()) { //checks if there is a saved table
                LOGGER.log(Level.WARNING, "Units haven't been loaded yet\n"); 
                bc.loadUnits(c.data);
            }

            LOGGER.log(Level.INFO, "Fetching lectures..."); 
            //Loop over all the units found on LMS
            for (String unit : c.data.units.keySet()) {
                //Dont fetch units found in excludeunits
                if(!c.excludeUnits.contains(unit.toUpperCase())) {
                    String courseID = c.data.units.get(unit);
                    ArrayList<Echo> fetched = fetchEchoes(courseID);    
                    if (fetched.isEmpty()) {
                        LOGGER.log(Level.INFO, "No new {0} lectures to fetch.", unit); 
                    } else {
                       LOGGER.log(Level.INFO, "Fetched {0} new {1} lectures.", new Object[] {fetched.size(), unit});                    
                    }
                    c.data.courseEchoes.get(courseID).addAll(fetched);
                }
            }
            LOGGER.log(Level.INFO,"Finished fetching lectures.\n");
            c.data.save();
        } else {
            LOGGER.log(Level.SEVERE, "Unable to fetch lectures without LMS login.\n");    
        }
        bc.webClient.close();
    }    

    public ArrayList<Echo> fetchEchoes(String courseID) {
        ArrayList<Echo> echoes = c.data.courseEchoes.get(courseID);
        ArrayList<Echo> fetchedEchoes = new ArrayList<>();
        //check if there are currently fetched echoes for that section
        if (echoes == null) {
            echoes = new ArrayList<>();
            c.data.courseEchoes.put(courseID, echoes);
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
        long t = System.currentTimeMillis();
        JSONObject obj = null; 
        //First we've gotta load the echo system through blackboard so we are authenticated and save the sectionid
        bc.webClient.getOptions().setJavaScriptEnabled(true);  
        try {
            HtmlPage authenticatedEchoes= bc.webClient.getPage(bc.lms + "/webapps/osc-BasicLTI-BBLEARN/window.jsp?course_id=" + courseID + "&id=lectur");
            int sectionID = Integer.parseInt(authenticatedEchoes.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0]);
            //then we disable javascript to speed things up
            bc.webClient.getOptions().setJavaScriptEnabled(false);   
            //next we load the echoes again to get the sectionID that is used in the api
            HtmlPage page = bc.webClient.getPage(echoBase + "/ess/portal/section/" + sectionID);
            String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
            UnexpectedPage json = bc.webClient.getPage(echoBase + "/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999");
            obj = new JSONObject(json.getWebResponse().getContentAsString()); 
        } catch(IOException Ex) {
            LOGGER.log(Level.WARNING, "Failed to get data from the API.");
        } 
        LOGGER.log(Level.FINE, "Getting JSON data for {0} took {1} ms", new Object[] {courseID, System.currentTimeMillis() - t});   
        return obj;
    }    
    
    public ArrayList<Echo> parseEchoes(String courseID, JSONObject section) {
        ArrayList<Echo> parsedEchoes = new ArrayList<>();
        //get a list of all of the previously fetched unique echo IDs
        List<String> UUIDs = c.data.courseEchoes.get(courseID).stream().map(Echo::getUUID).collect(Collectors.toList());
        
        String unit = section.getJSONObject("course").getString("identifier");
        String unitName = section.getJSONObject("course").getString("name").split("\\[")[0];
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");

        //fetch the new lectures
        for (int i = 0; i < presentations.length(); i++) {
            long t = System.currentTimeMillis();
            String uuid = presentations.getJSONObject(i).getString("uuid");
            LOGGER.log(Level.FINE, "Loading echo data for UUID = {0}.", uuid); 

            //check if the lecture has already been fetched
            if(UUIDs.contains(uuid)) {
                continue;
            }
            Echo e = new Echo();
            e.uuid = uuid;
            
            //loads the contentDir && streamDir for use in later steps
            if(!loadPresentationDirs(e)) {
                continue;
            }
            
            e.courseID = courseID;
            e.duration = presentations.getJSONObject(i).getLong("durationMS");
            e.unit = unit.toUpperCase();
            e.unitName = unitName;
            e.episode = presentations.length() - i;            
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");                
                e.date = sdf.parse(presentations.getJSONObject(i).getString("startTime"));
            } catch (ParseException ex) {
                LOGGER.log(Level.WARNING, "Error parsing date."); 
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(e.date);            
            e.name = String.format("%tB %te%s (%tA)", e.date, e.date, getDateSuffix(cal.get(Calendar.DAY_OF_MONTH)), e.date);            
            
            //Check that there are thumbnails for that lecture, if there isn't then there is no video component to the lecture          
            JSONArray thumbnails = presentations.getJSONObject(i).getJSONArray("thumbnails");
            
            //get a low thumbnail url
            if(thumbnails.length() > 0) { 
                int k;
                for (k = 0; k < thumbnails.length(); k++) {
                    if (thumbnails.getString(k).contains("low")) {
                        break;
                    }
                }
                e.thumbnail = thumbnails.getString(k);
            }
            
            e.url = getDownloadURL(e);
            e.venue = getVenue(e);
            parsedEchoes.add(e);
            LOGGER.log(Level.FINE, "Total parse time for {0} is {1} ms", new Object[] {e.name, System.currentTimeMillis() - t});             
        }
        return parsedEchoes;
    }
    
    public String getDownloadURL(Echo e) {
        long t = System.currentTimeMillis();
        String download;
        //If there is no thumbnail then we can only download the audio version
        if(e.thumbnail != null) {
            //Check if there is a downloadable m4v or use m3u8 playlist
            int responseCode = 404;
            String audiovga = e.contentDir + "audio-vga.m4v";
            try {
                URL u = new URL(audiovga);
                HttpURLConnection huc =  (HttpURLConnection) u.openConnection(); 
                huc.setRequestMethod("HEAD");
                responseCode = huc.getResponseCode();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Failed accessing {0} for {1}", new Object[] {audiovga, e.name});
            }

            //If the downloadable m4v exists use that, if not we have to use the m3u8 playlist
            if(responseCode == HttpURLConnection.HTTP_OK) {
                download = audiovga;
            } else {
                download = e.streamDir;
            }
        //We can only download the audio file for the lecture
        } else {
            download = e.contentDir + "audio.mp3";
        }
        LOGGER.log(Level.FINE, "Got download URL for {0} in {1} ms {2}", new Object[] {e.name, System.currentTimeMillis() - t, download}); 
        return download;
    }

    public boolean loadPresentationDirs(Echo e) {
        long t = System.currentTimeMillis();        
        try {
            HtmlPage presentation = bc.webClient.getPage(echoBase + "/ess/echo/presentation/" + e.uuid);
            LOGGER.log(Level.FINE, "Loading {0} took {1} ms {2}", new Object[] {presentation.getUrl(), presentation.getWebResponse().getLoadTime()}); 
            String requestURL = presentation.getElementsByTagName("iframe").get(0).getAttribute("src");
            List<NameValuePair> params = URLEncodedUtils.parse(new URI(requestURL), "UTF-8");
            for (NameValuePair param : params) {
                switch(param.getName().toLowerCase()) {
                    case "contentdir" :
                        e.contentDir = param.getValue();
                        break;
                    case "streamdir" :
                        URI stream = new URI(param.getValue());
                        StringBuilder sb = new StringBuilder();
                        sb.append("http://")
                            .append(stream.getHost())
                            .append(":1935") //rtmp is port 1935
                            .append(stream.getPath())
                            .append("mp4:audio-vga-streamable.m4v/playlist.m3u8");
                        e.streamDir = sb.toString();                        
                        break;
                }
            }
        } catch(IOException | FailingHttpStatusCodeException | URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "Error loading presentation URLs for UUID {0}.", e.uuid); 
        }
        LOGGER.log(Level.FINE, "Loaded presentation dirs in {0}", System.currentTimeMillis() - t); 
        return (e.contentDir != null && e.streamDir != null);
    }

    public static String getVenue(Echo e) {
        long t = System.currentTimeMillis();    
        String venue = null;
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        //get echo details from the presentation.xml
        try {
            InputStream in = new URL(e.contentDir + "presentation.xml").openStream();
            XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
            streamReader.nextTag(); // Advance to session-info
            streamReader.nextTag(); // Advance to presentation-properties             
            while (streamReader.hasNext()) {
                if (streamReader.isStartElement() && streamReader.getLocalName().equals("location")) {
                    break;
                }
                streamReader.next();
            }
            venue = streamReader.getElementText();
            in.close();
        } catch (IOException | XMLStreamException ex) {
            LOGGER.log(Level.WARNING, "Error loading venue for {0}.", e.name); 
        }
        LOGGER.log(Level.FINE, "Loaded venue for {0} in {1} ms", new Object[] {e.name, System.currentTimeMillis() - t}); 
        return venue;
    }    
    
    private static String getDateSuffix(int day) { 
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
}
