
import java.util.ArrayList;

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
            return;
        }

        options.addOption("h", "help", false, "Show help.");
        options.addOption("f", "fetch", false, "Fetch new lectures, must be combined with a filter");
        options.addOption("c", "config", true, "Specifies the config file with the filters");
        options.addOption("d", "download", false, "Download lectures, must be combined with a filter, downloades new lectures when combined with fetch");
        options.addOption("a", "add", true, "Add a unit by specifying section number");
        options.addOption("s", "set", false, "Sets all of the fetched lectures to downloaded");
        options.addOption("l", "list", false, "Lists the fetched lectures, filtered list when combined with filter");
    }   

    public void parse() {
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;
        
        String filterFile = null;
        int sectionID = 0;
        boolean fetch = false;
        boolean download = false;
        boolean list = false;
        boolean add = false;
        echoDownloader ld = new echoDownloader();        
        
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                help();
                return;
            }
            if (cmd.hasOption("c")) {
                filterFile = cmd.getOptionValue("c");           
            } 
            if (cmd.hasOption("f")) {
                fetch = true;
            } 
            if (cmd.hasOption("d")) {
                download = true;
            }             
            if (cmd.hasOption("a")) {
                try {
                    sectionID = Integer.parseInt(cmd.getOptionValue("a"));   
                    add = true;
                } catch(Exception e) {
                    System.err.println("Please enter a number for the section number.");
                    return;
                }
            }
            if (cmd.hasOption("s")) {
                ld.setAllEchoesDownloaded(ld.d.sections);
            }
            if (cmd.hasOption("l")) {
                list = true;
            }                 
            
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            help();
        }
        Config c = new Config();
        ArrayList<Echo> echoList;
        
        if(filterFile == null) {
            System.out.println("Please specify a config file.");
            return;
        } else {
            try {
                c = new Config(filterFile, ld);
                if(ld.d.echoBase.isEmpty() || ld.d.downloads.isEmpty()) {
                    System.err.println("Please specify echoBase and downloadsFolder in the config file.");
                    return;
                }                  
            } catch (Exception ex) {
                System.err.println("Failed to read config file check that the path is correct.");
                return;
            }          
            if(add) {
                if(sectionID > 0) ld.addUnit(sectionID);                     
                return;
            }
            echoList = c.filterEchoMap(ld.d.sections);
            if(fetch) {
                echoList = ld.fetchFiltered(c);
            }
            if(download) {
                ld.downloadEchoes(echoList);
            }
        }

        if(list) {
            echoList = c.filterEchoMap(ld.d.sections);            
            if(echoList.isEmpty()) {
                System.out.println("No lectures found matching the filter.");            
            } else ld.printLectures(echoList);
        }
    }

    private void help() {
        // This prints out some help
        HelpFormatter formater = new HelpFormatter();
        formater.printHelp("Main", options);
        System.exit(0);
    }
}
