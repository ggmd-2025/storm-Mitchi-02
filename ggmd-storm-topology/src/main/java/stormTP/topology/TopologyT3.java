package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import stormTP.operator.Exit3Bolt;
import stormTP.operator.GiveRankBolt;
import stormTP.operator.InputStreamSpout;
import stormTP.operator.MyTortoiseBolt;

/**
 * Topology T3: Calculate and rank all tortoises by their position on track
 */
public class TopologyT3 {

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

		// Add MyTortoiseBolt to filter tortoise ID 3 and enrich data
		builder.setBolt("tortoise", new MyTortoiseBolt(3), nbExecutors)
			.shuffleGrouping("masterStream");

		// Add GiveRankBolt with fieldsGrouping on "top" to ensure state consistency
		builder.setBolt("rank", new GiveRankBolt(), nbExecutors)
			.fieldsGrouping("tortoise", new Fields("json"));

		// Add exit bolt to output ranked data
		builder.setBolt("exit", new Exit3Bolt(portOUTPUT), nbExecutors)
			.shuffleGrouping("rank");

		// Create configuration and submit topology
		Config config = new Config();
		StormSubmitter.submitTopology("topoT3", config, builder.createTopology());
	}
}
