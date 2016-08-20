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

public class BAOTrain {
    static final Logger logger = Logger.getLogger(BAOTrain.class.getName());

    static class Assay implements Comparable<Assay> {
        public final Long aid;
        public int count; // activity count
        public double prob; // p(a|t)
        public int[] bins = new int[PCFP.FP_SIZE];
        public Set<Term> terms = new TreeSet<Term>();

        Assay (Long aid) {
            this.aid = aid;
        }
        public int hashCode () { return aid.hashCode(); }
        public boolean equals (Object o) {
            if (o instanceof Assay)
                return aid.equals(((Assay)o).aid);
            return false;
        }
        public int compareTo (Assay a) {
            if (aid < a.aid) return -1;
            if (aid > a.aid) return 1;
            return 0;
        }
        
        public Collection<String> getTerms () {
            List<String> col = new ArrayList<String>();
            for (Term t : terms)
                col.add(t.term);
            return col;
        }
    }

    static class Term implements Comparable<Term> {
        public final String term;
        public int count;
        public double prob;
        public Set<Assay> assays = new TreeSet<Assay>();
        
        Term (String term) {
            this.term = term;
        }

        public int hashCode () { return term.hashCode(); }
        public boolean equals (Object o) {
            if (o instanceof Term) {
                return term.equals(((Term)o).term);
            }
            return false;
        }
        
        public int compareTo (Term t) {
            return term.compareTo(t.term);
        }
    }

    final MolIndex index;
    final Map<Long, Assay> assays = new HashMap<Long, Assay>();
    final Map<String, Term> terms = new HashMap<String, Term>();

    public BAOTrain (String index, String anno) throws IOException {
        this.index = new MolIndex (new File (index));

        /*
         * sample format of anno file:
BID,AIDs,BAOIDs
1,602132|588588,BAO_0000357|BAO_0000139|BAO_0000363|BAO_0000697|BAO_0000516
2,489040|588582|602416|540305,BAO_0000219|BAO_0003006|BAO_0000050|BAO_0000722|BAO_0000516
4,588703|588845,BAO_0000219|BAO_0002993|BAO_0000515|BAO_0000050|BAO_0000697
6,602130|602424|588414|588672,BAO_0000516|BAO_0000363|BAO_0000697|BAO_0000357|BAO_0000142
7,602133|588593|602425,BAO_0000516|BAO_0000363|BAO_0000697|BAO_0000357|BAO_0000139
         */
        BufferedReader br = new BufferedReader (new FileReader (anno));
        br.readLine(); // skip header
        for (String line; (line = br.readLine()) != null; ) {
            String[] fields = line.split(",");
            if (fields.length == 3) {
                String[] aids = fields[1].split("\\|");
                String[] bao = fields[2].split("\\|");
                for (String a : aids) {
                    try {
                        Long aid = Long.parseLong(a);
                        if (aid > 0) {
                            Assay assay = assays.get(aid);
                            if (assay == null) {
                                assays.put(aid, assay = new Assay (aid));
                            }
                            
                            for (String t : bao) {
                                Term term = terms.get(t);
                                if (term == null)
                                    terms.put(t, term = new Term (t));
                                ++term.count;
                                assay.terms.add(term);
                                term.assays.add(assay);
                            }
                        }
                    }
                    catch (Exception ex) {
                        logger.warning("Bogus AID: "+a);
                    }
                }
            }
        }
        br.close();
        
        for (Term t : terms.values())
            t.prob = (double)t.count/assays.size();
        for (Assay a : assays.values())
            a.prob = (double)a.terms.size()/terms.size();
    }

    public void close () throws IOException {
        index.close();
    }

    /* sample format:
aid,cid,outcome,score
1002,663743,Active,58
1002,6603229,Active,52
1002,663900,Active,50
1002,665561,Active,50
1002,666554,Active,48
1003,16752637,Active,48
1003,16752638,Active,48
1003,16752652,Active,50
1003,16752655,Active,50
1007,6603008,Inactive,0
1007,6602571,Inactive,0
    */
    public void train (String bioassay) throws Exception {
        InputStream is = new FileInputStream (bioassay);
        train (is);
        is.close();
    }

    public void train (InputStream is) throws Exception {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        br.readLine(); // skip header
        for (String line; (line = br.readLine()) != null; ) {
            String[] fields = line.split(",");
            if (fields.length >= 3) {
                if (fields[0].equals("") || fields[1].equals(""))
                    continue;

                long aid = Long.parseLong(fields[0]);
                Assay assay = assays.get(aid);
                if (assay == null) {
                    logger.warning
                        ("Assay aid "+aid+" doesn't have a BAO annotation!");
                    continue;
                }

                ++assay.count;
                if (!"active".equalsIgnoreCase(fields[2])) {
                    continue;
                }

                MolIndex.MolEntry me = index.get(fields[1]);
                if (me != null) {
                    BitSet bs = me.getFpBits();
                    for (int b = bs.nextSetBit(0);
                         b >= 0; b = bs.nextSetBit(b+1)) {
                        ++assay.bins[b];
                    }
                }
                else {
                    logger.warning("Can't retrieve MolEntry for '"
                                   +fields[1]+"'");
                }
            }
        }
    }

    public void save (String path) throws IOException {
        File file = new File (path);
        file.mkdirs();
        save (file);
    }

    public void save (File dir) throws IOException {
        ObjectWriter writer =
            new ObjectMapper().writerWithDefaultPrettyPrinter();
        for (Term t : terms.values()) {
            System.out.println("saving model "+t.term+"...");
            FileWriter w = new FileWriter (new File (t.term+".model"));
            writer.writeValue(w, t);
            w.close();
        }
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println("Usage: BAOTrain INDEX ANNO [BIOASSAY]");
            System.err.println("where INDEX is the index built by ncats.bayeslib.MolIndex$Build");
            System.err.println("ANNO is the mapping of BAO terms to assays");
            System.err.println("BIOASSAY is the compound assay activity data; if not specified, read from STDIN");
            System.exit(1);
        }

        BAOTrain bt = new BAOTrain (argv[0], argv[1]);
        try {
            if (argv.length > 2)
                bt.train(argv[2]);
            else
                bt.train(System.in);
            bt.save(System.getProperty("bayeslib.path", "."));
        }
        finally {
            bt.close();
            Index.shutdown();
        }
    }
}
