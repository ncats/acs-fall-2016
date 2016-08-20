package ncats.bayeslib;

import java.io.*;
import java.util.zip.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import tripod.fingerprint.PCFP;

/**
 * naive bayes predictor
 * created: 08.18.2016
 */
public class NaiveBayesPredictor {
    static final Logger logger = Logger.getLogger
        (NaiveBayesPredictor.class.getName());

    static public class Predictor implements Comparable<Predictor> {
        double score;
        String label;

        Predictor (String label, double score) {
            this.label = label;
            this.score = score;
        }

        public int compareTo (Predictor p) {
            if (score < p.score) return -1;
            if (score > p.score) return 1;
            return label.compareTo(p.label);
        }

        public double getScore () { return score; }
        public String getLabel () { return label; }
        public String toString () { return "label:"+label+" score:"+score; }
    }
    
    final MolIndex index;
    final List<NaiveBayesModel> models = new ArrayList<NaiveBayesModel>();
    
    public NaiveBayesPredictor (String path) throws IOException {
        File file = new File (path);
        if (!file.exists())
            throw new IllegalArgumentException
                ("Path "+path+" does not exist!");

        index = new MolIndex (file);
    }

    public void loadModel (File file) throws Exception {
        if (!file.exists()) {
            logger.warning("Model file does not exist: "+file);
        }
        else if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                try {
                    loadModel (f);
                }
                catch (IOException ex) {
                    logger.log(Level.SEVERE, "Can't load model: "+f, ex);
                }
            }
        }
        else {
            try {
                NaiveBayesModel model = NaiveBayesModel.load(file);
                models.add(model);
                logger.info("Model "+model+" loaded!");
            }
            catch (IOException ex) {
                logger.log(Level.SEVERE,
                           "Not a valid naive bayes model: "+file, ex);
            }
        }
    }

    public List<Predictor> eval () throws Exception {
        if (models.isEmpty()) {
            logger.warning("No models loaded!");
            return null;
        }

        final Map<String, Double> cumulative = new HashMap<String, Double>();
        try (MolIndex.MolEntryIterator it = index.iterator()) {
            while (it.hasNext()) {
                MolIndex.MolEntry me = it.next();
                //System.out.println("["+me.getKey()+"]");
                for (NaiveBayesModel model : models) {
                    double prob = model.getPosterior(me.getFpBits());
                    //System.out.println(model.getName()+" "+prob);
                    Double c = cumulative.get(model.getName());
                    cumulative.put
                        (model.getName(), c == null ? prob : (prob+c));
                }
            }
        }
        
        String[] labels = cumulative.keySet().toArray(new String[0]);
        Arrays.sort(labels, new Comparator<String>() {
                public int compare (String l1, String l2) {
                    Double d1 = cumulative.get(l1);
                    Double d2 = cumulative.get(l2);
                    if (d2 < d1) return -1;
                    if (d2 > d1) return 1;
                    return l1.compareTo(l2);
                }
            });

        List<Predictor> results = new ArrayList<Predictor>();
        double size = (double)index.size();
        for (String l : labels) {
            // normalize 
            results.add(new Predictor (l, cumulative.get(l)/size));
        }
        return results;
    }

    public void close () throws IOException {
        index.close();
    }
    
    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println
                ("Usage: NaiveBayesPredictor INDEX MODELS...\n"
                 +"where INDEX is the input built by ncats.bayeslib.MolIndex$Build\n"
                 +"and MODELS are model files generated by ncats.bayeslib.NaiveBayesTrainer.\n"
                 +"Note that MODELS can a directory contains all the models\n"
                 +"or individual models themselves.");
            System.exit(1);
        }

        NaiveBayesPredictor nbp = new NaiveBayesPredictor (argv[0]);
        try {
            for (int i = 1; i < argv.length; ++i) {
                nbp.loadModel(new File (argv[i]));
            }

            for (Predictor p : nbp.eval()) {
                System.out.println(p.getLabel()+"\t"+p.getScore());
            }
        }
        finally {
            nbp.close();
            Index.shutdown();
        }
    }
}
