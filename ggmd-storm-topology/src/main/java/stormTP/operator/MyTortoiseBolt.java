package stormTP.operator;

import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/**
 * Filter a single tortoise by ID and enrich it with custom name and cumulative cells traveled
 * Uses simple String parsing to avoid external JSON library dependencies
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

	private long extractLong(String json, String key) {
		Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
		Matcher matcher = pattern.matcher(json);
		if (matcher.find()) {
			return Long.parseLong(matcher.group(1));
		}
		return 0;
	}

	private String buildJson(long id, long top, String nom, long nbCellsParcourus, long total, long maxcel) {
		return String.format("{\"id\":%d,\"top\":%d,\"nom\":\"%s\",\"nbCellsParcourus\":%d,\"total\":%d,\"maxcel\":%d}",
			id, top, nom, nbCellsParcourus, total, maxcel);
	}

	@Override
	public void execute(Tuple t) {
		try {
			String jsonStr = t.getValueByField("json").toString();

			// Check if this is a wrapped message with "timestamp" and "runners" array
			if (jsonStr.contains("\"runners\"")) {
				// New format: {"timestamp": ..., "runners": [...]}
				// Use regex to find all runner objects: {"id":N,"top":N,...}
				Pattern runnerPattern = Pattern.compile("\\{\"id\":(\\d+),\"top\":(\\d+),\"tour\":(\\d+),\"cellule\":(\\d+),\"total\":(\\d+),\"maxcel\":(\\d+)\\}");
				Matcher matcher = runnerPattern.matcher(jsonStr);

				boolean found = false;
				while (matcher.find()) {
					long id = Long.parseLong(matcher.group(1));
					long top = Long.parseLong(matcher.group(2));
					long tour = Long.parseLong(matcher.group(3));
					long cellule = Long.parseLong(matcher.group(4));
					long total = Long.parseLong(matcher.group(5));
					long maxcel = Long.parseLong(matcher.group(6));

					// Filter by tortoise ID
					if (id == tortoiseId) {
						// Calculate cumulative cells traveled
						long nbCellsParcourus = cellule + (tour * maxcel);

						// Get tortoise name
						String nom = TORTOISE_NAMES[(int)(id % TORTOISE_NAMES.length)];

						// Build output JSON
						String result = buildJson(id, top, nom, nbCellsParcourus, total, maxcel);
						logger.info("Tortoise " + id + " => " + result + " treated!");

						collector.emit(t, new Values(result));
						found = true;
					}
				}
				collector.ack(t);
			} else {
				// Old format: direct runner object
				long id = extractLong(jsonStr, "id");

				// Filter by tortoise ID
				if (id == tortoiseId) {
					long top = extractLong(jsonStr, "top");
					long tour = extractLong(jsonStr, "tour");
					long cellule = extractLong(jsonStr, "cellule");
					long total = extractLong(jsonStr, "total");
					long maxcel = extractLong(jsonStr, "maxcel");

					// Calculate cumulative cells traveled
					long nbCellsParcourus = cellule + (tour * maxcel);

					// Get tortoise name
					String nom = TORTOISE_NAMES[(int)(id % TORTOISE_NAMES.length)];

					// Build output JSON
					String result = buildJson(id, top, nom, nbCellsParcourus, total, maxcel);
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
