/**
 * App.java <br/>
 * $LastChangedDate: 2010-09-28 11:13:43 -0400 (Tue, 28 Sep 2010) $ <br/>
 * $Author: jstroop $ <br/>
 * $Rev: 942 $
 */
package edu.princeton.diglib.md.metsCompiler;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.xml.sax.SAXException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityCursor;

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
    private static Properties defaultProps;
    private static Properties localProps;
    private static final String DEFAULT_PROPS = "defaultProps.xml";
    private static final String LOCAL_PROPS = "localProps.xml";

    private static Logger appLog;
    private static Logger recordLog;

    private static ClassLoader cl;

    private static DBEnv db;
    private static EntityAccessor accessor;

    public static void main(String[] args) {
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
        }
    }

    public App() {
        super();
        cl = App.class.getClassLoader();

        setupProperties();

        // set up the db.
        db = new DBEnv();
        db.setup(localProps.getProperty("dbenv.dir"), false);
        accessor = new EntityAccessor(db.getStore());

        initLoggers();

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
     * Initialize the properties objects. Any exceptions here cause the App to
     * exit.
     */
    private static void setupProperties() {
        // setup default properties
        InputStream localIn = null;
        try {
            localIn = new FileInputStream(LOCAL_PROPS);
        } catch (FileNotFoundException e1) {
            try {
                File f = new File(LOCAL_PROPS);
                f.createNewFile();
                InputStream in = cl.getResourceAsStream(DEFAULT_PROPS);
                FileOutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
                System.out.println("A file called " + LOCAL_PROPS + " has "
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
            localIn.close();
        } catch (InvalidPropertiesFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

    // Do we want to purge when finished? Boolean option?
    public void loadDB() throws XMLStreamException, MissingURIException,
            UnsupportedNamespaceException, MissingTypeException {
        File mdata = new File(localProps.getProperty("mdata.root.dir"));
        File images = new File(localProps.getProperty("images.root.dir"));
        File texts = new File(localProps.getProperty("texts.root.dir"));

        FileFilter filter = new PUDLFileFilter();

        EntityBuilder builder = new EntityBuilder();

        for (File dir : new File[] { mdata, images, texts }) {
            try {
                loadTree(dir, filter, builder);
            } catch (NullPointerException npe) {
                appLog.warn(dir.getAbsolutePath() + " does not exist");
            }
        }
    }

    private static void loadTree(File node, FileFilter filter, EntityBuilder builder)
            throws XMLStreamException, MissingURIException, UnsupportedNamespaceException,
            MissingTypeException {

        if (filter.accept(node)) {
            PUDLMETSEntity pme = builder.build(node);
            accessor.getUriIndex().put(pme);
            appLog.info("Loading " + pme.getUri());
            return;
        }
        for (File n : node.listFiles()) {
            // base case
            if (filter.accept(n)) {
                PUDLMETSEntity pme = builder.build(n);
                appLog.info("Loading " + pme.getUri());
                accessor.getUriIndex().put(pme);
            }
            // recursive case
            else if (n.isDirectory() && !n.isHidden() && n.getName() != "work") {
                loadTree(n, filter, builder);
            }
            // default case
            else {}
        }
    }

    public void doCompile() throws ParserConfigurationException, DatatypeConfigurationException,
            SAXException, IOException, ParseException {
        METSCompiler compiler = null;

        String outPath = localProps.getProperty("output.dir");
        compiler = new METSCompiler(accessor, outPath);

        EntityCursor<PUDLMETSEntity> cursor;
        cursor = accessor.getTypeIndex().subIndex(TYPE.OBJECT).entities();

        try {
            for (PUDLMETSEntity pme : cursor) {
                try {
                    compiler.compile(pme.getPath());
                    appLog.info("Compiled " + pme.getUri());
                } catch (MissingRecordException e) {
                    recordLog.error(e.getMessage());
                    System.err.println(e.getMessage());
                }

            }
        } finally {
            cursor.close();
        }

    }

}
