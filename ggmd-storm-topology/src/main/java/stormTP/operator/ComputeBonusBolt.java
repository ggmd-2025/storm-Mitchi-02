package stormTP.operator;

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
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.StringReader;

/**
 * Stateful bolt that calculates cumulative bonus points every 15 observations
 */
public class ComputeBonusBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

	private static final long serialVersionUID = 4262369370788107348L;
	private static Logger logger = Logger.getLogger("ComputeBonusBoltLogger");
	private KeyValueState<String, String> kvState;
	private OutputCollector collector;

	@Override
	public void execute(Tuple t) {
		try {
			String jsonStr = t.getValueByField("json").toString();

			// Parse incoming JSON
			JsonReader reader = Json.createReader(new StringReader(jsonStr));
			JsonObject inputJson = reader.readObject();
			reader.close();

			int id = inputJson.getInt("id");
			long top = inputJson.getJsonNumber("top").longValue();
			String rang = inputJson.getString("rang");
			int total = inputJson.getInt("total");
			int maxcel = inputJson.getInt("maxcel");

			// Check if we're at a bonus calculation point (every 15 observations)
			if (top > 0 && top % 15 == 0) {
				// Parse rank number, removing 'ex' suffix if present
				int rankNum = Integer.parseInt(rang.replace("ex", ""));

				// Calculate bonus: total - rank
				int bonus = total - rankNum;

				// Get current score from state
				String scoreStr = kvState.get("score_" + id);
				int currentScore = (scoreStr != null) ? Integer.parseInt(scoreStr) : 0;
				currentScore += bonus;

				// Store updated score
				kvState.put("score_" + id, String.valueOf(currentScore));

				// Format tops field: "t{top-14}-t{top}"
				long topStart = top - 14;
				String topsRange = "t" + topStart + "-t" + top;

				// Build output JSON
				JsonObjectBuilder output = Json.createObjectBuilder();
				output.add("id", id);
				output.add("tops", topsRange);
				output.add("score", currentScore);

				JsonObject result = output.build();
				logger.info("Bonus for tortoise " + id + " at top " + top + ": bonus=" + bonus + ", total=" + currentScore);

				collector.emit(t, new Values(result.toString()));
				collector.ack(t);
			} else {
				// Not a bonus point, just pass through (ack but don't emit)
				collector.ack(t);
			}

		} catch (Exception e) {
			System.err.println("Error in ComputeBonusBolt: " + e.getMessage());
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
		logger.info("ComputeBonusBolt state initialized");
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
