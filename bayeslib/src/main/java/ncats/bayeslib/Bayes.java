package ncats.bayeslib;

import java.io.*;
import java.util.*;
import tripod.fingerprint.PCFP;

public class Bayes {
    
    static class Assay implements Comparable<Assay> {
        public final Long aid;
        public int count;
        public int total;
        public double prob; // p(a|t)
        public int[] bins = new int[PCFP.FP_SIZE];
        public Set<Term> terms = new TreeSet<Term>();

        protected Assay () { this (null); }
        public Assay (Long aid) {
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

        public double eval (BitSet bits) {
            double x = 0.;
            double den = count+2;
            for (int b = bits.nextSetBit(0); b>= 0; b = bits.nextSetBit(b+1)) {
                x += Math.log((bins[b]+1)/den);
            }
            return Math.log(prob) + x;
        }
    }

    static class Term implements Comparable<Term> {
        public final String term;
        public int count;
        public double prob;
        public Set<Assay> assays = new TreeSet<Assay>();

        protected Term () {
            this (null);
        }
        public Term (String term) {
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

        public double eval (BitSet bits) {
            double z = 0.;
            int c = 0;
            for (Assay a : assays)
                if (a.count > 10) {
                    z += a.eval(bits);
                    ++c;
                }
            return c == 0 ? -1000000 : (Math.log(prob) + z);
        }
    }

    final protected MolIndex index;

    protected Bayes (String index) throws IOException {
        this.index = new MolIndex (new File (index));   
    }

    public void close () throws IOException {
        index.close();
    }
}
