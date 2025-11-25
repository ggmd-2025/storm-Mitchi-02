package stormTP.operator;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

/**
 * Windowed bolt that tracks rank evolution over time (Storm 1.0.2 compatible)
 * Compares rank at window start vs end to determine progression
 * Note: Uses in-memory state instead of KeyValueState for Storm 1.0.2 compatibility
 */
public class RankEvolutionBolt extends BaseWindowedBolt {

	private static final long serialVersionUID = 4262369370788107352L;
	private static Logger logger = Logger.getLogger("RankEvolutionBoltLogger");
	private Map<String, String> state; // In-memory state for Storm 1.0.2
	private OutputCollector collector;
	private ObjectMapper mapper;

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
			JsonNode firstJson = mapper.readTree(firstJsonStr);

			int id = firstJson.get("id").asInt();
			String nom = firstJson.get("nom").asText();
			String firstRang = firstJson.get("rang").asText();

			// Parse last tuple
			String lastJsonStr = lastTuple.getValueByField("json").toString();
			JsonNode lastJson = mapper.readTree(lastJsonStr);

			String lastRang = lastJson.get("rang").asText();

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

			// Store last rank for next window comparison (in-memory)
			state.put("firstRank_" + id, lastRang);

			// Get current timestamp
			String timestamp = Instant.now().toString();

			// Build output JSON
			ObjectNode output = mapper.createObjectNode();
			output.put("id", id);
			output.put("nom", nom);
			output.put("date", timestamp);
			output.put("evolution", evolution);

			String result = mapper.writeValueAsString(output);
			logger.info("Evolution for tortoise " + id + " (" + nom + "): " + evolution +
				" (from " + firstRang + " to " + lastRang + ")");

			// Emit with all tuples in window for proper acknowledgement
			collector.emit(inputWindow.get(), new Values(result));

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
	@SuppressWarnings("rawtypes")
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		this.mapper = new ObjectMapper();
		this.state = new HashMap<>(); // Initialize in-memory state
		logger.info("RankEvolutionBolt initialized (Storm 1.0.2 compatible)");
	}

	@Override
	public void cleanup() {
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		return null;
	}
}
