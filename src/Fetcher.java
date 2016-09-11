/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.gargoylesoftware.htmlunit.util.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.util.Pair;
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
            c.data.units.entrySet()
                    .parallelStream()
                    .filter(entry -> !c.excludeUnits.contains(entry.getKey().toUpperCase()))
                    .forEach(entry -> {
                        String courseID = entry.getValue();
                        List<Echo> fetched = fetchEchoes(courseID);    
                        if (fetched.isEmpty()) {
                            LOGGER.log(Level.INFO, "No new {0} lectures to fetch.", entry.getKey()); 
                        } else {
                           LOGGER.log(Level.INFO, "Fetched {0} new {1} lectures.", new Object[] {fetched.size(), entry.getKey()});                    
                        }
                        c.data.courseEchoes.get(courseID).addAll(fetched);                        
                    });
            
            LOGGER.log(Level.INFO,"Finished fetching lectures.\n");
            c.data.save();
        } else {
            LOGGER.log(Level.SEVERE, "Unable to fetch lectures without LMS login.\n");    
        }
        bc.webClient.close();
    }    

    public List<Echo> fetchEchoes(String courseID) {    
        WebClient wc = new WebClient();
        Iterator<Cookie> i = bc.cookieManager.getCookies().iterator();
        while (i.hasNext()) {
            wc.getCookieManager().addCookie(i.next());
        }
        wc.getOptions().setJavaScriptEnabled(false);   
        wc.getOptions().setCssEnabled(false); 
        wc.getOptions().setAppletEnabled(false);              
        
        ArrayList<Echo> echoes = c.data.courseEchoes.get(courseID);
        List<Echo> fetchedEchoes = new ArrayList<>();
        //check if there are currently fetched echoes for that section
        if (echoes == null) {
            echoes = new ArrayList<>();
            c.data.courseEchoes.put(courseID, echoes);
        }
        //get the JSON data from the API
        Pair<String, JSONObject> apiData = getAPIData(courseID, wc);
        if(apiData.getValue() != null) {
            fetchedEchoes = parseEchoes(courseID, apiData, wc.getCookieManager().getCookies());            
        }
        return fetchedEchoes;
    }
    
    private Pair<String, JSONObject> getAPIData(String courseID, WebClient wc) {
        long t = System.currentTimeMillis();    
        JSONObject obj = null; 
        String echoBase = null;
        //First we've gotta load the echo system through blackboard so we are authenticated and save the sectionid
        wc.getOptions().setJavaScriptEnabled(true);          
        try {
            HtmlPage authenticatedEchoes= wc.getPage(bc.lms + "/webapps/osc-BasicLTI-BBLEARN/window.jsp?course_id=" + courseID + "&id=lectur");
            echoBase = "http://" + authenticatedEchoes.getUrl().getAuthority();
            int sectionID = Integer.parseInt(authenticatedEchoes.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0]);
            //then we disable javascript to speed things up
            wc.getOptions().setJavaScriptEnabled(false);   
            //next we load the echoes again to get the sectionID that is used in the api
            HtmlPage page = wc.getPage(echoBase + "/ess/portal/section/" + sectionID);
            String apiSectionID = page.getElementsByTagName("iframe").get(0).getAttribute("src").split("/section/")[1].split("\\?api")[0];
            UnexpectedPage json = wc.getPage(echoBase + "/ess/client/api/sections/" + apiSectionID + "/section-data.json?&pageSize=999");
            obj = new JSONObject(json.getWebResponse().getContentAsString()); 
        } catch(IOException Ex) {
            LOGGER.log(Level.WARNING, "Failed to get data from the API.");
        } 
        LOGGER.log(Level.FINE, "Getting JSON data for {0} took {1} ms", new Object[] {courseID, System.currentTimeMillis() - t});   
        return new Pair<>(echoBase, obj);
    }    
    
    public List<Echo> parseEchoes(String courseID, Pair<String, JSONObject> apiData, Set<Cookie> cookies) {
        List<Echo> parsedEchoes = Collections.synchronizedList(new ArrayList<Echo>());
        //get a list of all of the previously fetched unique echo IDs
        List<String> UUIDs = c.data.courseEchoes.get(courseID).stream().map(Echo::getUUID).collect(Collectors.toList());
        
        JSONObject section = apiData.getValue().getJSONObject("section");
        String echoBase = apiData.getKey();
        String unit = section.getJSONObject("course").getString("identifier");
        String unitName = section.getJSONObject("course").getString("name").split("\\[")[0];
        JSONArray presentations = section.getJSONObject("presentations").getJSONArray("pageContents");
        
        for (int i = 0; i < presentations.length(); i++) {
            String uuid = presentations.getJSONObject(i).getString("uuid");
            if(UUIDs.contains(uuid)) {
                continue;
            }            
            Echo e = new Echo();
            e.uuid = uuid;
            e.unit = unit;
            e.unitName = unitName;
            e.episode = presentations.length() - i;
            e.echoBase = echoBase;

            parsedEchoes.add(e);
        }
        
        parsedEchoes.parallelStream()
                .forEach((e) -> populateEcho(e, presentations.getJSONObject(presentations.length() - e.episode), cookies));
        return parsedEchoes;
    }
    
    public void populateEcho(Echo e, JSONObject presentation, Set<Cookie> cookies) {
        long t = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "Loading echo data for UUID = {0}.", e.uuid); 
        WebClient wc = new WebClient();
       Iterator<Cookie> i = cookies.iterator();
        while (i.hasNext()) {
            wc.getCookieManager().addCookie(i.next());
        }
        wc.getOptions().setJavaScriptEnabled(false);   
        wc.getOptions().setCssEnabled(false); 
        wc.getOptions().setAppletEnabled(false);             
        
        //loads the contentDir && streamDir for use in later steps
        Pair<String, String> presentationDirs = loadPresentationDirs(e, wc);
        e.contentDir = presentationDirs.getKey();
        e.streamDir = presentationDirs.getValue();
            
        e.duration = presentation.getLong("durationMS");
         
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");                
            e.date = sdf.parse(presentation.getString("startTime"));
        } catch (ParseException ex) {
            LOGGER.log(Level.WARNING, "Error parsing date."); 
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(e.date);            
        e.title = String.format("%tB %te%s (%tA)", e.date, e.date, getDateSuffix(cal.get(Calendar.DAY_OF_MONTH)), e.date);            

        //Check that there are thumbnails for that lecture, if there isn't then there is no video component to the lecture          
        JSONArray thumbnails = presentation.getJSONArray("thumbnails");

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

        e.url = getDownloadURL(e, wc);
        e.venue = getVenue(e);
        wc.close();
        LOGGER.log(Level.FINE, "Total parse time for {0} is {1} ms", new Object[] {e.title, System.currentTimeMillis() - t});             
    }
    
    public String getDownloadURL(Echo e, WebClient wc) {
        long t = System.currentTimeMillis();
        String download = null;
        //If there is no thumbnail then we can only download the audio version
        if(e.thumbnail != null) {
            //Check if there is a downloadable m4v or use m3u8 playlist
            try {           
                HtmlPage index = wc.getPage(e.contentDir);
                HtmlTable table = (HtmlTable) index.getElementsByTagName("table").get(0);
                for (HtmlTableRow row : table.getRows()) {
                    List<HtmlTableCell> cells = row.getCells();
                    if(cells.size() > 1 ) {
                         DomNodeList<HtmlElement> a = cells.get(1).getElementsByTagName("a");
                         if(!a.isEmpty() && a.get(0).getAttribute("href").contains(".m4v")) {
                             download = e.contentDir + a.get(0).getAttribute("href");
                             break;
                         }
                        
                    }
                }                
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Failed accessing {0} for {1}", new Object[] {e.contentDir, e.title});
            }

            //If no downloadable m4v after searching contentDir use the m3u8 playlist stream
            if(download == null) {
                download = e.streamDir;
            }
        //We can only download the audio file for the lecture
        } else {
            download = e.contentDir + "audio.mp3";
        }
        LOGGER.log(Level.FINE, "Got download URL for {0} in {1} ms {2}", new Object[] {e.title, System.currentTimeMillis() - t, download}); 
        return download;
    }

    public Pair<String, String> loadPresentationDirs(Echo e, WebClient wc) {       
        long t = System.currentTimeMillis();        
        try {
            HtmlPage presentation = wc.getPage(e.echoBase + "/ess/echo/presentation/" + e.uuid);
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
            ex.printStackTrace();
            LOGGER.log(Level.WARNING, "Error loading presentation URLs for UUID {0}.", e.uuid); 
        }
        LOGGER.log(Level.FINE, "Loaded presentation dirs in {0} ms", System.currentTimeMillis() - t); 
        return new Pair<>(e.contentDir, e.streamDir);
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
            LOGGER.log(Level.WARNING, "Error loading venue for {0}.", e.title); 
        }
        LOGGER.log(Level.FINE, "Loaded venue for {0} in {1} ms", new Object[] {e.title, System.currentTimeMillis() - t}); 
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
