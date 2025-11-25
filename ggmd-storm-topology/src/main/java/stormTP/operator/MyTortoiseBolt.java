package stormTP.operator;

import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/**
 * Filter a single tortoise by ID and enrich it with custom name and cumulative cells traveled
 */
public class MyTortoiseBolt implements IRichBolt {

	private static final long serialVersionUID = 4262369370788107344L;
	private static Logger logger = Logger.getLogger("MyTortoiseBoltLogger");
	private OutputCollector collector;
	private int tortoiseId;
	private ObjectMapper mapper;

	// Tortoise names for each ID
	private static final String[] TORTOISE_NAMES = {
		"Alice", "Bob", "Caroline", "David", "Eve",
		"Frank", "Grace", "Henry", "Iris", "Jack"
	};

	public MyTortoiseBolt(int tortoiseId) {
		this.tortoiseId = tortoiseId;
		this.mapper = new ObjectMapper();
	}

	@Override
	public void execute(Tuple t) {
		try {
			String jsonStr = t.getValueByField("json").toString();
			JsonNode inputJson = mapper.readTree(jsonStr);

			// Check if this is a wrapped message with "timestamp" and "runners" array
			if (inputJson.has("runners")) {
				// New format: {"timestamp": ..., "runners": [...]}
				JsonNode runners = inputJson.get("runners");

				for (JsonNode runner : runners) {
					long id = runner.get("id").asLong();

					// Filter by tortoise ID
					if (id == tortoiseId) {
						long top = runner.get("top").asLong();
						long tour = runner.get("tour").asLong();
						long cellule = runner.get("cellule").asLong();
						long total = runner.get("total").asLong();
						long maxcel = runner.get("maxcel").asLong();

						// Calculate cumulative cells traveled
						long nbCellsParcourus = cellule + (tour * maxcel);

						// Get tortoise name
						String nom = TORTOISE_NAMES[(int)(id % TORTOISE_NAMES.length)];

						// Build output JSON
						ObjectNode output = mapper.createObjectNode();
						output.put("id", id);
						output.put("top", top);
						output.put("nom", nom);
						output.put("nbCellsParcourus", nbCellsParcourus);
						output.put("total", total);
						output.put("maxcel", maxcel);

						String result = mapper.writeValueAsString(output);
						logger.info("Tortoise " + id + " => " + result + " treated!");

						collector.emit(t, new Values(result));
					}
				}
				collector.ack(t);
			} else {
				// Old format: direct runner object
				long id = inputJson.get("id").asLong();

				// Filter by tortoise ID
				if (id == tortoiseId) {
					long top = inputJson.get("top").asLong();
					long tour = inputJson.get("tour").asLong();
					long cellule = inputJson.get("cellule").asLong();
					long total = inputJson.get("total").asLong();
					long maxcel = inputJson.get("maxcel").asLong();

					// Calculate cumulative cells traveled
					long nbCellsParcourus = cellule + (tour * maxcel);

					// Get tortoise name
					String nom = TORTOISE_NAMES[(int)(id % TORTOISE_NAMES.length)];

					// Build output JSON
					ObjectNode output = mapper.createObjectNode();
					output.put("id", id);
					output.put("top", top);
					output.put("nom", nom);
					output.put("nbCellsParcourus", nbCellsParcourus);
					output.put("total", total);
					output.put("maxcel", maxcel);

					String result = mapper.writeValueAsString(output);
					logger.info("Tortoise " + id + " => " + result + " treated!");

					collector.emit(t, new Values(result));
					collector.ack(t);
				} else {
					collector.ack(t);
				}
			}
		} catch (Exception e) {
			System.err.println("Error in MyTortoiseBolt: " + e.getMessage());
			e.printStackTrace();
			collector.fail(t);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("json"));
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
