package ncats.bayeslib;


import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.*;

import com.sleepycat.je.*;
import com.sleepycat.bind.tuple.*;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;

/**
 * Base class for constructing an Index
 * created: 08.16.2016
 */
public class Index {
    static final Logger logger = Logger.getLogger(Index.class.getName());
    static final String CATDB = Index.class.getName()+".catalog";

    static final ConcurrentMap<File, Environment> ENV =
        new ConcurrentHashMap<File, Environment>();

    protected Database db, catalogDb;
    protected Environment env;
    protected StoredClassCatalog catalog;
    
    protected Index (File dir, String name) throws IOException {
        env = createEnv (dir);
        Transaction tx = env.beginTransaction(null, null);
        try {
            DatabaseConfig dbconf = new DatabaseConfig ();
            dbconf.setAllowCreate(true);
            dbconf.setTransactional(true);

            db = env.openDatabase(tx, name, dbconf);
            logger.info("## path="+dir+" database="+name
                        +" initialized; size="+db.count());
            
            catalogDb = env.openDatabase(tx, CATDB, dbconf);
            catalog = new StoredClassCatalog (catalogDb);
        }
        finally {
            tx.commit();
        }
    }

    static synchronized Environment createEnv (File dir) throws IOException {
        Environment env = ENV.get(dir);
        if (env == null) {
            EnvironmentConfig envconf = new EnvironmentConfig ();
            envconf.setAllowCreate(true);
            envconf.setTransactional(true);
            envconf.setTxnTimeout(5, TimeUnit.SECONDS);
            envconf.setLockTimeout(5, TimeUnit.SECONDS);
            // don't have to worry about dir already opened
            ENV.putIfAbsent
                (dir, env = new Environment (dir, envconf));
        }
        return env;
    }

    public void drop () throws IOException {
        Transaction tx = env.beginTransaction(null, null);      
        try {
            env.removeDatabase(tx, db.getDatabaseName());
        }
        finally {
            tx.commit();
        }
    }

    public void close () throws IOException {
        catalog.close();
        catalogDb.close();
        db.close();
    }

    protected static DatabaseEntry createEntry (Serializable v)
        throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream ();
        ObjectOutputStream oos = new ObjectOutputStream (bos);
        oos.writeObject(v);
        oos.close();
        return new DatabaseEntry (bos.toByteArray());
    }

    protected static Object createObject (DatabaseEntry e) throws Exception {
        ObjectInputStream ois = new ObjectInputStream
            (new ByteArrayInputStream
             (e.getData(), e.getOffset(), e.getSize()));
        return ois.readObject();
    }

    public <T> boolean put (Serializable key, T value) throws IOException {
        return put (key, value, true);
    }
    
    public <T> boolean put (Serializable key, T value, boolean overwrite)
        throws IOException {
        Transaction tx = env.beginTransaction(null, null);
        try {
            DatabaseEntry ekey = createEntry (key);
            EntryBinding<T> binding = getEntryBinding ();
            DatabaseEntry data;
            if (binding != null) {
                data = new DatabaseEntry ();
                binding.objectToEntry(value, data);
            }
            else if (value instanceof Serializable) {
                data = createEntry ((Serializable)value);
            }
            else {
                throw new IllegalArgumentException
                    ("Value neither is serializable nor have specific "
                     +"EntryBinding!");
            }
            
            OperationStatus status = overwrite
                ? db.put(tx, ekey, data) : db.putNoOverwrite(tx, ekey, data);
            if (status != OperationStatus.SUCCESS
                && status != OperationStatus.KEYEXIST)
                logger.warning("PUT operation fails with status "+status);
            
            return status == OperationStatus.SUCCESS;
        }
        catch (Exception ex) {
            tx.abort();
        }
        finally {
            tx.commit();
        }
        return false;
    }

    protected <T> EntryBinding<T> getEntryBinding () {
        return null;
    }

    public <T> T get (Serializable key) throws Exception {
        T value = null;
        Transaction tx = env.beginTransaction(null, null);
        try {
            DatabaseEntry ekey = createEntry (key);
            DatabaseEntry data = new DatabaseEntry ();
            OperationStatus status = db.get(tx, ekey, data, null);
            if (status == OperationStatus.NOTFOUND) {
            }
            else if (status == OperationStatus.SUCCESS) {
                EntryBinding<T> binding = getEntryBinding ();
                if (binding != null) {
                    value = binding.entryToObject(data);
                }
                else 
                    value = (T) createObject (data);
            }
            else {
                logger.warning("GET operation fails with status "
                               +status+" for key="+key);
            }

            return value;
        }
        finally {
            tx.commit();
        }
    }

    public static void shutdown () {
        for (Map.Entry<File, Environment> me : ENV.entrySet()) {
            try {
                me.getValue().close();
            }
            catch (Exception ex) {
                logger.log(Level.SEVERE, "Can't shutdown environment: "
                           +me.getKey(), ex);
            }
        }
    }
}
