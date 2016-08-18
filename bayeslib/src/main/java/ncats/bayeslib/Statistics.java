package ncats.bayeslib;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 */
public class Statistics {
    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println("Usage: Statistics ANNO BIOASSAY");
            System.exit(1);
        }

        final Map<Long, Set<String>> assays = new TreeMap<Long, Set<String>>();
        BufferedReader br = new BufferedReader (new FileReader (argv[0]));
        br.readLine(); //skip header
        final Map<String, Set<Long>> bao = new TreeMap<String, Set<Long>>();
        
        for (String line; (line = br.readLine()) != null; ) {
            String[] fields = line.split(",");
            if (fields.length == 3) {
                String[] aids = fields[1].split("\\|");
                String[] terms = fields[2].split("\\|");
                for (String a : aids) {
                    try {
                        Long id = Long.parseLong(a);
                        if (id > 0) {
                            Set<String> set = assays.get(id);
                            if (set == null) {
                                assays.put(id, set = new TreeSet<String>());
                            }
                            
                            for (String t : terms) {
                                if (!t.equals("")) {
                                    set.add(t);
                                    Set<Long> aa = bao.get(t);
                                    if (aa == null) {
                                        bao.put(t, aa = new TreeSet<Long>());
                                    }
                                    aa.add(id);
                                }
                            }
                        }
                    }
                    catch (NumberFormatException ex) {
                        System.err.println("** Bogus AID: "+a);
                    }
                }
            }
        }
        br.close();

        System.err.println(bao.size()+" BAO terms spanning "
                           +assays.size()+" assays!");
        String[] terms = bao.keySet().toArray(new String[0]);
        int[] assprofile = new int[5000];
        for (int i = 0; i < terms.length; ++i) {
            Set<Long> ai = bao.get(terms[i]);
            for (int j = i+1; j < terms.length; ++j) {
                Set<Long> aj = bao.get(terms[j]);
                if (ai.equals(aj)) {
                    System.err.println
                        ("** warning: terms "+terms[i]+" and "+terms[j]+
                         " have identical assays: "+ai);
                }
            }
            assprofile[ai.size()]++;
        }
        for (int i = 0; i < assprofile.length; ++i) {
            if (assprofile[i] > 0) {
                System.out.println(i+","+assprofile[i]);
            }
        }
        Arrays.sort(terms, new Comparator<String>() {
                public int compare (String t1, String t2) {
                    int d = bao.get(t2).size() - bao.get(t1).size();
                    if (d == 0) {
                        d = t1.compareTo(t2);
                    }
                    return d;
                }
            });
        /*
        for (String t : terms) {
            Set<Long> ass = bao.get(t);
            System.out.println(t+": "+ass.size());
        }
        */

        br = new BufferedReader
            (new InputStreamReader
             (new GZIPInputStream (new FileInputStream (argv[1]))));
        Map<String, Set<Long>> baopos = new TreeMap<String, Set<Long>>();
        Map<String, Set<Long>> baoneg = new TreeMap<String, Set<Long>>();
        br.readLine(); // skip header
        for (String line; (line = br.readLine()) != null;) {
            String[] fields = line.split(",");
            if (fields.length >= 3) {
                long aid = Long.parseLong(fields[0]);
                Set<String> bt = assays.get(aid);
                if (bt != null && !fields[1].equals("")) {
                    long cid = Long.parseLong(fields[1]);
                    String outcome = fields[2];
                    if (outcome.equalsIgnoreCase("active")) {
                        for (String t : bt) {
                            Set<Long> cids = baopos.get(t);
                            if (cids == null) {
                                baopos.put(t, cids = new TreeSet<Long>());
                            }
                            cids.add(cid);
                        }
                    }
                    else if (outcome.equalsIgnoreCase("inactive")) {
                        for (String t : bt) {
                            Set<Long> cids = baoneg.get(t);
                            if (cids == null) {
                                baoneg.put(t, cids = new TreeSet<Long>());
                            }
                            cids.add(cid);
                        }
                    }
                    else {
                    }
                }
            }
        }
        br.close();

        PrintWriter pw = new PrintWriter (new FileWriter ("bao_terms.csv"));
        pw.println("BAO,Assays,Pos,Neg");
        for (String t : terms) {
            Set<Long> p = baopos.get(t);
            Set<Long> n = baoneg.get(t);
            PrintWriter pos = new PrintWriter (new FileWriter (t+"_POS.txt"));
            if (p != null) {
                for (Long cid : p)
                    pos.println(cid);
            }
            pos.close();
            
            PrintWriter neg = new PrintWriter (new FileWriter (t+"_NEG.txt"));
            if (n != null) {
                for (Long cid : n)
                    neg.println(cid);
            }
            neg.close();
            
            pw.println(t+","+bao.get(t).size()+","
                       +(p != null ? p.size():0)+","
                       +(n != null ? n.size():0));
        }
        pw.close();
    }
}
