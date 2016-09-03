
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
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
public class BlackboardConnector {
    private static final Logger LOGGER = Logger.getLogger(BlackboardConnector.class.getName());
    final String SSO = "https://sso.uwa.edu.au/siteminderagent/forms/uwalogin.fcc";
    final String smagentname = "GCS23xIxgS7fdRlcwRobZbcKcGiz2HARAVD4LGRn6JtGwfdc1G0BnNt9BwOjIZgtJ9SRUO+9A7TRrHTxoGYqXK0A3Vgwllbu";
    final String target = "HTTPS://blackboardsso.webservices.uwa.edu.au/BlackBoardSSO.aspx?env=prod";
    final String lms = "https://lms.uwa.edu.au";
    String username;
    String password;
    HtmlPage BBHome;
    WebClient webClient;
    
    
    
    BlackboardConnector(String user, String pass, WebClient client) {
        username = user;
        password = pass;
        BBHome = null;
        webClient = client;
        webClient.getOptions().setJavaScriptEnabled(false);   
        webClient.getOptions().setCssEnabled(false); 
        webClient.getOptions().setAppletEnabled(false);
        
    }
    
    boolean loginLMS() {
        boolean loggedIn = false;
        LOGGER.log(Level.INFO, "Logging into LMS...");
        try {
            //WebClient webClient = new WebClient();
            //Create the POST to login to LMS
            URL url = new URL(SSO);
            WebRequest requestSettings = new WebRequest(url, HttpMethod.POST);     
            webClient.getOptions().setJavaScriptEnabled(false);
            requestSettings.setAdditionalHeader("Content-Type", "application/x-www-form-urlencoded");
            
            StringBuilder body = new StringBuilder();
            body.append("PASSWORD=");
            body.append(password);
            body.append("&USER=");
            body.append(username);
            body.append("&smagentname=");
            body.append(URLEncoder.encode(smagentname, "UTF-8"));
            body.append("&target=");
            body.append(URLEncoder.encode(target, "UTF-8"));

            requestSettings.setRequestBody(body.toString());
            
            //Login to LMS
            HtmlPage login = webClient.getPage(requestSettings);
            
            //Check that the login worked
            if(login.getElementsByTagName("title").get(0).getTextContent().contains("Blackboard")) {
                BBHome = login;
                loggedIn = true;              
            }
            
        } catch (RuntimeException | IOException ex) {
            loggedIn = false;
        }
        
        if(loggedIn) {
            LOGGER.log(Level.INFO, "Logged into LMS as {0}.\n", username);                       
        } else {
            LOGGER.log(Level.SEVERE, "Unable to login to LMS, check the username and password.");              
        }
        return loggedIn;
    }
    
    public int loadUnits(Data d) {
        int loaded = 0;
        try {
            LOGGER.log(Level.INFO, "Loading units...");              
            long t = System.currentTimeMillis();

            //Load the unit personalisation page to get list of units
            String href = BBHome.getElementById("module:_3_1").getElementsByTagName("a").get(0).getAttribute("href");
            HtmlPage unitPage = webClient.getPage("https://" + BBHome.getUrl().getHost() + "/" + href);
            
            //Iterate over the table of units
            Iterable<DomElement> rows = unitPage.getElementById("blockAttributes_table_jsListFULL_Student_23163_1_body").getChildElements();
            for(DomElement row : rows) {
                //iterate over all of the checkboxes to get the course id
                List<HtmlElement> inputs = row.getElementsByTagName("input");
                for(DomElement input : inputs) {
                    String id = input.getAttribute("id");
                    if(id.contains("course")) {
                        //separate the courseID from the rest of the id, e.g. (id = "amc.showcourseid._12892_1")
                        String title = input.getAttribute("title").substring(0,8);
                        d.units.put(title , id.split("\\.")[2]);
                        LOGGER.log(Level.INFO, "Found unit {0} with course ID {1}", new Object[] {title, d.units.get(title)});                           
                        loaded++;
                        break;
                    }
                }
            }           
            d.save();
            LOGGER.log(Level.INFO, "Loaded {0} units from LMS.\n", loaded);
        } catch (IOException | FailingHttpStatusCodeException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get units from Blackboard.\n");
        }
        return loaded;
    }
    
}
