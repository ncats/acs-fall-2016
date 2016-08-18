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
        if (argv.length < 4) {
            logger.info
                ("Usage: NaiveBayesTrainer INDEX NAME POS NEG\n"
                 +"where INDEX is the index built by ncats.bayeslib.MolIndex$Build\n"
                 +"NAME is the name of the model (e.g., BAO_0000519)\n"
                 +"POS is the file of all keys for positive/active instances\n"
                 +"NEG is the file of all keys of negative/inactive instances."
                 );
            System.exit(1);
        }

        NaiveBayesTrainer nbt = new NaiveBayesTrainer (argv[0]);        
        try {
            String name = argv[1];
            int n = nbt.load(name, new File (argv[2]), true);
            logger.info(argv[2]+": "+n+" positive sample(s) loaded!");
            
            n = nbt.load(name, new File (argv[3]), false);
            logger.info(argv[3]+": "+n+" negative sample(s) loaded!");
            
            NaiveBayesModel model = nbt.getModel(name);
            model.bitSelection(51);
            String file = model.save();
            logger.info("Model "+name+" saved into file '"+file+"'!");
        }
        finally {
            nbt.close();
            Index.shutdown();
        }
    }
}
