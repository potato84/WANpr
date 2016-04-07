import java.util.ArrayList;
import java.util.Hashtable;

/**
 * deal with LSA messages.
 * build LSA database
 * build the topology
 */
public class LSADatabaseThread implements Runnable {
    private ArrayList<Packet> queue = new ArrayList<>();
    public LSADatabaseThread (Packet p){
        queue.add(p);
    }
    public void run(){
        while(!this.queue.isEmpty()){
            Packet recv = this.queue.get(0);
            LSAMessage cur = recv.getLSA();
            long now = System.currentTimeMillis();

            if((now - cur.getTime_created()) > Config.AGE_LIMITATION){
                //if age is larger than the limitation, too old, ignore
            }else{
                //todo the sequence number would be the other condition to judge whether the message is too old
                int id = Integer.parseInt(cur.getLinkID());
                LSADatabase workdb = sLSRP.lsadb.get(id);
                if(workdb != null){
                    if(workdb.seqno < cur.getSeqno()){
                        //update lsa database
                        workdb.seqno = cur.getSeqno();

                        //update linkstates
                        ArrayList<Links> updated = cur.getLinkArray();
                        for(int i=0; i<updated.size(); i++){
                            String tmp_key = updated.get(i).source + "_" + updated.get(i).destination;
                            // no matter it existed or not, will update the links
                            sLSRP.links.put(tmp_key, updated.get(i));
                        }
                        sLSRP.lsadb.put(id, workdb);
                        //recalculate routing table
                        int nodecount = CountNodes();
                        SetupGraph(nodecount);
                    }
                }else{
                    // no entry in lsa database, add new one
                    LSADatabase newentry = new LSADatabase();
                    newentry.fromLSAMessage(cur);
                    sLSRP.lsadb.put(Integer.parseInt(cur.getLinkID()),newentry);

                    // update linkstates
                    ArrayList<Links> updated = cur.getLinkArray();
                    for(int i=0; i<updated.size(); i++){
                        String tmp_key = updated.get(i).source + "_" + updated.get(i).destination;
                        // no matter it existed or not, will update the links
                        sLSRP.links.put(tmp_key, updated.get(i));
                    }
                    sLSRP.lsadb.put(id, workdb);

                    // recalcaulate routing table
                    int nodecount = CountNodes();
                    SetupGraph(nodecount);
                }
            }
            this.queue.remove(0);
        }
    }

    private int CountNodes (){
        Hashtable<Integer,Integer> nodes = new Hashtable<>();
        for(String j: sLSRP.links.keySet()){
            String [] records = j.split("_");
            nodes.put(Integer.parseInt(records[0]),0);
            nodes.put(Integer.parseInt(records[1]),0);
        }

        return nodes.size();
    }

    private void SetupGraph (int nodecount) {
        WeightedGraph t = new WeightedGraph (nodecount);

        for(int i = 1; i<=nodecount; i++){
            String router = "Router_" + Integer.toString(i);
            t.setLabel(i,router);
        }

        for (String j: sLSRP.links.keySet()){
            Links worklink = sLSRP.links.get(j);
            t.addEdge(worklink.source, worklink.destination,worklink.cost);
            t.addEdge(worklink.destination, worklink.source,worklink.cost);
        }

        t.print();

        final int [] pred = Dijkstra.dijkstra (t, 0);
        for (int n=0; n<6; n++) {
            Dijkstra.printPath (t, pred, 0, n);
        }

    }
}