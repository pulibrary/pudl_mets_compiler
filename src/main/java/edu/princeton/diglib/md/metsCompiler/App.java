/**
 * App.java <br/>
 * $LastChangedDate$ <br/>
 * $Author$ <br/>
 * $Rev$
 */
package edu.princeton.diglib.md.metsCompiler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.InvalidPropertiesFormatException;
import java.util.LinkedList;
import java.util.Properties;

import javax.ws.rs.core.MediaType;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.xml.sax.SAXException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityCursor;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import edu.princeton.diglib.md.metsCompiler.db.DBEnv;
import edu.princeton.diglib.md.metsCompiler.db.EntityAccessor;
import edu.princeton.diglib.md.metsCompiler.db.EntityBuilder;
import edu.princeton.diglib.md.metsCompiler.db.MissingTypeException;
import edu.princeton.diglib.md.metsCompiler.db.MissingURIException;
import edu.princeton.diglib.md.metsCompiler.db.PUDLMETSEntity;
import edu.princeton.diglib.md.metsCompiler.db.UnsupportedNamespaceException;
import edu.princeton.diglib.md.metsCompiler.db.PUDLMETSEntity.TYPE;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Sep 14, 2010
 */
public class App {

    private static Logger appLog;
    private static Logger recordLog;

    private static ClassLoader cl;

    private static DBEnv db;
    private static EntityAccessor accessor;

    // HTTP client
    private static Client client;
    private static HTTPBasicAuthFilter auth;

    // Properties
    private static Properties defaultProps;
    private static Properties localProps;
    private static final String DEFAULT_PROPS = "defaultProps.xml";
    private static final String LOCAL_PROPS = "config.xml";
    private static final String HOME_TMPL = "{USER_HOME}";

    private static String output;
    private static String httpUser;
    private static String httpPW;
    private static String[] pudlNos;
    private static String imageMetsRoot;
    private static String objectsRoot;
    private static String textsRoot;
    private static String imageMetsDir;
    private static String singleObjectMETS;
    private static String singleDMD;
    private static String dbenvDir;
    private static String singleText;

    public static void main(String[] args) throws TransformerException {

        App app = new App();

        // load
        appLog.info("Beginning database load");
        try {
            app.loadDB();
        } catch (DatabaseException e) {
            appLog.error(e.getMessage());
        } catch (XMLStreamException e) {
            recordLog.error(e.getMessage());
        } catch (MissingURIException e) {
            recordLog.error(e.getMessage());
        } catch (UnsupportedNamespaceException e) {
            recordLog.error(e.getMessage());
        } catch (MissingTypeException e) {
            recordLog.error(e.getMessage());
        }

        // compile
        appLog.info("Compiling...");
        try {
            app.doCompile();
        } catch (FileNotFoundException e) {
            recordLog.error(e.getMessage());
        } catch (ParserConfigurationException e) {
            recordLog.error(e.getMessage());
        } catch (DatatypeConfigurationException e) {
            recordLog.error(e.getMessage());
        } catch (SAXException e) {
            recordLog.error(e.getMessage());
        } catch (IOException e) {
            recordLog.error(e.getMessage());
        } catch (ParseException e) {
            recordLog.error(e.getMessage());
        } finally {
            app.getDb().close();
            File dbDir = new File(dbenvDir);
            for (File f : dbDir.listFiles())
                f.delete();
            if (dbDir.delete())
                appLog.info("Successfully deleted temporary database environment");
            else
                appLog.warn("Could not delete temporary database environment");
        }
    }

    public App() {
        super();
        cl = App.class.getClassLoader();

        setupProperties();

        initLoggers();

        // set up the db.
        db = new DBEnv();
        db.setup(dbenvDir, false);
        accessor = new EntityAccessor(db.getStore());
    }

    /**
     * @return the db
     */
    public DBEnv getDb() {
        return db;
    }

    private static void initLoggers() {
        URL propsPath = cl.getResource("log4j.xml");
        PropertyConfigurator.configure(propsPath);
        appLog = Logger.getLogger("app.info");
        recordLog = Logger.getLogger("record.errors");
    }

    /*
     * Initialize the properties objects. Any exceptions here cause the
     * JpxDataExtract to exit.
     */
    private static void setupProperties() {
        // setup default properties
        FileInputStream localIn = null;
        try {
            localIn = new FileInputStream(LOCAL_PROPS);
        } catch (FileNotFoundException e1) {
            try {
                InputStream in = cl.getResourceAsStream(DEFAULT_PROPS);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                BufferedWriter bw = new BufferedWriter(new FileWriter(LOCAL_PROPS));

                String home = System.getProperty("user.home");
                String str;

                while (null != ((str = br.readLine()))) {
                    str = str.replace(HOME_TMPL, home);
                    bw.write(str);
                    bw.newLine();
                }

                br.close();
                bw.close();

                System.err.println("A file called " + LOCAL_PROPS + " has "
                        + "been created in this directory. Please update it " + "and run again.");
                System.exit(0);

            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }

        // defaults
        defaultProps = new Properties();
        try {
            InputStream defaultsIn = cl.getResourceAsStream(DEFAULT_PROPS);
            defaultProps.loadFromXML(defaultsIn);
            defaultsIn.close();
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        // local
        localProps = new Properties(defaultProps);
        try {
            localProps.loadFromXML(localIn);

            output = localProps.getProperty("App.output");
            httpUser = localProps.getProperty("App.httpUser");
            httpPW = localProps.getProperty("App.httpPW");

            dbenvDir = localProps.getProperty("App.dbenvDir");

            pudlNos = localProps.getProperty("App.pudlNos").split(",\\s?");
            imageMetsRoot = localProps.getProperty("App.imageMetsRoot");
            objectsRoot = localProps.getProperty("App.objectsRoot");
            textsRoot = localProps.getProperty("App.textsRoot");

            imageMetsDir = localProps.getProperty("App.imageMetsDir");
            singleObjectMETS = localProps.getProperty("App.singleObjectMETS");
            singleDMD = localProps.getProperty("App.singleDMD");
            singleText = localProps.getProperty("App.singleText");

            dbenvDir = localProps.getProperty("App.dbenvDir");

            localIn.close();
        } catch (InvalidPropertiesFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

    public void loadDB() throws XMLStreamException, MissingURIException,
            UnsupportedNamespaceException, MissingTypeException {

        Mode mode = determineMode();
        appLog.info("Loading in " + mode.toString() + " mode");

        FileFilter filter = new Filters.PUDLFileFilter();

        EntityBuilder builder = new EntityBuilder();

        LinkedList<File> toLoad = new LinkedList<File>();

        if (mode.equals(Mode.MULTIPLE_OBJECTS)) {
            // build a bunch of paths based on each of our given PUDL numbers
            for (String pudlNo : pudlNos) {
                toLoad.add(new File(imageMetsRoot, pudlNo));
                toLoad.add(new File(objectsRoot, pudlNo));
                toLoad.add(new File(textsRoot, pudlNo));
            }
        } else { // SINGLE
            toLoad.add(new File(imageMetsDir));
            toLoad.add(new File(singleObjectMETS));
            toLoad.add(new File(singleDMD));
            toLoad.add(new File(singleText));
        }

        for (File node : toLoad) {
            try {
                loadToBDB(node, filter, builder);
            } catch (NullPointerException npe) {
                appLog.warn(node.getAbsolutePath() + " does not exist");
            }
        }
    }

    private static void loadToBDB(File node, FileFilter filter, EntityBuilder builder)
            throws XMLStreamException, MissingURIException, UnsupportedNamespaceException,
            MissingTypeException {

        // catch files
        if (filter.accept(node)) {
            PUDLMETSEntity pme = builder.build(node);
            accessor.getUriIndex().put(pme);
            appLog.info("Loading " + pme.getUri());
            return;
        }
        // dirs
        for (File n : node.listFiles()) {
            // base case
            if (filter.accept(n)) {
                PUDLMETSEntity pme = builder.build(n);
                appLog.info("Loading " + pme.getUri());
                accessor.getUriIndex().put(pme);
            }
            // recursive case
            else if (n.isDirectory() && !n.isHidden() && !n.getName().equals("work")) {
                loadToBDB(n, filter, builder);
            }
            // default case
            else {}
        }
    }

    public void doCompile() throws ParserConfigurationException, DatatypeConfigurationException,
            SAXException, IOException, ParseException, TransformerException {
        METSCompiler compiler = null;

        compiler = new METSCompiler(accessor, output, true);

        EntityCursor<PUDLMETSEntity> cursor;
        cursor = accessor.getTypeIndex().subIndex(TYPE.OBJECT).entities();

        try {
            for (PUDLMETSEntity pme : cursor) {
                try {
                    String pmePath = pme.getPath();

                    // This is hack...necessary to keep fs structure
                    int begin = pmePath.lastIndexOf("/pudl");
                    String relPath = pmePath.substring(begin);

                    appLog.info("Compiling " + pme.getUri());

                    if (output.startsWith("http://")) {

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        compiler.compileToOutputStream(pmePath, out);
                        ByteArrayInputStream in;
                        in = new ByteArrayInputStream(out.toByteArray());
                        out.close();
                        String status = loadToWebResource(in, relPath);
                        appLog.info(status + " - " + pme.getUri());
                    } else {
                        File outFile = new File(output, relPath);
                        outFile.getParentFile().mkdirs();
                        compiler.compileToFile(pme.getPath(), outFile);
                        appLog.info("Compiled " + pme.getUri());
                    }

                } catch (MissingRecordException e) {
                    recordLog.error(e.getMessage());
                    System.err.println(e.getMessage());
                }
            }
        } finally {
            cursor.close();
        }
    }

    private static String loadToWebResource(InputStream in, String relPath) {
        if (client == null) client = Client.create();
        if (auth == null) auth = new HTTPBasicAuthFilter(httpUser, httpPW);

        String putURI;
        WebResource putResource; // web resource based on the putURI
        ClientResponse response;

        putURI = output + relPath;
        putResource = client.resource(putURI);
        putResource.addFilter(auth);

        response = (ClientResponse) putResource.type(MediaType.TEXT_XML).put(ClientResponse.class,
                in);

        try {
            in.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // appLog.info(response.getClientResponseStatus());
        return response.getStatus() + ": " + response.getClientResponseStatus();
    }

    /**
     * @return the localProps
     */
    public static Properties getLocalProps() {
        return localProps;
    }

    private static Mode determineMode() {
        Mode mode;
        if (imageMetsDir != null && singleObjectMETS != null && singleDMD != null
                && singleText != null)
            mode = Mode.SINGLE_OBJECT;
        else
            mode = Mode.MULTIPLE_OBJECTS;

        return mode;
    }

    /**
     * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
     * @since Mar 1, 2011
     * 
     */
    public enum Mode {
        SINGLE_OBJECT("single object"), MULTIPLE_OBJECTS("multiple objects");

        private String readable;

        private Mode(String readable) {
            this.readable = readable;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return this.readable;
        };

    }
}
