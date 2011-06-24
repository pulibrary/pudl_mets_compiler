/**
 * DBEnv.java <br/>
 * $LastChangedDate$ <br/>
 * $Author$ <br/>
 * $Rev$
 */
package edu.princeton.diglib.md.metsCompiler.db;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Tuesday, Sep 14 2010
 */

public class DBEnv {

    private static Logger appLog;
    private static Environment myEnv;
    private static EntityStore PUDLMETSEntityStore;

    public DBEnv() {
        appLog = Logger.getLogger("app.info");
    }


    /**
     * @param envHome
     * @param readOnly
     *            - pass {@code true} to make the db read-only
     * @throws DatabaseException
     * @throws IOException
     */
    public void setup(String envPath, boolean readOnly)
            throws DatabaseException {
        
        File envHome = new File(envPath);

        if (!envHome.exists() && !readOnly) {
            // Create the directory for the data store
            appLog.warn("Environment does not exist - will attempt to create");
            appLog.info("Created directory " + envHome);
            envHome.mkdir();
        }

        try {
            // Open the environment and store, and allow creation if necessary
            EnvironmentConfig myEnvConfig = new EnvironmentConfig();
            StoreConfig dataStoreConfig = new StoreConfig();
            // If the environment and store is opened for write, then we must be
            // able to create it if necessary
            myEnvConfig.setAllowCreate(!readOnly);
            dataStoreConfig.setAllowCreate(!readOnly);
            // @see http://download.oracle.com/docs/cd/E17277_02/html/GettingStartedGuide/databases.html#tempdbje
            dataStoreConfig.setTemporary(true);
            // Configure environment and store for read-only as appropriate
            myEnvConfig.setReadOnly(readOnly);
            dataStoreConfig.setReadOnly(readOnly);
            // create the environment and the data store
            myEnv = new Environment(envHome, myEnvConfig);
            PUDLMETSEntityStore = new EntityStore(myEnv, "DataStore",
                    dataStoreConfig);

        } catch (DatabaseException dbe) {
            appLog.error("Error opening environment and data store: "
                    + dbe.toString());
            System.exit(-1);
        }
        appLog.info("Successfully opened database environment and data store");

    }

    /**
     * Close the environment and the data store
     */
    public void close() {
        if (PUDLMETSEntityStore != null) {
            try {
                PUDLMETSEntityStore.close();
                appLog.info("PUDLMETSEntity store closed");
            } catch (DatabaseException dbe) {
                appLog.error("Error closing data store: " + dbe.toString());
                System.exit(-1);
            }
        }
        try {
            if (myEnv != null) {
                myEnv.close();
                appLog.info("Database environment closed");
            }
        } catch (DatabaseException dbe) {
            appLog.error("Error closing environment: " + dbe.toString());
            System.exit(-1);
        }
    }

    /**
     * Get the environment
     */
    public Environment getEnv() {
        return myEnv;
    }

    /**
     * Get the data store
     */
    public EntityStore getStore() {
        return PUDLMETSEntityStore;
    }
}
