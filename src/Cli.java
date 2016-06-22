
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Cli {

    private static final Logger log = Logger.getLogger(Cli.class.getName());
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
        options.addOption("c", "config", true, "Specifies the filter config file");
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
        LectureDownloader ld = new LectureDownloader();  
        ld.setEchoBase("http://prod.lcs.uwa.edu.au:8080");
        ld.setDownloads("C:/users/tom/desktop/lectures");         
        
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
                    if(sectionID > 0) ld.addUnit(sectionID);                
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
        Filter f = new Filter();
        ArrayList<Echo> echoList = f.filterEchoMap(ld.d.sections);
        if(filterFile != null) {
            f = new Filter(filterFile, ld.units);
            echoList = f.filterEchoMap(ld.d.sections);
            if(fetch) {
                echoList = ld.fetchFiltered(f);
            }
            if(download) {
                ld.downloadEchoes(echoList);
            }
            echoList = f.filterEchoMap(ld.d.sections);
        }
        else if(download || fetch) System.out.println("Please specify a filter config file.");
        if(list) ld.printLectures(echoList);
    }

    private void help() {
        // This prints out some help
        HelpFormatter formater = new HelpFormatter();
        formater.printHelp("Main", options);
        System.exit(0);
    }
}
