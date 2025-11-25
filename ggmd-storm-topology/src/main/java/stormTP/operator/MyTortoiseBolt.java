package stormTP.operator;

import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.StringReader;

/**
 * Filter a single tortoise by ID and enrich it with custom name and cumulative cells traveled
 */
public class MyTortoiseBolt implements IRichBolt {

	private static final long serialVersionUID = 4262369370788107344L;
	private static Logger logger = Logger.getLogger("MyTortoiseBoltLogger");
	private OutputCollector collector;
	private int tortoiseId;

	// Tortoise names for each ID
	private static final String[] TORTOISE_NAMES = {
		"Alice", "Bob", "Caroline", "David", "Eve",
		"Frank", "Grace", "Henry", "Iris", "Jack"
	};

	public MyTortoiseBolt(int tortoiseId) {
		this.tortoiseId = tortoiseId;
	}

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

			// Filter by tortoise ID
			if (id == tortoiseId) {
				// Calculate cumulative cells traveled
				int nbCellsParcourus = cellule + (tour * maxcel);

				// Get tortoise name
				String nom = TORTOISE_NAMES[id % TORTOISE_NAMES.length];

				// Build output JSON
				JsonObjectBuilder output = Json.createObjectBuilder();
				output.add("id", id);
				output.add("top", top);
				output.add("nom", nom);
				output.add("nbCellsParcourus", nbCellsParcourus);
				output.add("total", total);
				output.add("maxcel", maxcel);

				JsonObject result = output.build();
				logger.info("Tortoise " + id + " => " + result.toString() + " treated!");

				collector.emit(t, new Values(result.toString()));
				collector.ack(t);
			}
		} catch (Exception e) {
			System.err.println("Error in MyTortoiseBolt: " + e.getMessage());
			e.printStackTrace();
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
