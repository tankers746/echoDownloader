
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
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
    
    final String SSO = "https://sso.uwa.edu.au/siteminderagent/forms/uwalogin.fcc";
    final String smagentname = "GCS23xIxgS7fdRlcwRobZbcKcGiz2HARAVD4LGRn6JtGwfdc1G0BnNt9BwOjIZgtJ9SRUO+9A7TRrHTxoGYqXK0A3Vgwllbu";
    final String target = "HTTPS://blackboardsso.webservices.uwa.edu.au/BlackBoardSSO.aspx?env=prod";
    String username;
    String password;
    HtmlPage BBHome;
    
    
    
    BlackboardConnector(String user, String pass) {
        username = user;
        password = pass;
        BBHome = null;
        
    }
    
    void loginLMS(WebClient webClient) {
        System.out.println("\nLogging into LMS...");
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
            }
            
        } catch (Exception ex) {
                System.err.println("Unable to login to LMS, check that the specified username and password are correct.");  
        }
        
        System.out.println("Logged into LMS as " + username + ".");
    }
    
    /*
        
            //Load the unit personalisation page to get list of units
            String href = BBHome.getElementById("module:_3_1").getElementsByTagName("a").get(0).getAttribute("href");
            HtmlPage unitPage = webClient.getPage(BBServer + "/" + href);
            
            
            
            //Iterate over the table of units
            Iterable<DomElement> rows = unitPage.getElementById("blockAttributes_table_jsListFULL_Student_23163_1_body").getChildElements();
            for(DomElement row : rows) {
                //iterate over all of the checkboxes to get the course id
                List<HtmlElement> inputs = row.getElementsByTagName("input");
                for(DomElement input : inputs) {
                    String id = input.getAttribute("id");
                    if(id.contains("course")) {
                        //separate the courseID from the rest of the id, e.g. (id = "amc.showcourseid._12892_1")
                        courseIDs.add(id.split("\\.")[2]);
                        break;
                    }
                }
            }
            

            //System.out.println(href);
            //System.out.println(redirectPage.getWebResponse().getContentAsString());
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getPage("https://lms.uwa.edu.au/webapps/osc-BasicLTI-BBLEARN/window.jsp?course_id=_12895_1&id=lectur");
            HtmlPage echo = webClient.getPage("http://prod.lcs.uwa.edu.au:8080/ess/portal/section/178900");
            System.out.println(echo.asXml());
            
            webClient.close();
            
        } catch (Exception ex) {
            Logger.getLogger(BlackboardConnector.class.getName()).log(Level.SEVERE, null, ex);
        }

    }*/
    
}
