package ncats.bayeslib;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import tripod.fingerprint.PCFP;

public class BayesRank extends Bayes {
    static final Logger logger = Logger.getLogger(BayesRank.class.getName());

    public static class Rank implements Comparable<Rank> {
        public String term;
        public double score;

        Rank (String term, double score) {
            this.term = term;
            this.score = score;
        }

        public int compareTo (Rank p) {
            if (p.score < score) return -1;
            if (p.score > score) return 1;
            return term.compareTo(p.term);
        }
    }

    ObjectMapper mapper = new ObjectMapper ();
    List<Term> terms = new ArrayList<Term>();

    public BayesRank (String index) throws IOException {
        super (index);
    }

    public void load (File file) throws Exception {
        if (!file.exists()) {
        }
        else if (file.isDirectory()) {
            // recursive
            for (File f : file.listFiles())
                load (f);
        }
        else {
            try {
                FileReader reader = new FileReader (file);              
                Term t = mapper.readValue(reader, Term.class);
                if (t.count < 5) {
                    logger.warning("Term "+t.term
                                   +" not loaded because it has "+t.count
                                   +" (<5) assays!");
                }
                else {
                    logger.info("Term "+t.term+" loaded...");
                    terms.add(t);
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
                logger.warning("File "+file+" is not a BAO model!");
            }
        }
    }

    public int size () { return terms.size(); }

    public List<Rank> eval () throws Exception {
        final Map<String, Double> cumulative = new HashMap<String, Double>();
        final Map<String, int[]> ranks = new HashMap<String, int[]>();
        try (MolIndex.MolEntryIterator it = index.iterator()) {
            while (it.hasNext()) {
                MolIndex.MolEntry me = it.next();
                //System.out.println("["+me.getKey()+"]");
                final Map<String, Double> scores = new HashMap<String, Double>();
                for (Term t : terms) {
                    double score = t.eval(me.getFpBits());
                    //System.out.println(t.term+" "+score);
                    Double total = cumulative.get(t.term);
                    scores.put(t.term, score);
                    cumulative.put
                        (t.term, total == null ? score : (total+score));
                }
                String[] order = scores.keySet().toArray(new String[0]);
                Arrays.sort(order, new Comparator<String>() {
                        public int compare (String l1, String l2) {
                            Double d1 = scores.get(l1);
                            Double d2 = scores.get(l2);
                            if (d2 < d1) return -1;
                            if (d2 > d1) return 1;
                            return l1.compareTo(l2);
                        }
                    });
                for (int i = 0; i < order.length; ++i) {
                    int[] r = ranks.get(order[i]);
                    if (r == null)
                        ranks.put(order[i], r = new int[terms.size()]);
                    r[i]++;
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
        
        List<Rank> results = new ArrayList<Rank>();
        for (String l : labels)
            results.add(new Rank (l, cumulative.get(l)));

        /*
        for (Map.Entry<String, int[]> me : ranks.entrySet()) {
            System.out.print(me.getKey());
            int[] r = me.getValue();
            for (int i = 0; i < r.length; ++i)
                System.out.print(" "+r[i]);
            System.out.println();
        }

        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < terms.size(); ++i) {
            int max = 0;

            List<String> tt = new ArrayList<String>();
            for (Map.Entry<String, int[]> me : ranks.entrySet()) {
                String t = me.getKey();
                if (!seen.contains(t)) {
                    int v = me.getValue()[i];
                    if (v > max) {
                        max = v;
                        tt.clear();
                        tt.add(t);
                    }
                    else if (v > 0 && v == max)
                        tt.add(t);
                }
            }
            
            for (String t : tt) {
                results.add(new Rank (t, max));
                seen.add(t);
            }
        }
        */
        
        return results;
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println
                ("Usage: BayesRank INDEX MODELS..."
                 +"where INDEX is the input built by MolIndex$Build\n"
                 +"and MODELS are model files generated by BAOTrain.\n"
                 +"Note that MODELS can a directory contains all the models\n"
                 +"or individual model files.");
            System.exit(1);
        }
        
        BayesRank rank = new BayesRank (argv[0]);
        try {
            for (int i = 1; i < argv.length; ++i) {
                try {
                    System.out.println("loading "+argv[i]+"...");
                    rank.load(new File (argv[i]));
                }
                catch (Exception ex) {
                    logger.log(Level.SEVERE, "Can't load file: "+argv[i], ex);
                }
            }
            logger.info(rank.size()+" model(s) loaded!");
            
            List<Rank> ranked = rank.eval();
            
            System.out.println("---- final ranking ----");
            for (Rank r : ranked) {
                System.out.println(r.term+" "+r.score);
            }
        }
        finally {
            rank.close();
            Index.shutdown();
        }
    }
}
