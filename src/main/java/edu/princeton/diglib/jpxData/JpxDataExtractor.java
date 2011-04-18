/**
 * JpxDataExtractor.java <br/>
 * $LastChangedDate $ <br/>
 * $Author $ <br/>
 * $Rev $
 */
package edu.princeton.diglib.jpxData;

import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Helper class for getting dimensions of a jpx file.
 * 
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Apr 15, 2011
 */
public class JpxDataExtractor {
    private static DocumentBuilder builder;
    private static boolean init;

    public static void main(String[] args) throws IOException {
        String[] testFiles = new String[5];
        testFiles[0] = "/mnt/libserv37/dps/pudl0001/4603661/00000001.jpf";
        testFiles[1] = "/mnt/libserv37/dps/pudl0001/4609321/s42/00000008.jpf";
        testFiles[2] = "/mnt/libserv37/dps/pudl0030/018/00000001.jpf";
        testFiles[3] = "/mnt/libserv37/dps/pudl0060/5954033/00000015/00000001.jpf";
        testFiles[4] = "/mnt/libserv37/dps/pudl0017/wc064/M/M0006/00000001.jpf";

        for (String s : testFiles) {
            File f = new File(s);
            int[] dimensions = JpxDataExtractor.extractDimensions(f);
            System.out.println(dimensions[0] + " : " + dimensions[1]);
        }

    }

    /**
     * @return a two member int array: [height, width]
     * @throws IOException 
     */
    public static int[] extractDimensions(File file)  {
        ImageReader reader = ImageIO.getImageReadersByMIMEType("image/jp2").next();
        ImageInputStream iis;
        try {
            iis = ImageIO.createImageInputStream(file);
            reader.setInput(iis);
            int height = reader.getHeight(0);
            int width = reader.getWidth(0);
            iis.close();
            return new int[] { height, width };
        } catch (IOException e) {
            System.err.println("Could not parse " + file.getAbsolutePath());
            e.printStackTrace();
        }
        return null;
 
    }

    public static Element extractDimensionsAsMix(File file) {
        if (init == false) {
            initDBF();
        }
        Element element = null;
        int[] dimensions = null;
        dimensions = extractDimensions(file);
        String xmlString = "<mix xmlns=\"http://www.loc.gov/mix/v20\"\n"
                + "   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "   xsi:schemaLocation=\"http://www.loc.gov/mix/v20 http://www.loc.gov/standards/mix/mix20/mix20.xsd\">\n"
                + "   <BasicImageInformation>\n" + "      <BasicImageCharacteristics>\n"
                + "         <imageWidth>" + dimensions[1] + "</imageWidth>\n"
                + "         <imageHeight>" + dimensions[0] + "</imageHeight>\n"
                + "      </BasicImageCharacteristics>\n" + "   </BasicImageInformation>\n"
                + "</mix>";
        Reader reader = new CharArrayReader(xmlString.toCharArray());
        try {
            Document doc = builder.parse(new InputSource(reader));
            element = doc.getDocumentElement();
        } catch (SAXException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return element;
    }

    private static void initDBF() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://xml.org/sax/features/namespaces", true);
            builder = factory.newDocumentBuilder();
            init = true;
        } catch (ParserConfigurationException e) {
            System.err.println("Could not initialize the DocumentBuilder for JpxDataExtractor");
            e.printStackTrace();
        }
    }
}
