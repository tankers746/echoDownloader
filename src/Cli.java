
import com.gargoylesoftware.htmlunit.WebClient;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Cli {

    private String[] args = null;
    private Options options = new Options();

    public Cli(String[] args) {

        this.args = args;
        
        if(args.length < 1) {
            System.err.println("Please enter a command. Type -h for help.");
            System.exit(0);
        }

        options.addOption("h", "help", false, "Show help.");
        options.addOption("f", "fetch", false, "Fetch new lectures");
        options.addOption("c", "config", true, "Specifies the config file with the filters");
        options.addOption("d", "download", false, "Downloads any undownloaded lectures, can be combined with a filter, downloads new lectures when combined with fetch");
        options.addOption("s", "setdownloaded", true, "Required: true/false, sets the lectures matching the filter as being downloaded or not downloaded");
        options.addOption("l", "list", false, "Lists the fetched lectures, filtered list when combined with filter");
        options.addOption("u", "username", true, "Required: Student number login for LMS");
        options.addOption("p", "password", true, "Required: Password login for LMS");
        options.addOption("v", "verbose", false, "Show ffmpeg output");
    }   

    public void parse() {
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;
        
        //first we load the saved data
        Data d = (Data) Data.getObject("data.ser");
        if (d == null) { //checks if there is a saved table
            d = new Data();
            Data.saveObject("data.ser", d);
        }     
        HashMap<String, String> units = (HashMap<String, String>) Data.getObject("units.ser");
        if (units == null) { //checks if there is a saved table
            units = new HashMap<>();
            Data.saveObject("units.ser", units);
        }
        
        String configPath = null;
        String user = null;
        String pass = null;
        boolean fetch = false;
        boolean download = false;
        boolean list = false;
        boolean verbose = false;
        Boolean setDownloaded = null;
        
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                help();
                return;
            }
            if (cmd.hasOption("c")) {
                configPath = cmd.getOptionValue("c");           
            } 
            if (cmd.hasOption("f")) {
                fetch = true;
            } 
            if (cmd.hasOption("d")) {
                download = true;
            }             
            if (cmd.hasOption("s")) {
                setDownloaded = parseBoolean(cmd.getOptionValue("s"));
            }
            if (cmd.hasOption("l")) {
                list = true;
            }                
            if (cmd.hasOption("u")) {
                user = cmd.getOptionValue("u");    
            }  
            if (cmd.hasOption("p")) {
                pass = cmd.getOptionValue("p"); 
            }
            if (cmd.hasOption("v")) {
                verbose = true;
            }             
            
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            help();
        }
        
        Config c = new Config(d, units, configPath);
        ArrayList<Echo> echoList = c.filterEchoMap(d.courses);  
        
        if(setDownloaded != null) {
            echoDownloader.setEchoesDownloaded(echoList, setDownloaded);
            Data.saveObject("data.ser", d);
        }
        
        if(list) {
            echoDownloader.printLectures(echoList);
            return;
        }
     
        //fetching requires an LMS login
        if(fetch) {
            //Check that we can login to LMS
            if(user != null || pass != null) {
                WebClient webClient = new WebClient();
                webClient.getOptions().setJavaScriptEnabled(false);  
                BlackboardConnector bc = new BlackboardConnector(user, pass);
                bc.loginLMS(webClient);
                echoDownloader ld = new echoDownloader(webClient, bc, d, units);
                //Check that LMS login worked
                if(bc.BBHome != null) {
                    ld.fetch(c);
                    Data.saveObject("data.ser", d);
                    echoList = c.filterEchoMap(d.courses);                    
                }
                webClient.close();
            } else {
                System.err.println("Please specify a username and password for LMS using -u and -p.");
            }
        }
        
        //downloading requires a config file
        if(download) {
            if(configPath != null) {
                if(d.downloads != null) {
                    if(checkffmpeg(d.ffmpeg)) {
                        echoList = echoList.stream().filter((e) -> (!e.downloaded)).collect(Collectors.toCollection(ArrayList::new));
                        echoDownloader.downloadEchoes(echoList, d.downloads, d.ffmpeg, verbose); 
                        Data.saveObject("data.ser", d);                        
                    }
                } else System.err.println("Ensure you have specified a valid downloadsFolder in the config file.");         
            } else System.err.println("Please specify a config file.");        
        }
    }
    
    private boolean checkffmpeg(String path) {
        boolean works = false;
        try {        
            // start execution
            Process p = Runtime.getRuntime().exec(path + " -?");
            // exhaust input stream
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            byte[] bytes = new byte[4096];
            while (in.read(bytes) != -1) {}
            // wait for completion
            int exitCode = p.waitFor();
            if(exitCode == 0) {
                works = true;
            }
        } catch (IOException | InterruptedException ex) {
            System.err.println("Unable to access ffmpeg, make sure the correct path is specified in the config file.");
        }
        return works;
    }
         
    private Boolean parseBoolean(String s) {
        if(s == null) return null;
        if(s.toLowerCase().equals("true")) return true;
        if(s.toLowerCase().equals("false")) return false;
        return null;
    }

    private void help() {
        // This prints out some help
        HelpFormatter formater = new HelpFormatter();
        formater.printHelp("Main", options);
        System.exit(0);
    }
}
