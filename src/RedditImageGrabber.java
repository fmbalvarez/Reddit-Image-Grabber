import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

// saves n images from a subreddit's rss feed
// it is recommended to pipe this programs output to a text file if used regularly
public class RedditImageGrabber {
    
    // set true if you are sure you will have an internet connection when run
    // if true, will brute force bypass HTTP error code 429
    private static final boolean bruteForce = false;
    
    private static final String SUBREDDIT = "/user/fmbalvarez/m/historypics";
    private static final String DL_PATH = "saved/";
    private static final String LINK_PATTERN = "https?:\\/\\/\\S+\\\">\\[link\\]";
    private static final String ALT_PATTERN ="alt(.*?)title";
    private static final int NUM_TO_DL = 20;
    private static final int CHUNK_SIZE = 2048;
    private static final String USER_AGENT = "Mozilla/5.0";
    private static int saved = 0;

    static {
        System.out.println("Getting " + NUM_TO_DL + " images from " + SUBREDDIT);
        System.out.println();
    }
    
    public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
        try {
            // construct the XML document tree to navigate
            Document rss = getRSS(SUBREDDIT);
            // empty or create dl path
            File dlFile = new File(DL_PATH);
            if(dlFile.exists())
                FileUtils.cleanDirectory(dlFile);
            else
                dlFile.mkdirs();
            NodeList items = rss.getElementsByTagName("item");
            String link = null;
            String alt = null;
            // iterate through the list of submissions, by order of 'hot' until limit reached or no more to dl
            for(int i = 0; saved < NUM_TO_DL && i < items.getLength(); i++) {
                System.out.println(i + 1);
                Element item = (Element) items.item(i);
                Node description = item.getElementsByTagName("description").item(0); // should only be one description for each item
                // find the link in the description
                link = getLink(description.getTextContent());
                alt = getAlt(description.getTextContent());
                if(link.endsWith(".jpg") || link.endsWith(".png") || link.endsWith(".jpeg")) {
                    // the link is one we can download
                	// saves file with alt description as name
                    String fileFormat = getFileFormat(link);
                    String dlPath = DL_PATH + alt + "." + fileFormat;
                    if(saveImage(link, dlPath)) {
                        System.out.println("Saved to " + dlPath);
                        saved++;
                    }
                } else if(link.contains("/gallery/")) {
                    // the link is (probably) an imgur gallery
                    System.out.println("IMGUR GALLERY FOUND, NOT YET SUPPORTED");
                } else {
                    // the link is one we can not download
                    System.out.println("UNSUPPORTED FILETYPE");
                }
                System.out.println();
            }
        } catch (IOException e){
            // brute force way to bypass http error code 429 from getRSS (if no internet connection, will cause stack overflow)
            if(bruteForce)
                main(args);
        }
    }

    // saves an image from a url chunk by chunk to a destination file. Returns true if successfully saved image
    public static boolean saveImage(String imageUrl, String destinationFile) throws IOException {
        URL url ;
        InputStream in;
        FileOutputStream out;
        try {
            url = new URL(imageUrl);
            // use a URLConnection with a spoofed user-agent to bypass HTTP 403 response code
            URLConnection con = url.openConnection();
            con.addRequestProperty("User-Agent", USER_AGENT);
            in = con.getInputStream();
            out = new FileOutputStream(destinationFile);
        } catch (IOException e) {
            System.out.println("Caught IOException: " + e.getMessage());
            return false;
        }
        byte[] b = new byte[CHUNK_SIZE];
        int length;
        while ((length = in.read(b)) != -1) {
            out.write(b, 0, length);
        }
        in.close();
        out.close();
        return true;
    }
    
    // returns the link to an image given a link pattern specified for the reddit RSS feed
    private static String getLink(String description) {
        Pattern pattern = Pattern.compile(LINK_PATTERN);
        Matcher matcher = pattern.matcher(description);
        matcher.find();
        String link = matcher.group();
        if(link == null) {
            System.out.println("DESCRIPTION: " + description);
            return "LINK NOT FOUND";
        }
        link = link.substring(0, link.length() - 8); // cuts off the end: ">[link]
        System.out.println("LINK: " + link);
        return link;
    }
    
    // returns the alt description of the downloaded image
    private static String getAlt(String description) {
        Pattern pattern = Pattern.compile(ALT_PATTERN);
        Matcher matcher = pattern.matcher(description);
        matcher.find();
        String alt = null;
        try{
        	alt = matcher.group();
        	if(alt == null) {
        		System.out.println("DESCRIPTION: " + description);
        		return "ALT NOT FOUND";
        	}
        	alt = alt.substring(5);
        	alt = alt.substring(0, alt.length() - 7);
        } catch (IllegalStateException i){
        	// if fails in getting alt description it saves the image with number of saved pictures
        	alt = String.valueOf(saved);
        }
        System.out.println("ALT: " + alt);
        return alt;
    }

    // return the file format of the image
    private static String getFileFormat(String link){
        StringTokenizer tokenizer = new StringTokenizer(link,".");
        ArrayList <String> tokens = new ArrayList();
        while (tokenizer.hasMoreTokens()){
            tokens.add(tokenizer.nextToken());
        }
        return tokens.get(tokens.size() - 1);
    }

    // gets the XML/RSS document tree for the given subreddit
    private static Document getRSS(String subreddit) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse("http://www.reddit.com/" + subreddit + ".rss");
        return doc;
    }
}
