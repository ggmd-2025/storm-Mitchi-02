package stormTP.operator;

import java.util.Map;
import java.util.TreeMap;
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
 * Stateful bolt that ranks tortoises by their position on the track
 */
public class GiveRankBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

	private static final long serialVersionUID = 4262369370788107346L;
	private static Logger logger = Logger.getLogger("GiveRankBoltLogger");
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
			int tour = inputJson.getInt("tour");
			int cellule = inputJson.getInt("cellule");
			int total = inputJson.getInt("total");
			int maxcel = inputJson.getInt("maxcel");

			// Calculate absolute position on track
			int position = cellule + (tour * maxcel);

			// Store position in state: key = "pos_<id>", value = position as string
			kvState.put("pos_" + id, String.valueOf(position));

			// Store top and maxcel info for ranking
			kvState.put("top_" + id, String.valueOf(top));
			kvState.put("maxcel_" + id, String.valueOf(maxcel));
			kvState.put("total_" + id, String.valueOf(total));

			// When we receive a tuple for a given top, we need to rank all tortoises
			// Get all positions and rank them
			TreeMap<Integer, Integer> positionToId = new TreeMap<>((a, b) -> Integer.compare(b, a)); // Descending order

			// Collect all positions for this top
			for (int i = 0; i < total; i++) {
				String posStr = kvState.get("pos_" + i);
				if (posStr != null) {
					int pos = Integer.parseInt(posStr);
					positionToId.put(pos, i);
				}
			}

			// Assign ranks with 'ex' suffix for ties
			int rank = 1;
			int prevPosition = -1;
			int sameRankCount = 0;

			for (Map.Entry<Integer, Integer> entry : positionToId.entrySet()) {
				int pos = entry.getKey();
				int tortoiseId = entry.getValue();

				String rankStr;
				if (pos == prevPosition) {
					// Tie: same position as previous
					sameRankCount++;
					rankStr = rank + "ex";
				} else {
					// New position
					rank = rank + sameRankCount;
					sameRankCount = 0;
					prevPosition = pos;
					rankStr = String.valueOf(rank);
				}

				// Emit rank for each tortoise (only if it's the one we're processing)
				if (tortoiseId == id) {
					// Build output JSON
					JsonObjectBuilder output = Json.createObjectBuilder();
					output.add("id", id);
					output.add("top", top);
					output.add("rang", rankStr);
					output.add("total", total);
					output.add("maxcel", maxcel);

					JsonObject result = output.build();
					logger.info("Rank for tortoise " + id + ": " + rankStr);

					collector.emit(t, new Values(result.toString()));
					collector.ack(t);
				}
			}

		} catch (Exception e) {
			System.err.println("Error in GiveRankBolt: " + e.getMessage());
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
		logger.info("GiveRankBolt state initialized");
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
