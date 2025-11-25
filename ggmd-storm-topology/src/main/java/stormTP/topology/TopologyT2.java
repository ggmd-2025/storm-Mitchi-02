package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import stormTP.operator.Exit2Bolt;
import stormTP.operator.InputStreamSpout;
import stormTP.operator.MyTortoiseBolt;

/**
 * Topology T2: Filter a single tortoise and enrich with name and cumulative cells
 */
public class TopologyT2 {

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

		// Add exit bolt to output filtered data
		builder.setBolt("exit", new Exit2Bolt(portOUTPUT), nbExecutors)
			.shuffleGrouping("tortoise");

		// Create configuration and submit topology
		Config config = new Config();
		StormSubmitter.submitTopology("topoT2", config, builder.createTopology());
	}
}
