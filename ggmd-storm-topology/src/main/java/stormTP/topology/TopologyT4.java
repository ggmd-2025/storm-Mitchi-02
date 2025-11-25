package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import stormTP.operator.ComputeBonusBolt;
import stormTP.operator.Exit4Bolt;
import stormTP.operator.GiveRankBolt;
import stormTP.operator.InputStreamSpout;
import stormTP.operator.MyTortoiseBolt;

/**
 * Topology T4: Calculate cumulative bonus points for filtered tortoise every 15 observations
 */
public class TopologyT4 {

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

		// Add ComputeBonusBolt with fieldsGrouping on "json" for state consistency
		builder.setBolt("bonus", new ComputeBonusBolt(), nbExecutors)
			.fieldsGrouping("rank", new Fields("json"));

		// Add exit bolt to output bonus data
		builder.setBolt("exit", new Exit4Bolt(portOUTPUT), nbExecutors)
			.shuffleGrouping("bonus");

		// Create configuration and submit topology
		Config config = new Config();
		StormSubmitter.submitTopology("topoT4", config, builder.createTopology());
	}
}
