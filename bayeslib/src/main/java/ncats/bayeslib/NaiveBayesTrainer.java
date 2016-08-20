package ncats.bayeslib;

import java.io.*;
import java.util.zip.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Training a naive bayes classifier
 * created: 08.18.2016
 */
public class NaiveBayesTrainer {
    static final Logger logger =
        Logger.getLogger(NaiveBayesTrainer.class.getName());

    final MolIndex index;
    final Map<String, NaiveBayesModel> models =
        new TreeMap<String, NaiveBayesModel>();

    public NaiveBayesTrainer (String path) throws IOException {
        File file = new File (path);
        if (!file.exists())
            throw new IllegalArgumentException
                ("Path "+path+" does not exist!");
        index = new MolIndex (file);
    }

    public NaiveBayesModel getModel (String name) {
        return models.get(name);
    }

    public int load (String name, File file, boolean isPos) throws Exception {
        if (!file.exists())
            throw new IllegalArgumentException
                ("File "+file+" does not exist!");
        return load (name, new FileInputStream (file), isPos);
    }
    
    public int load (String name, InputStream is, boolean isPos)
        throws Exception {
        NaiveBayesModel model= models.get(name);
        if (model == null) {
            model = new NaiveBayesModel (name);
            models.put(name, model);
        }

        int count = 0;
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        for (String line; (line = br.readLine()) != null;) {
            MolIndex.MolEntry me = index.get(line.trim());
            if (me != null) {
                model.add(isPos, me.getFpBits());
                ++count;
            }
        }
        return count;
    }

    public void close () throws IOException {
        index.close();
    }
    
    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            logger.info
                ("Usage: NaiveBayesTrainer INDEX NAME [POS NEG]\n"
                 +"where INDEX is the index built by ncats.bayeslib.MolIndex$Build\n"
                 +"NAME is the name of the model (e.g., BAO_0000519)\n"
                 +"POS is the file of all keys for positive/active instances; if not specified, default to {NAME}_POS.txt\n"
                 +"NEG is the file of all keys of negative/inactive instances; if not specified, default to {NAME}_NEG.txt"
                 );
            System.exit(1);
        }

        File path = new File (System.getProperty("bayeslib.path", "."));
        logger.info("## working path: "+path);

        NaiveBayesTrainer nbt = new NaiveBayesTrainer (argv[0]);
        try {
            String name = argv[1];

            File pos = new File 
                (path, argv.length > 2 ? argv[2] : name+"_POS.txt");
            int n = nbt.load(name, pos, true);
            logger.info(pos+": "+n+" positive sample(s) loaded!");
            
            File neg = new File
                (path, argv.length > 3 ? argv[3] : name+"_NEG.txt");
            n = nbt.load(name, neg, false);
            logger.info(neg+": "+n+" negative sample(s) loaded!");
            
            NaiveBayesModel model = nbt.getModel(name);
            model.bitSelection(41);
            String file = model.save();
            logger.info("Model "+name+" saved into file '"+file+"'!");
        }
        finally {
            nbt.close();
            Index.shutdown();
        }
    }
}
