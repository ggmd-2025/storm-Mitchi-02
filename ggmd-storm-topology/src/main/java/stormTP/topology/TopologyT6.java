package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseWindowedBolt.Duration; // Import direct de Duration
import org.apache.storm.tuple.Fields;

import stormTP.operator.InputStreamSpout;
import stormTP.operator.GiveRankBolt;
import stormTP.operator.RankEvolutionBolt;
import stormTP.operator.Exit6Bolt;

public class TopologyT6 {

    public static void main(String[] args) throws Exception {

        // Gestion basique des arguments si non fournis
        int portINPUT = (args.length > 0) ? Integer.parseInt(args[0]) : 9001;
        int portOUTPUT = (args.length > 1) ? Integer.parseInt(args[1]) : 9002;

        TopologyBuilder builder = new TopologyBuilder();

        // 1. Source
        builder.setSpout("masterStream", new InputStreamSpout("127.0.0.1", portINPUT));

        // 2. Calcul des Rangs
        // Hypothèse : GiveRankBolt émet ("id", "top", "nom", "points", "rang"...)
        builder.setBolt("rank", new GiveRankBolt(), 1)
               .shuffleGrouping("masterStream");
        
        // 3. Evolution (Fenêtre de 10s, glissante toutes les 2s)
        int idToMonitor = 2; 
        
        builder.setBolt("evolution", 
                new RankEvolutionBolt(idToMonitor)
                    // CORRECTION ICI : Utiliser .withWindow(Longueur, Intervalle)
                    .withWindow(Duration.seconds(10), Duration.seconds(2)), 
                1)
               // CORRECTION ICI : fieldsGrouping obligatoire pour la cohérence temporelle par objet
               .fieldsGrouping("rank", new Fields("id")); 

        // 4. Sortie
        builder.setBolt("exit", new Exit6Bolt(portOUTPUT), 1)
               .shuffleGrouping("evolution");

        Config config = new Config();
        
        // Si vous testez en local via IDE, utilisez LocalCluster au lieu de StormSubmitter
        // Sinon pour le cluster :
        StormSubmitter.submitTopology("topoTP6", config, builder.createTopology());
    }
}