package stormTP.operator;

import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/**
 * Stateful bolt that calculates cumulative bonus points every 15 observations
 */
public class ComputeBonusBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

	private static final long serialVersionUID = 4262369370788107348L;
	private static Logger logger = Logger.getLogger("ComputeBonusBoltLogger");
	private KeyValueState<String, String> kvState;
	private OutputCollector collector;
	private ObjectMapper mapper;

	@Override
	public void execute(Tuple t) {
		try {
			String jsonStr = t.getValueByField("json").toString();
			JsonNode inputJson = mapper.readTree(jsonStr);

			int id = inputJson.get("id").asInt();
			long top = inputJson.get("top").asLong();
			String rang = inputJson.get("rang").asText();
			int total = inputJson.get("total").asInt();
			int maxcel = inputJson.get("maxcel").asInt();

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
				ObjectNode output = mapper.createObjectNode();
				output.put("id", id);
				output.put("tops", topsRange);
				output.put("score", currentScore);

				String result = mapper.writeValueAsString(output);
				logger.info("Bonus for tortoise " + id + " at top " + top + ": bonus=" + bonus + ", total=" + currentScore);

				collector.emit(t, new Values(result));
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
		this.mapper = new ObjectMapper();
	}

	@Override
	public void cleanup() {
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		return null;
	}
}
