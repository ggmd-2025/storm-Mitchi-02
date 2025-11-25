package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import stormTP.operator.Exit6Bolt;
import stormTP.operator.GiveRankBolt;
import stormTP.operator.InputStreamSpout;
import stormTP.operator.MyTortoiseBolt;
import stormTP.operator.RankEvolutionBolt;

/**
 * Topology T6: Track rank evolution of filtered tortoise over time windows
 * Window: 10-second tumbling window
 */
public class TopologyT6 {

	public static void main(String[] args) throws Exception {
		int nbExecutors = 1;
		int portINPUT = Integer.parseInt(args[0]);
		int portOUTPUT = Integer.parseInt(args[1]);

		// Create spout
		InputStreamSpout spout = new InputStreamSpout("client", portINPUT);

		// Create topology
		TopologyBuilder builder = new TopologyBuilder();

		// Add spout to topology
		builder.setSpout("masterStream", spout);

		// Add MyTortoiseBolt to filter tortoise with ID 3
		builder.setBolt("tortoise", new MyTortoiseBolt(3), nbExecutors)
			.shuffleGrouping("masterStream");

		// Add GiveRankBolt with fieldsGrouping on "json" for state consistency
		builder.setBolt("rank", new GiveRankBolt(), nbExecutors)
			.fieldsGrouping("tortoise", new Fields("json"));

		// Add RankEvolutionBolt with fieldsGrouping for state consistency
		// Window configuration: 10 seconds tumbling window
		builder.setBolt("rankEvo", new RankEvolutionBolt(), nbExecutors)
			.fieldsGrouping("rank", new Fields("json"));

		// Add exit bolt to output rank evolution data
		builder.setBolt("exit", new Exit6Bolt(portOUTPUT), nbExecutors)
			.shuffleGrouping("rankEvo");

		// Create configuration and submit topology
		Config config = new Config();
		StormSubmitter.submitTopology("topoT6", config, builder.createTopology());
	}
}
