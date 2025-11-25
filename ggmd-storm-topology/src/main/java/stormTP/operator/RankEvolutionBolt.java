package stormTP.operator;

import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RankEvolutionBolt extends BaseWindowedBolt {

    private OutputCollector collector;
    private int idToMonitor; // L'ID de la tortue à surveiller

    public RankEvolutionBolt(int idToMonitor) {
        this.idToMonitor = idToMonitor;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(TupleWindow inputWindow) {
        List<Tuple> tuples = inputWindow.get();
        
        // Variables pour stocker le premier et le dernier état connu dans la fenêtre
        Tuple startTuple = null;
        Tuple endTuple = null;

        // On parcourt la fenêtre pour trouver les tuples de NOTRE tortue
        for (Tuple t : tuples) {
            int id = t.getIntegerByField("id");
            if (id == this.idToMonitor) {
                if (startTuple == null) startTuple = t; // Le plus vieux dans la fenêtre
                endTuple = t; // Le plus récent (sera écrasé jusqu'au dernier)
            }
        }

        // Si on a des données pour cette tortue
        if (startTuple != null && endTuple != null) {
            
            // Récupération des rangs (attention "1ex" -> 1)
            int rankStart = parseRank(startTuple.getStringByField("rang"));
            int rankEnd = parseRank(endTuple.getStringByField("rang"));
            
            String evolution = "Constant";
            if (rankEnd < rankStart) {
                evolution = "En progression"; // ex: passé de 5ème à 2ème
            } else if (rankEnd > rankStart) {
                evolution = "En régression"; // ex: passé de 2ème à 5ème
            }

            // Récupération des infos pour la sortie
            int top = endTuple.getIntegerByField("top");
            String nom = endTuple.getStringByField("nom");
            
            // Formatage de la date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateStr = sdf.format(new Date());

            // Emission vers Exit6Bolt
            // Schema attendu : (id, top, nom, date, evolution) 
            // (Note: j'inclus 'top' pour le debug même si le prompt ne le liste pas explicitement dans le tuple retourné, Exit6Bolt l'attendra)
            collector.emit(new Values(idToMonitor, top, nom, dateStr, evolution));
        }
    }

    private int parseRank(String rankStr) {
        try {
            return Integer.parseInt(rankStr.replace("ex", ""));
        } catch (Exception e) {
            return 999;
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("id", "top", "nom", "date", "evolution"));
    }
}