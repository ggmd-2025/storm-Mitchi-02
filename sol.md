# Solution Guide

## Task T2: Filtrer une tortue (Filter a Tortoise)

**Objective**: Implement a stateless bolt that filters a single tortoise and enriches the data.

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/MyTortoiseBolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit2Bolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT2.java`

```bash
# In Terminal 3
mvn clean package

storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT2 9001 9005

# In Terminal 4 (listener)
cd /ggmd-storm-listner
mvn package
./startListner.sh 9005
```

Output:

```json
{
    "id": 3,
    "top": 2,
    "nom": "David",
    "nbCellsParcourus": 2,
    "total": 10,
    "maxcel": 150
}
```

---

## Task T3: Calcul du rang (Calculate Rank)

**Objective**: Implement a stateless bolt that calculates and ranks all tortoises.

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/GiveRankBolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit3Bolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT3.java`

```bash
#Terminal 3
mvn clean package
storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT3 9001 9005

# Terminal 4:
./startListner.sh 9005
```

**Output**:

```json
{"id":3,"top":2,"rang":"1","total":10,"maxcel":150}
{"id":3,"top":2,"rang":"1","total":10,"maxcel":150}
{"id":3,"top":2,"rang":"1","total":10,"maxcel":150}
```

---

## Task T4: Affectation des points bonus (Calculate Bonus Points)

**Objective**: Implement a stateful bolt that accumulates bonus points every 15 observations.

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/ComputeBonusBolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit4Bolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT4.java`

```bash
#Terminal 3
mvn clean package
storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT4 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Output**:

```json
Received: {"id":3,"tops":"t1-t15","score":9}
Received: {"id":3,"tops":"t1-t15","score":9}
Received: {"id":3,"tops":"t1-t15","score":9}
Received: {"id":3,"tops":"t1-t15","score":9}
Received: {"id":3,"tops":"t1-t15","score":9}
```

---

## Task T5: Vitesse moyenne (Average Speed with Windowing)

**Objective**: Implement a stateless windowed bolt for speed calculation.

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/SpeedBolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit5Bolt.java`

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT5.java`

```bash
#Terminal 3
mvn clean package
storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT5 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Output**:

```json
Received: {"id":3,"nom":"David","tops":"t2-t6","vitesse":0.6}
Received: {"id":3,"nom":"David","tops":"t2-t6","vitesse":0.6}
```

---

## Task T6: Evolution du rang (Rank Evolution with Time Window)

**Objective**: Implement a stateful windowed bolt for rank progression tracking.

### Step 1: Implement RankEvolutionBolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/RankEvolutionBolt.java`

Key requirements:

-   **Stateful windowed bolt**: Extends `BaseStatefulWindowedBolt<KeyValueState<String, String>>`
-   Input schema: `(id, top, rang, total, maxcel)`
-   Output schema: `(id, nom, date, evolution)`
-   Window: 10 seconds (NOT tuples)
-   Evolution determination:
    -   Compare rank at start of window vs end of window (30 seconds mentioned in task.md line 82)
    -   "En progression": rank number decreased (improved) - e.g., "3" → "1"
    -   "Constant": rank number stayed same
    -   "En régression": rank number increased (worsened) - e.g., "1" → "3"
    -   Ignore 'ex' suffix when comparing: "1ex" = "1", "2ex" = "2"
    -   `date` field: ISO-8601 timestamp of when calculation was made

Algorithm:

1. Maintain state: `firstRank_<id>` (rank at window start)
2. For each window (10 seconds of tuples):
    - Get first rank from state (or from first tuple if none)
    - Get last rank from last tuple in window
    - Parse rank numbers (remove 'ex' suffix)
    - Compare: firstRank vs lastRank
    - Determine: progression (+), constant (=), regression (-)
    - Emit: (id, nom, timestamp, evolution_string)
    - Update state with new rank for next window

Window configuration:

```java
builder.setBolt("rankEvo", new RankEvolutionBolt()
    .withWindow(new Time(10, TimeUnit.SECONDS))  // 10-second tumbling window
    , parallelism)
    .fieldsGrouping("rankBolt", new Fields("id"));
```

### Step 2: Implement Exit6Bolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit6Bolt.java`

Key requirements:

-   Terminal bolt (implements IRichBolt)
-   Constructor takes: `int port`
-   Input schema: `(id, nom, date, evolution)` (note: task.md line 86 says (id, top, nom, points) but that seems incorrect)
-   Output to TCP port

### Step 3: Create TopologyT6

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT6.java`

Key requirements:

-   Topology structure:
    ```
    InputStreamSpout (9001)
      → MyTortoiseBolt (id=3)
      → GiveRankBolt
      → RankEvolutionBolt (10-second window)
      → Exit6Bolt (9005)
    ```
-   Use `fieldsGrouping("id")` to keep same tortoise in same executor (required for state)

### Step 4: Build and Test

```bash
mvn clean package
storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT6 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Expected Output**: (emitted every 10 seconds)

```json
{"id": 3, "nom": "Caroline", "date": "2024-11-25T10:30:45Z", "evolution": "En progression"}
{"id": 3, "nom": "Caroline", "date": "2024-11-25T10:30:55Z", "evolution": "Constant"}
```

---

## Quick Reference: Commands Summary

```bash
# Terminal 1: Start cluster (leave running)
docker compose up

# Terminal 2: Start stream (wait for "Server Started ....")
docker compose exec client /bin/bash
cd /ggmd-storm-stream && mvn package
./startStream.sh tortoise 10 150 9001

# Terminal 3: Build and submit topology (run BEFORE stream times out)
docker compose exec -it client /bin/bash
cd /storm/examples/ggmd-storm-topology
mvn clean package
storm jar target/stormTP-0.1-jar-with-dependencies.jar stormTP.topology.TopologyT2 9001 9005

# Terminal 4: Listen to output (run BEFORE topology produces data)
docker compose exec -it client /bin/bash
cd /ggmd-storm-listner && mvn package
./startListner.sh 9005

# View Storm UI: http://localhost:8081

# Stop topology via UI or:
storm kill topoT2 -w 0
```

## Important Timing Notes

The stream producer **waits for a client to connect** before sending data:

1. Stream starts and listens on port 9001
2. Stream waits for InputStreamSpout to connect (blocking at `server.accept()`)
3. Topology must be submitted quickly to establish connection
4. Once connected, stream sends JSON with all runners every 5 seconds (tortoise delay)
5. MyTortoiseBolt filters the array and emits individual tuples for the target tortoise
6. **Listener should be running BEFORE topology is submitted** to avoid losing initial data

---

## Important Notes

1. **JSON Format**: Stream sends `{"timestamp": ..., "runners": [...]}` - MyTortoiseBolt handles parsing the array
2. **Modify InputStreamSpout host**: Already set to `"client"` in topologies (correct for Docker)
3. **Kill topology before next**: Always stop previous topology before testing new one
4. **Check logs**: Use Storm UI at http://localhost:8081 to monitor topology
5. **State management**: Use `fieldsGrouping()` for stateful bolts to ensure consistent state
6. **JSON parsing**: Uses Jackson library (com.fasterxml.jackson.databind) - included in jar-with-dependencies
7. **JAR with dependencies**: Always use `target/stormTP-0.1-jar-with-dependencies.jar` (includes Jackson), not `target/stormTP-0.1.jar`
8. **Tuple schema**: Always declare output fields in `declareOutputFields()`
9. **Broken Pipe Error**: Usually means no client connected to stream producer - ensure topology connects within a few seconds
