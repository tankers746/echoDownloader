
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    enum downloadType { AUDIO, VIDEO, STREAM }
    
    Downloader(Config config, boolean vb) {
        c = config;
        verbose = vb;
    }

    public void download() {
        ArrayList<Echo> queue = c.filterEchoes(false);
        final AtomicInteger queueSize = new AtomicInteger(queue.size());
        LOGGER.log(Level.INFO, "{0} lecture(s) in the download queue.", queueSize.get());
        queue.parallelStream()
                .forEach((e) -> {
                    downloadEcho(e);                    
                    LOGGER.log(Level.INFO, "{0} lecture(s) in the download queue.", queueSize.decrementAndGet());
                    });
    }
    
    public void downloadEcho(Echo e) {              
        String ext;
        downloadType type;
        
        //Find out the type of download
        if(e.url.substring(e.url.length()-4, e.url.length()).equals("m3u8")) {    
            ext = ".mp4";
            type = downloadType.STREAM;
        } else if(e.url.substring(e.url.length()-3, e.url.length()).equals("mp3")) {
            ext = ".mp4";
            type = downloadType.AUDIO;
        } else {
            ext = ".m4v";
            type = downloadType.VIDEO;            
        }        
        
        String basePath = c.downloads + "/" + e.unit + "/";
        String filename =  String.format("%s%s - S01E%02d - %s", basePath, e.unit, e.episode, e.title);
        File f = new File(filename + ext);
        int n = 1;
        while(f.exists()) {
            f = new File(filename + " (" + n++ + ")" + ext);
        }
        List<String> args = buildFFmpegArgs(f, e, type);
        
        LOGGER.log(Level.FINE, "Downloading ''{0}''...", f.getName());          
        e.downloaded = runFFmpeg(args);
        
        if(e.downloaded) {
            c.data.save();    
            LOGGER.log(Level.INFO, "Succesfully downloaded ''{0}''\n", f.getName());    
        } else {
            f.delete();
            LOGGER.log(Level.INFO, "Failed to download ''{0}''\n", f.getName());      
        }
    }
    
    List<String> buildFFmpegArgs(File f, Echo e, downloadType type) {
        List<String> args = new ArrayList<>();
        args.add(c.ffmpeg);
        args.add("-i");
        args.add(e.url);

        if(type == downloadType.AUDIO) {
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

       if(type == downloadType.STREAM) {               
            args.add("-bsf:a");
            args.add("aac_adtstoasc");   
        }                

        args.add("-metadata");
        args.add("show=" + e.unit + " - " + e.unitName);

        args.add("-metadata");
        args.add("title=" + e.title + " - " + e.description);          

        args.add("-metadata");
        args.add("episode_sort=" + e.episode);   

        args.add("-metadata");
        args.add("description=" + e.description);              

        //media type is tv show for iTunes
        args.add("-metadata");
        args.add("media_type=10");       
        
        args.add(f.getPath()); 
        return args;
    }
    
    boolean runFFmpeg(List<String> args) {
        boolean ok = false;        
        try {     
            ProcessBuilder ffmpeg = new ProcessBuilder(args).redirectErrorStream(true);
            LOGGER.log(Level.FINE, "{0}", ffmpeg.command());          
            Process p = ffmpeg.start();
            try (BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));) {
                String line;
                while ((line = processOutputReader.readLine()) != null) {
                    if(verbose) System.out.println(line);
                }
                int exitCode = p.waitFor();
                ok = (exitCode == 0);
            }
        } catch (IOException | InterruptedException ex) {}
        return ok;        
    }

    public boolean checkFFmpeg() {       
        if(runFFmpeg(Arrays.asList(c.ffmpeg, "-?"))) {
            return true;            
        } else {
            LOGGER.log(Level.SEVERE, "Unable to access ffmpeg at the location: ''{0}''", c.ffmpeg);
            return false;            
        }
    }
    
    public boolean checkDownloadsFolder() {
        File f = new File(c.downloads);
        if(f.getAbsoluteFile().exists() && f.getAbsoluteFile().isDirectory()) {
            return true;
        } else {
            LOGGER.log(Level.SEVERE, "Path to downloads folder is not valid ''{0}''", c.downloads);  
            return false;
        }
    }
    
}
