package ncats.bayeslib;

import java.io.*;
import java.util.zip.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sleepycat.je.*;
import com.sleepycat.bind.tuple.*;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.EntryBinding;

import lychi.LyChIStandardizer;
import tripod.fingerprint.PCFP;
import chemaxon.struc.Molecule;
import chemaxon.formats.MolImporter;
import chemaxon.util.MolHandler;

/**
 * Molecule index
 * created: 08.16.2016
 */
public class MolIndex extends Index {
    static final Logger logger = Logger.getLogger(MolIndex.class.getName());
    public static final String MOLDB = MolIndex.class.getName()+".moldb";
    public static final String MWTDB = MolIndex.class.getName()+".mwtdb";
    public static final String POPDB = MolIndex.class.getName()+".popdb";

    static final int MASK[] = {
        0x80,
        0x40,
        0x20,
        0x10,
        0x08,
        0x04,
        0x02,
        0x01
    };

    public class MolEntryIterator implements Iterator<MolEntry>, AutoCloseable {
        final Transaction txn;
        final Cursor cursor;
        boolean hasNext;
        final DatabaseEntry key;
        final DatabaseEntry data;

        MolEntryIterator (Transaction txn, Cursor cursor) throws IOException {
            this.txn = txn;
            this.cursor = cursor;
            key = new DatabaseEntry ();
            data = new DatabaseEntry ();
            hasNext = cursor.getFirst(key, data, null)
                == OperationStatus.SUCCESS;
        }

        MolEntryIterator (Transaction txn, Cursor cursor,
                          DatabaseEntry key, DatabaseEntry data)
            throws IOException {
            this.txn = txn;
            this.cursor = cursor;
            this.key = key;
            this.data = data;
            hasNext = true;
        }

        public boolean hasNext () { return hasNext && !isDone (); }
        public MolEntry next () {
            MolEntry me = null;
            try {
                me = molEntryBinding.entryToObject(data);
                hasNext = cursor.getNext(key, data, null)
                    == OperationStatus.SUCCESS;
            }
            catch (DatabaseException ex) {
                logger.log(Level.SEVERE, "Can't getNext", ex);
                hasNext = false;
            }
            return me;
        }

        protected boolean isDone () {
            return false;
        }
        
        public void remove () {
            try {
                cursor.delete();
            }
            catch (DatabaseException ex) {
                logger.log(Level.SEVERE, "Can't delete cursor", ex);
            }
        }

        public void close () throws Exception {
            cursor.close();
            txn.commit();
        }
    }

    public static class MolEntry implements Serializable {
        static final long serialVersionUID = 1l;

        byte[] fp;
        byte[] mrv;
        Object key;
        
        transient Molecule mol;
        MolEntry () {}
        MolEntry (Object key, Molecule mol) throws Exception {
            mrv = mol.toFormat("mrv").getBytes();
            PCFP pcfp = new PCFP ();
            pcfp.setMolecule(mol);
            fp = pcfp.toBytes();
            this.mol = mol;
            this.key = key;
        }

        public Object getKey () { return key; }
        public byte[] getFp () { return fp; }
        public BitSet getFpBits () { return toBitSet (fp); }
        public int popcnt () {
            int cnt = 0;
            for (int i = 0; i < fp.length; ++i) {
                cnt += Integer.bitCount(fp[i] & 0xff);
            }
            return cnt;
        }
        public Molecule getMol () {
            if (mol == null && mrv != null) {
                try {
                    MolHandler mh = new MolHandler (mrv);
                    mol = mh.getMolecule();
                }
                catch (Exception ex) {
                    logger.log(Level.SEVERE, "Can't create mol instance", ex);
                }
            }
            return mol;
        }
    }

    class MwtKeyCreator implements SecondaryKeyCreator {
         public boolean createSecondaryKey (SecondaryDatabase secondary,
                                            DatabaseEntry key,
                                            DatabaseEntry data,
                                            DatabaseEntry result) {
             try {
                 MolEntry me = molEntryBinding.entryToObject(data);
                 DoubleBinding.doubleToEntry(me.getMol().getMass(), result);
                 return true;
             }
             catch (Exception ex) {
                 logger.log(Level.SEVERE, "Can't extract key", ex);
             }
             return false;
         }
    }

    class PopcntKeyCreator implements SecondaryKeyCreator {
         public boolean createSecondaryKey (SecondaryDatabase secondary,
                                            DatabaseEntry key,
                                            DatabaseEntry data,
                                            DatabaseEntry result) {
             try {
                 MolEntry me = molEntryBinding.entryToObject(data);
                 IntegerBinding.intToEntry(me.popcnt(), result);
                 return true;
             }
             catch (Exception ex) {
                 logger.log(Level.SEVERE, "Can't extract key", ex);
             }
             return false;
         }
    }
    
    SecondaryDatabase mwtDb, popDb;
    SerialBinding<MolEntry> molEntryBinding;

    public MolIndex (File dir) throws IOException {
        super (dir, MOLDB);
        molEntryBinding = new SerialBinding (catalog, MolEntry.class);
        mwtDb = createIndex (MWTDB, new MwtKeyCreator ());
        popDb = createIndex (POPDB, new PopcntKeyCreator ());
    }

    SecondaryDatabase createIndex
        (String name, SecondaryKeyCreator keyCreator) throws IOException {
        Transaction tx = env.beginTransaction(null, null);
        try {
            SecondaryConfig secConfig = new SecondaryConfig();
            secConfig.setAllowCreate(true);
            secConfig.setSortedDuplicates(true);
            secConfig.setKeyCreator(keyCreator);
            secConfig.setTransactional(true);
            return env.openSecondaryDatabase(tx, name, db, secConfig);
        }
        finally {
            tx.commit();
        }
    }

    @Override
    public void close () throws IOException {
        mwtDb.close();
        popDb.close();
        super.close();
    }

    @Override
    protected EntryBinding<MolEntry> getEntryBinding () {
        return molEntryBinding;
    }

    public boolean add (Serializable key, Molecule mol) throws Exception {
        return add (key, mol, false);
    }
    
    public boolean add (Serializable key, Molecule mol, boolean standardize)
        throws Exception {
        Molecule m = mol.cloneMolecule();
        if (standardize) {
            LyChIStandardizer lychi = new LyChIStandardizer ();
            lychi.standardize(m);
        }
        return put (key, new MolEntry (key, m));
    }

    public static BitSet toBitSet (byte[] fp) {
        BitSet bits = new BitSet (PCFP.FP_SIZE);
        for (int i = 0; i < PCFP.FP_SIZE; ++i) {
            bits.set(i, (fp[i>>3] & MASK[i%8]) != 0);
        }
        return bits;
    }   

    public int add (InputStream is, String key) throws Exception {
        return add (is, key, false);
    }
    
    public int add (InputStream is, String key, boolean standardize)
        throws Exception {
        MolImporter mi = new MolImporter (is);
        int count = 0, current = 1;
        for (Molecule mol; (mol = mi.read()) != null; ++current) {
            String id = key != null
                ? mol.getProperty(key) : mol.getName();
            if (id == null) {
                logger.warning("Input record "
                               +current+" has no key; record not indexed!");
            }
            else if (add (id, mol, standardize)) {
                ++count;
            }

            if (current%100 == 0) {
                System.out.println(id+"..."+count+"/"+current+" indexed!");
            }

            //if (current %1000 == 0) break;
        }
        
        return count;
    }

    public long size () {
        try {
            return db.count();
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Db.count() fails", ex);
        }
        return -1l;
    }

    public MolEntryIterator iterator () throws IOException {
        Transaction tx = env.beginTransaction(null, null);
        Cursor cursor = db.openCursor(tx, null);
        return new MolEntryIterator (tx, cursor);
    }
    
    /*
     * search MolEntry for range (inclusive) in mwt
     */
    public MolEntryIterator mwt (final double lower, final double upper)
        throws IOException {
        Transaction tx = env.beginTransaction(null, null);
        SecondaryCursor cursor = mwtDb.openCursor(tx, null);
        DatabaseEntry key = new DatabaseEntry ();
        DoubleBinding.doubleToEntry(lower, key);
        DatabaseEntry data = new DatabaseEntry ();
        
        if (OperationStatus.SUCCESS ==
            cursor.getSearchKeyRange(key, data, null)) {
            return new MolEntryIterator (tx, cursor, key, data) {
                @Override
                protected boolean isDone () {
                    return DoubleBinding.entryToDouble(key) > upper;
                }
            };
        }
        else {
            cursor.close();
            tx.abort();
        }
        
        return null;
    }

    /*
     * search MolEntry for popcnt range (inclusive)
     */
    public MolEntryIterator popcnt (final int lower, final int upper)
        throws IOException {
        Transaction tx = env.beginTransaction(null, null);
        SecondaryCursor cursor = popDb.openCursor(tx, null);
        DatabaseEntry key = new DatabaseEntry ();
        IntegerBinding.intToEntry(lower, key);
        DatabaseEntry data = new DatabaseEntry ();
        
        if (OperationStatus.SUCCESS ==
            cursor.getSearchKeyRange(key, data, null)) {
            return new MolEntryIterator (tx, cursor, key, data) {
                @Override
                protected boolean isDone () {
                    return IntegerBinding.entryToInt(key) > upper;
                }
            };
        }
        else {
            cursor.close();
            tx.abort();
        }
        
        return null;
    }
    
    @Override
    public MolEntry get (Serializable key) throws Exception {
        return super.get(key);
    }

    public static class Build {
        public static void main (String[] argv) throws Exception {
            if (argv.length < 2) {
                System.err.println("Usage: MolIndex$Build INDEX FILE [KEY]");
                System.exit(1);
            }
            
            File dir = new File (argv[0]);
            dir.mkdirs();
            
            File file = new File (argv[1]);
            if (!file.exists()) {
                logger.log(Level.SEVERE, "Input file doesn't exist: "+argv[1]);
                System.exit(1);
            }
            
            InputStream is;
            try {
                is = new GZIPInputStream (new FileInputStream (file));
            }
            catch (Exception ex) {
                is = new FileInputStream (file);
            }

            MolIndex index = new MolIndex (dir);            
            try {
                index.add(is, argv.length > 2 ? argv[2] : null);
            }
            finally {
                index.close();
                Index.shutdown();
            }
        }
    }

    public static class Entries {
        public static void main (String[] argv) throws Exception {
            if (argv.length == 0) {
                System.err.println("Usage: MolIndex$Entries INDEX");
                System.exit(1);
            }

            MolIndex index = new MolIndex (new File (argv[0]));
            try (MolEntryIterator it = index.iterator()) {
                while (it.hasNext()) {
                    MolEntry me = it.next();
                    BitSet bs = me.getFpBits();
                    System.out.print(me.getKey()+" "+bs.cardinality());
                    for (int b = bs.nextSetBit(0);
                         b >= 0; b = bs.nextSetBit(b+1)) {
                        System.out.print(" "+b);
                    }
                    System.out.println();
                }
            }
            finally {
                index.close();
                Index.shutdown();
            }
        }
    }

    public static class Fetch {
        final MolIndex index;
        
        Fetch (String path) throws IOException {
            File dir = new File (path);
            if (!dir.exists()) {
                logger.log(Level.SEVERE,
                           "** Error: path "+path+" does not exist!");
                System.exit(1);
            }

            index = new MolIndex (dir);
        }

        public boolean fetch (PrintStream ps, String key) throws Exception {
            MolEntry me = index.get(key);
            boolean found = false;
            if (me != null) {
                BitSet bs = me.getFpBits();
                ps.print(key+ " "+bs.cardinality());
                for (int b = bs.nextSetBit(0); b >= 0; b = bs.nextSetBit(b+1))
                    ps.print(" "+b);
                ps.println();
                found = true;
            }
            return found;
        }

        public void close () throws IOException {
            index.close();
        }
          
        public static void main (String[] argv) throws Exception {
            if (argv.length < 2) {
                System.err.println("Usage: MolIndex$Fetch INDEX KEYS...");
                System.err.println("where KEYS can be files or index keys");
                System.exit(1);
            }

            Fetch fetch = new Fetch (argv[0]);
            try {
                for (int i = 1; i < argv.length; ++i) {
                    File file = new File (argv[i]);
                    if (file.exists()) {
                        BufferedReader br =
                            new BufferedReader (new FileReader (file));
                        for (String line; (line = br.readLine()) != null; ) {
                            String tok = line.trim();
                            fetch.fetch(System.out, tok);
                        }
                        br.close();
                    }
                    else { // treat it as id
                        if (!fetch.fetch(System.out, argv[i]))
                            logger.warning(argv[i]+": not found!");
                    }
                }
            }
            finally {
                fetch.close();
                Index.shutdown();
            }
        }
    }

    public static class Mwt {
        public static void main (String[] argv) throws Exception {
            if (argv.length < 3) {
                System.err.println("Usage: MolIndex$Mwt INDEX LOWER UPPER");
                System.exit(1);
            }

            File dir = new File (argv[0]);
            if (!dir.exists()) {
                System.err.println
                    ("** Error: path "+argv[0]+" does not exist!");
                System.exit(1);
            }

            double lower = Double.parseDouble(argv[1]);
            double upper = Double.parseDouble(argv[2]);
            System.out.println("Searching for mwt range: "+lower+" to "+upper);

            try {
                MolIndex index = new MolIndex (dir);
                MolEntryIterator mei = index.mwt(lower, upper);
                if (mei != null) {
                    while (mei.hasNext()) {
                        MolEntry me = mei.next();
                        System.out.println(me.getKey()+": atoms="
                                           +me.getMol().getAtomCount()+" bonds="
                                           +me.getMol().getBondCount()+" mwt="
                                           +me.getMol().getMass());
                    }
                    mei.close();
                }
                index.close();
            }
            finally {
                Index.shutdown();
            }
        }
    }

    public static class Popcnt {
        public static void main (String[] argv) throws Exception {
            if (argv.length < 3) {
                System.err.println("Usage: MolIndex$Popcnt INDEX LOWER UPPER");
                System.exit(1);
            }

            File dir = new File (argv[0]);
            if (!dir.exists()) {
                System.err.println
                    ("** Error: path "+argv[0]+" does not exist!");
                System.exit(1);
            }

            int lower = Integer.parseInt(argv[1]);
            int upper = Integer.parseInt(argv[2]);
            System.out.println
                ("Searching for popcnt range: "+lower+" to "+upper);

            try {
                MolIndex index = new MolIndex (dir);
                MolEntryIterator mei = index.popcnt(lower, upper);
                if (mei != null) {
                    while (mei.hasNext()) {
                        MolEntry me = mei.next();
                        System.out.println
                            (me.getKey()+": atoms="
                             +me.getMol().getAtomCount()+" bonds="
                             +me.getMol().getBondCount()+" popcnt="
                             +me.popcnt());
                    }
                    mei.close();
                }
                index.close();
            }
            finally {
                Index.shutdown();
            }
        }
    }

    public static void main (String[] argv) {
        System.err.println("Please use one of the following classes instead:");
        for (Class c : new Class[] { Build.class, Entries.class, Fetch.class,
                                     Mwt.class, Popcnt.class}) {
            System.err.println(c.getName());
        }
    }
}
