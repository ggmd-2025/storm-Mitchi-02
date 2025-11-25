package stormTP.operator;

import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

import java.io.StringReader;

/**
 * Stateful windowed bolt that tracks rank evolution over time
 * Compares rank at window start vs end to determine progression
 */
public class RankEvolutionBolt extends BaseStatefulWindowedBolt<KeyValueState<String, String>> {

	private static final long serialVersionUID = 4262369370788107352L;
	private static Logger logger = Logger.getLogger("RankEvolutionBoltLogger");
	private KeyValueState<String, String> kvState;
	private OutputCollector collector;

	@Override
	public void execute(TupleWindow inputWindow) {
		try {
			if (inputWindow.get().isEmpty()) {
				return;
			}

			// Get first and last tuples from window
			Tuple firstTuple = inputWindow.get().get(0);
			Tuple lastTuple = inputWindow.get().get(inputWindow.get().size() - 1);

			// Parse first tuple
			String firstJsonStr = firstTuple.getValueByField("json").toString();
			JsonReader firstReader = Json.createReader(new StringReader(firstJsonStr));
			JsonObject firstJson = firstReader.readObject();
			firstReader.close();

			int id = firstJson.getInt("id");
			String nom = firstJson.getString("nom");
			String firstRang = firstJson.getString("rang");

			// Parse last tuple
			String lastJsonStr = lastTuple.getValueByField("json").toString();
			JsonReader lastReader = Json.createReader(new StringReader(lastJsonStr));
			JsonObject lastJson = lastReader.readObject();
			lastReader.close();

			String lastRang = lastJson.getString("rang");

			// Parse rank numbers (remove 'ex' suffix)
			int firstRankNum = Integer.parseInt(firstRang.replace("ex", ""));
			int lastRankNum = Integer.parseInt(lastRang.replace("ex", ""));

			// Determine evolution
			String evolution;
			if (lastRankNum < firstRankNum) {
				// Rank improved (lower number = better)
				evolution = "En progression";
			} else if (lastRankNum == firstRankNum) {
				// Rank stayed same
				evolution = "Constant";
			} else {
				// Rank worsened (higher number = worse)
				evolution = "En régression";
			}

			// Store first rank for next window
			kvState.put("firstRank_" + id, lastRang);

			// Get current timestamp
			String timestamp = Instant.now().toString();

			// Build output JSON
			JsonObjectBuilder output = Json.createObjectBuilder();
			output.add("id", id);
			output.add("nom", nom);
			output.add("date", timestamp);
			output.add("evolution", evolution);

			JsonObject result = output.build();
			logger.info("Evolution for tortoise " + id + " (" + nom + "): " + evolution +
				" (from " + firstRang + " to " + lastRang + ")");

			// Emit with all tuples in window for proper acknowledgement
			collector.emit(inputWindow.get(), new Values(result.toString()));

		} catch (Exception e) {
			System.err.println("Error in RankEvolutionBolt: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("json"));
	}

	@Override
	public void initState(KeyValueState<String, String> state) {
		kvState = state;
		logger.info("RankEvolutionBolt state initialized");
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void cleanup() {
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		return null;
	}
}
