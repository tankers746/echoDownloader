
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
public class Downloader {
    
    private static final Logger LOGGER = Logger.getLogger(Downloader.class.getName());
    Config c;
    boolean verbose;
    
    Downloader(Config config, boolean vb) {
        c = config;
        verbose = vb;
    }

    public void download() {
        ArrayList<Echo> queue = c.filterEchoes(false);
        int queueSize = queue.size();
        for (Echo e : queue) {
            LOGGER.log(Level.INFO, "{0} lecture(s) in the download queue.", queueSize--);
            downloadEcho(e);
        }
    }
    
    public void downloadEcho(Echo e) {
        boolean failed = false;                
        String ext = ".m4v";
        boolean audio = false;
        boolean m3u8 = false;
        
        //Check if the file needs to be constructed from a m3u8 playlist
        if(e.url.substring(e.url.length()-4, e.url.length()).equals("m3u8")) {    
            ext = ".mp4";
            m3u8 = true;
        }
        if(e.url.substring(e.url.length()-3, e.url.length()).equals("mp3")) {
            ext = ".mp4";
            audio = true;
        }        
        
        String basePath = c.downloads + "/" + e.unit + "/";
        String filename =  String.format("%s%s - S01E%02d - %s", basePath, e.unit, e.episode, e.title);
        File f = new File(filename + ext);
        int n = 1;
        while(f.exists()) {
            f = new File(filename + " (" + n++ + ")" + ext);
        }
             
        try {
            List<String> args = new ArrayList<>();
            args.add(c.ffmpeg);
            args.add("-i");
            args.add(e.url);
            
            if(audio) {
                args.add("-f");
                args.add("lavfi");                      
                args.add("-i");
                args.add("color=s=640x480:r=10");  
                args.add("-c:v");
                args.add("libx264");  
                args.add("-c:a");
                args.add("aac");                
                args.add("-shortest");                 
            } else {
                args.add("-c");
                args.add("copy");                
            }
            
            args.add("-metadata");
            args.add("show=" + e.unit + " - " + e.unitName);
            
            args.add("-metadata");
            args.add("title=" + e.title);          
            
            args.add("-metadata");
            args.add("episode_sort=" + e.episode);   

            //media type is tv show for iTunes
            args.add("-metadata");
            args.add("media_type=10");               
            
            if(m3u8) {               
                args.add("-bsf:a");
                args.add("aac_adtstoasc");   
            }
            
            args.add(f.getPath());
            
            new File(basePath).mkdirs();
            ProcessBuilder downloader = new ProcessBuilder(args).redirectErrorStream(true);
            LOGGER.log(Level.FINE, "{0}", downloader.command());
            LOGGER.log(Level.INFO, "Downloading ''{0}''...", f.getName());               
            Process ffmpeg = downloader.start();
            
            int exitCode = -1;
            try (BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(ffmpeg.getInputStream(), Charset.defaultCharset()));) {
                String line;
                while ((line = processOutputReader.readLine()) != null) {
                    if(verbose) System.out.println(line);
                }
                exitCode = ffmpeg.waitFor();
            }
            
            if(exitCode == 0) {
                LOGGER.log(Level.INFO, "Succesfully downloaded to ''{0}''\n", f.getParent());
                e.downloaded = true;
                c.data.save();
            } else failed = true;
        } catch (IOException | InterruptedException ex) {
            failed = true;  
        }
        
        if(failed) {
            f.delete();
            LOGGER.log(Level.INFO, "Failed to download ''{0}''\n", f.getName());            
        }
    }

    public boolean checkffmpeg() {
        boolean ok = false;
        try {        
            // start execution
            Process p = Runtime.getRuntime().exec(c.ffmpeg + " -?");
            // exhaust input stream
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            byte[] bytes = new byte[4096];
            while (in.read(bytes) != -1) {}
            // wait for completion
            int exitCode = p.waitFor();
            if(exitCode == 0) {
                ok = true;
            }
        } catch (IOException | InterruptedException ex) {
            ok = false;
        }
        
        if(!ok) {
            LOGGER.log(Level.SEVERE, "Unable to access ffmpeg at the location: ''{0}''", c.ffmpeg);            
        }
        return ok;
    }
    
    public boolean checkDownloadsFolder() {
        boolean ok = false;
        try {
            File f = new File(c.downloads);
            ok = (f.getAbsoluteFile().exists() && f.getAbsoluteFile().isDirectory());
        } catch (Exception Ex) {
            ok = false;
        }
        
        if(!ok) {
            LOGGER.log(Level.SEVERE, "Path to downloads folder is not valid ''{0}''", c.downloads);  
        }
        return ok;
    }
    
}
