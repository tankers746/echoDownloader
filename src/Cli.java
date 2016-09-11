
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Cli {

    private static final Logger LOGGER = Logger.getLogger(Cli.class.getName());
    private String[] args = null;
    private Options options = new Options();

    public Cli(String[] args) {

        this.args = args;
        
        if(args.length < 1) {
            LOGGER.log(Level.INFO, "Please enter a command. Type -h for help.");
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
            LOGGER.log(Level.SEVERE, e.getMessage());
            help();
        }
        
        Config config = new Config(configPath);        
     
        //fetching requires an LMS login
        if(fetch) {
            //Check that we can login to LMS
            if(user != null && pass != null) {
                Fetcher fetcher = new Fetcher(config, user, pass);
                fetcher.fetch();               
            } else {
                LOGGER.log(Level.SEVERE, "Please specify a username and password for LMS using -u and -p.\n");
            }
        }
        
        //downloading requires a config file
        if(download) {
            if(configPath != null) {
                Downloader downloader = new Downloader(config, verbose);
                if(downloader.checkDownloadsFolder() && downloader.checkffmpeg()) {
                    downloader.download();                     
                }    
            } else LOGGER.log(Level.SEVERE, "Please specify a config file to download lectures.\n");        
        }
        
        if(setDownloaded != null) {
            config.setEchoesDownloaded(setDownloaded);
        }
        
        if(list) {
            config.printFiltered();
        }        
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
    
    private static void disableLogs() {
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

    }
    
    static class ConsoleFormatter extends Formatter {

        //private static final String PATTERN = "MMM dd, yyyy h:mm:ss a";
        //new SimpleDateFormat(PATTERN).format(new Date(record.getMillis()));
        

        @Override
        public String format(final LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(record.getLevel().getLocalizedName())
                .append(": ")
                .append(formatMessage(record))
                .append(System.getProperty("line.separator"));
            
            if (record.getThrown() != null) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    sb.append(sw.toString());
                } catch (Exception ex) {
                    // ignore
                }
            }
            return sb.toString();
        }
    }    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        System.out.println("");
        disableLogs();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.FINER);
        
        ConsoleFormatter vsf = new ConsoleFormatter();    
        for(Handler ch : rootLogger.getHandlers()) {
            ch.setFormatter(vsf);
        }        
        Handler fh = new FileHandler("echoDownloader.log", true);  // append is true     
        fh.setLevel(Level.FINER);
        SimpleFormatter sf = new SimpleFormatter();
        fh.setFormatter(sf);
        rootLogger.addHandler(fh);

        new Cli(args).parse();       
    }    
}
