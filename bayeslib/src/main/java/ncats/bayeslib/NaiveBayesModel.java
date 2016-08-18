package ncats.bayeslib;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import tripod.fingerprint.PCFP;

/**
 * rip this off from https://spotlite.nih.gov/opensource/tox21baseline
 */
public class NaiveBayesModel {
    static final Logger logger =
        Logger.getLogger(NaiveBayesModel.class.getName());

    static class Gap implements Comparable<Gap> {
        double gap;
        int bit;

        Gap (double gap, int bit) {
            this.gap = gap;
            this.bit = bit;
        }

        public int compareTo (Gap g) {
            // reverse ordering
            double a = Math.abs(gap), b = Math.abs(g.gap);
            if (a < b) return 1;
            if (a > b) return -1;
            return 0;
        }
        public boolean equals (Object obj) {
            if (obj instanceof Gap) {
                return bit == ((Gap)obj).bit;
            }
            return false;
        }

        public String toString () {
            return "{bit:"+bit+",gap:"+String.format("%1$f",gap)+"}";
        }
    }
    
    String name;
    int[] pos = new int[PCFP.FP_SIZE];
    int[] neg = new int[PCFP.FP_SIZE];
    int count = 0, npos = 0;

    double prior; // positive prior
    BitSet bitSelection = new BitSet ();

    public NaiveBayesModel (String name) {
        this (name, -1.);
    }

    public NaiveBayesModel (String name, double prior) {
        if (name == null)
            throw new IllegalArgumentException ("Invalid model name");
        this.name = name;
    }

    public String getName () { return name; }
    public void setPrior (double prior) {
        if (prior > 1.)
            throw new IllegalArgumentException
                ("Prior probibility can't be >1");
        this.prior = prior;
    }
    public double getPrior () { return prior; }

    public void add (boolean isPos, BitSet bits) {
        if (isPos) {
            for (int b = bits.nextSetBit(0); b >= 0;
                 b = bits.nextSetBit(b+1))
                ++pos[b];
            ++npos;
        }
        else {
            for (int b = bits.nextSetBit(0); b >= 0;
                 b = bits.nextSetBit(b+1))
                ++neg[b];
        }
        ++count;
    }

    public void bitSelection (int k) {
        bitSelection (k, 10, 20);
    }

    public void bitSelection (int k, int minpos, int minneg) {
        if (k < 0 || k > PCFP.FP_SIZE)
            throw new IllegalArgumentException ("Bogus bit selection: "+k);

        if (minpos < 0 || minneg < 0) 
            throw new IllegalArgumentException
                ("Either minpos or minneg is bogus");

        int nneg = count - npos;

        PriorityQueue<Gap> gaps = new PriorityQueue<Gap>();
        for (int i = 0; i < PCFP.FP_SIZE; ++i) {
            if (pos[i] >= minpos) {
                double p = (double)pos[i]/npos;
                gaps.add(new Gap (p, i));
            }

            if (neg[i] >= minneg) {
                double n = (double)neg[i]/nneg;
                gaps.add(new Gap (-n, i));
            }
        }

        bitSelection.clear();
        int kpos = 0, kneg = 0;
        int maxkpos = k/4; //(int)((double)npos*k/count + .5);
        int maxkneg = k - maxkpos;

        for (Gap g; (g = gaps.poll()) != null; ) {
            /*
             * here we make sure there are minpos of +'s and minneg -'s
             * in the top k gaps
             */
            if (g.gap < 0.) { // neg
                if (kneg < maxkneg) {
                    bitSelection.set(g.bit);
                    ++kneg;
                }
            }
            else { // pos
                if (kpos < maxkpos) {
                    bitSelection.set(g.bit);
                    ++kpos;
                }
            }
        }
    }

    /**
     * Return posterior probability of positive given the set of
     * bits.
     */
    public double getPosterior (BitSet bits) {
        if (count == 0) {
            throw new IllegalStateException
                ("Model has no training samples!");
        }

        double tpos = 0., tneg = 0.;
        double dpos = npos + 2;
        double dneg = (count-npos) + 2;

        // calculate likelihood probabilities
        for (int b = bits.nextSetBit(0); b>= 0; b = bits.nextSetBit(b+1)) {
            if (bitSelection.isEmpty() || bitSelection.get(b)) {
                // laplace smoothing..
                tpos += Math.log((pos[b]+1)/dpos);
                tneg += Math.log((neg[b]+1)/dneg);
            }
        }

        tpos = Math.exp(tpos);
        tneg = Math.exp(tneg);

        double pp = prior;
        if (pp <= 0) { // estimate from sample
            pp = (double)npos / count;
        }
        double pn = 1. - pp;

        // posterior
        return tpos*pp / (tpos*pp+tneg*pn);
    }

    public void write (OutputStream os) throws IOException {
        Properties props = new Properties ();
        props.put("name", name);
        props.put("count", String.valueOf(count));
        props.put("npos", String.valueOf(npos));
        props.put("prior", String.format("%1$.3f", prior));
        { StringBuilder sb = new StringBuilder ();
            for (int i = 0; i < pos.length; ++i) 
                if (pos[i] > 0) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(String.valueOf(i)
                              +":"+String.valueOf(pos[i]));
                }
            props.put("pos", sb.toString());
        }
        { StringBuilder sb = new StringBuilder ();
            for (int i = 0; i < pos.length; ++i) 
                if (neg[i] > 0) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(String.valueOf(i)
                              +":"+String.valueOf(neg[i]));
                }
            props.put("neg", sb.toString());
        }
        { StringBuilder sb = new StringBuilder ();
            for (int b = bitSelection.nextSetBit(0); b >= 0;
                 b = bitSelection.nextSetBit(b+1)) {
                if (sb.length() > 0) sb.append(",");
                sb.append(b);
            }
            props.put("bits", sb.toString());
        }
        props.store(os, "Generated by "+getClass().getName());
    }

    public String save () throws IOException {
        String fname = name+".model";
        save (fname);
        return fname;
    }
    
    public void save (String name) throws IOException {
        FileOutputStream fos = new FileOutputStream (name);
        write (fos);
        fos.close();
    }

    @Override
    public int hashCode () { return name.hashCode(); }
    public boolean equals (Object obj) {
        if (obj instanceof NaiveBayesModel) {
            return name.equals(((NaiveBayesModel)obj).getName());
        }
        return false;
    }

    public String toString () { return name; }

    public static NaiveBayesModel load (String file) throws IOException {
        return load (new FileInputStream (file));
    }

    public static NaiveBayesModel load (File file) throws IOException {
        return load (new FileInputStream (file));
    }

    public static NaiveBayesModel load (InputStream is) throws IOException {
        Properties props = new Properties ();
        props.load(is);
        NaiveBayesModel model = new NaiveBayesModel (props.getProperty("name"));
        model.count = Integer.parseInt(props.getProperty("count"));
        model.prior = Double.parseDouble(props.getProperty("prior"));
        model.npos = Integer.parseInt(props.getProperty("npos"));

        String bits = props.getProperty("bits");
        if (bits != null) {
            for (String b : bits.split(",")) {
                model.bitSelection.set(Integer.parseInt(b));
            }
        }

        parseArray (model.pos, props.getProperty("pos"));
        parseArray (model.neg, props.getProperty("neg"));
        return model;
    }

    static void parseArray (int[] any, String str) {
        for (String tok : str.split(",")) {
            String[] b = tok.split(":");
            if (b.length == 2)
                any[Integer.parseInt(b[0])] = Integer.parseInt(b[1]);
        }
    }
}
