package stormTP.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
 * Stateful bolt that ranks tortoises by their position on the track
 */
public class GiveRankBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

	private static final long serialVersionUID = 4262369370788107346L;
	private static Logger logger = Logger.getLogger("GiveRankBoltLogger");
	private KeyValueState<String, String> kvState;
	private OutputCollector collector;
	private ObjectMapper mapper;

	@Override
	public void execute(Tuple t) {
		try {
			String jsonStr = t.getValueByField("json").toString();
			JsonNode inputJson = mapper.readTree(jsonStr);

			long id = inputJson.get("id").asLong();
			long top = inputJson.get("top").asLong();
			long nbCellsParcourus = inputJson.get("nbCellsParcourus").asLong();
			long total = inputJson.get("total").asLong();
			long maxcel = inputJson.get("maxcel").asLong();

			// Store position in state
			kvState.put("pos_" + id, String.valueOf(nbCellsParcourus));
			kvState.put("top_" + id, String.valueOf(top));

			// Collect all positions for ranking
			Map<Long, Long> positions = new HashMap<>();
			for (int i = 0; i < total; i++) {
				String posStr = kvState.get("pos_" + i);
				String topStr = kvState.get("top_" + i);
				if (posStr != null && topStr != null) {
					long tortoiseTop = Long.parseLong(topStr);
					// Only rank tortoises at the same observation point
					if (tortoiseTop == top) {
						positions.put((long)i, Long.parseLong(posStr));
					}
				}
			}

			// Sort by position (descending - higher position = better rank)
			List<Map.Entry<Long, Long>> sortedEntries = new ArrayList<>(positions.entrySet());
			Collections.sort(sortedEntries, new Comparator<Map.Entry<Long, Long>>() {
				@Override
				public int compare(Map.Entry<Long, Long> a, Map.Entry<Long, Long> b) {
					return Long.compare(b.getValue(), a.getValue()); // Descending
				}
			});

			// Assign ranks with tie handling
			int rank = 1;
			long prevPosition = -1;
			Map<Long, String> ranks = new HashMap<>();
			List<Long> tiedIds = new ArrayList<>();

			for (Map.Entry<Long, Long> entry : sortedEntries) {
				long tortoiseId = entry.getKey();
				long position = entry.getValue();

				if (position == prevPosition) {
					// Tied with previous
					tiedIds.add(tortoiseId);
				} else {
					// Process previous tied group
					if (!tiedIds.isEmpty()) {
						String rankStr = tiedIds.size() > 1 ? (rank - 1) + "ex" : String.valueOf(rank - 1);
						for (Long tiedId : tiedIds) {
							ranks.put(tiedId, rankStr);
						}
						rank += tiedIds.size();
						tiedIds.clear();
					}
					// Start new group
					tiedIds.add(tortoiseId);
					prevPosition = position;
				}
			}

			// Process last group
			if (!tiedIds.isEmpty()) {
				String rankStr = tiedIds.size() > 1 ? rank + "ex" : String.valueOf(rank);
				for (Long tiedId : tiedIds) {
					ranks.put(tiedId, rankStr);
				}
			}

			// Emit rank for current tortoise
			String rang = ranks.get(id);
			if (rang == null) {
				rang = "1"; // Default if no other data
			}

			// Build output JSON
			ObjectNode output = mapper.createObjectNode();
			output.put("id", id);
			output.put("top", top);
			output.put("rang", rang);
			output.put("total", total);
			output.put("maxcel", maxcel);

			String result = mapper.writeValueAsString(output);
			logger.info("Rank for tortoise " + id + ": " + rang);

			collector.emit(t, new Values(result));
			collector.ack(t);

		} catch (Exception e) {
			System.err.println("Error in GiveRankBolt: " + e.getMessage());
			e.printStackTrace();
			collector.fail(t);
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
