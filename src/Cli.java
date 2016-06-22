
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Cli {

    private static final Logger log = Logger.getLogger(Cli.class.getName());
    private String[] args = null;
    private Options options = new Options();

    public Cli(String[] args) {

        this.args = args;

        options.addOption("h", "help", false, "Show help.");
        options.addOption("f", "filter", true, "Filter lectures, filter-file needs to be specified");
        options.addOption("l", "load", false, "Load new lectures, must be combined with a filter");
        options.addOption("d", "download", false, "Download lectures, must be combined with a filter");
        options.addOption("a", "add", true, "Add a unit by specifying section number");
    }   

    public void parse() {
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = null;
        
        String filterFile = null;
        int sectionID = 0;
        boolean load = false;
        boolean download = false;
        
        
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                help();
                return;
            }
            if (cmd.hasOption("f")) {
                filterFile = cmd.getOptionValue("v");
                File f = new File(filterFile);
                if(f.exists() && !f.isDirectory()) { 
                    System.err.println("Cannot open filter file, check that the path is correct.");
                }                
            } 
            if (cmd.hasOption("l")) {
                load = true;
            } 
            if (cmd.hasOption("d")) {
                download = true;
            }             
            if (cmd.hasOption("a")) {
                try {
                    sectionID = Integer.parseInt(cmd.getOptionValue("v"));
                } catch(Exception e) {
                    System.err.println("Please enter a number for the section number.");
                    return;
                }
            }             
        } catch (ParseException e) {
            log.log(Level.SEVERE, "Failed to parse comand line properties", e);
            help();
        }
        
        LectureDownloader ld = new LectureDownloader();
        ld.setEchoBase("http://prod.lcs.uwa.edu.au:8080");
        ld.setDownloads("C:/users/tom/desktop/lectures");
        
        
        if(filterFile != null) {
            Filter f = new Filter(filterFile, ld.units);
            ArrayList<Echo> filteredEchoes = f.filterEchoMap(ld.d.sections);
            if(load) {
                filteredEchoes = ld.fetchFiltered(f);
            }
            if(download) {
                ld.downloadEchoes(filteredEchoes);
            }
            ld.printLectures(f.filterEchoMap(ld.d.sections));
        }
        
        if(sectionID > 0) {
            ld.addUnit(sectionID);
        }
    }

    private void help() {
        // This prints out some help
        HelpFormatter formater = new HelpFormatter();

        formater.printHelp("Main", options);
        System.exit(0);
    }
}
