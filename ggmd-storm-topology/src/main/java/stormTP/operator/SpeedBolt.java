package stormTP.operator;

import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

import java.io.StringReader;
import java.text.DecimalFormat;

/**
 * Windowed stateless bolt that calculates average speed over a sliding window
 * Window: 10 tuples with slide of 5 tuples
 * Note: Window configuration is handled by the topology builder
 */
public class SpeedBolt extends BaseWindowedBolt {

	private static final long serialVersionUID = 4262369370788107350L;
	private static Logger logger = Logger.getLogger("SpeedBoltLogger");
	private OutputCollector collector;

	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void execute(TupleWindow inputWindow) {
		try {
			if (inputWindow.get().isEmpty()) {
				return;
			}

			// Parse first and last tuples to get range info
			int minCells = Integer.MAX_VALUE;
			int maxCells = Integer.MIN_VALUE;
			int id = -1;
			String nom = "";
			long firstTop = -1;
			long lastTop = -1;

			for (Tuple tuple : inputWindow.get()) {
				String jsonStr = tuple.getValueByField("json").toString();

				// Parse JSON
				JsonReader reader = Json.createReader(new StringReader(jsonStr));
				JsonObject inputJson = reader.readObject();
				reader.close();

				id = inputJson.getInt("id");
				nom = inputJson.getString("nom");
				long top = inputJson.getJsonNumber("top").longValue();
				int nbCells = inputJson.getInt("nbCellsParcourus");

				// Track first and last tops
				if (firstTop == -1) {
					firstTop = top;
				}
				lastTop = top;

				// Track min and max cells
				if (nbCells < minCells) {
					minCells = nbCells;
				}
				if (nbCells > maxCells) {
					maxCells = nbCells;
				}
			}

			// Calculate speed: cells traveled / window size
			// Window size is 10 tuples
			double speed = (double) (maxCells - minCells) / 10.0;

			// Format speed to 2 decimal places
			DecimalFormat df = new DecimalFormat("0.00");
			String speedStr = df.format(speed);

			// Build output JSON
			JsonObjectBuilder output = Json.createObjectBuilder();
			output.add("id", id);
			output.add("nom", nom);
			output.add("tops", "t" + firstTop + "-t" + lastTop);
			output.add("vitesse", Double.parseDouble(speedStr));

			JsonObject result = output.build();
			logger.info("Speed for tortoise " + id + " (" + nom + "): " + speedStr + " cells/top");

			// Emit with all tuples in window for proper acknowledgement
			collector.emit(inputWindow.get(), new Values(result.toString()));

		} catch (Exception e) {
			System.err.println("Error in SpeedBolt: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("json"));
	}
}
