package stormTP.operator;

import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import stormTP.stream.StreamEmiter;

/**
 * Exit bolt that sends filtered tortoise data to output port
 */
public class Exit2Bolt implements IRichBolt {

	private static final long serialVersionUID = 4262369370788107345L;
	private OutputCollector collector;
	int port = -1;
	StreamEmiter semit = null;

	public Exit2Bolt(int port) {
		this.port = port;
		this.semit = new StreamEmiter(this.port);
	}

	@Override
	public void execute(Tuple t) {
		try {
			String n = t.getValueByField("json").toString();
			this.semit.send(n);
			collector.ack(t);
		} catch (Exception e) {
			System.err.println("Error in Exit2Bolt: " + e.getMessage());
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer arg0) {
		arg0.declare(new Fields("json"));
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		return null;
	}

	@Override
	public void cleanup() {
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void prepare(Map arg0, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}
}
