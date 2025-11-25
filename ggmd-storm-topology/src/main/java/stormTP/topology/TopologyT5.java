package stormTP.topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseWindowedBolt.Count;

import stormTP.operator.Exit5Bolt;
import stormTP.operator.InputStreamSpout;
import stormTP.operator.MyTortoiseBolt;
import stormTP.operator.SpeedBolt;

/**
 * Topology T5: Calculate average speed of filtered tortoise over sliding window
 * Window: 10 tuples, sliding every 5 tuples
 */
public class TopologyT5 {

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

		// Add SpeedBolt with windowing: 10-tuple window, sliding every 5 tuples
		builder.setBolt("speed", new SpeedBolt()
			.withWindow(new Count(10), new Count(5)), nbExecutors)
			.shuffleGrouping("tortoise");

		// Add exit bolt to output speed data
		builder.setBolt("exit", new Exit5Bolt(portOUTPUT), nbExecutors)
			.shuffleGrouping("speed");

		// Create configuration and submit topology
		Config config = new Config();
		StormSubmitter.submitTopology("topoT5", config, builder.createTopology());
	}
}
