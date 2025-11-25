# Solution Guide: Tortoise Race Storm Topology (Part 2)

This guide provides step-by-step commands and implementation instructions starting from **#### Filtrer une tortue** (task.md line 21).

---

## Overview of Tasks

From task.md, you need to implement 5 main features, each with its own bolt, exit bolt, and topology:

1. **T2**: MyTortoiseBolt - Filter and enrich tortoise data
2. **T3**: GiveRankBolt - Calculate tortoise rankings
3. **T4**: ComputeBonusBolt - Calculate cumulative bonus points
4. **T5**: SpeedBolt - Calculate average speed over window
5. **T6**: RankEvolutionBolt - Track rank progression over time

---

## Task T2: Filtrer une tortue (Filter a Tortoise)

**Objective**: Implement a stateless bolt that filters a single tortoise and enriches the data.

### Step 1: Implement MyTortoiseBolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/MyTortoiseBolt.java`

Key requirements:

-   Stateless bolt (implements IRichBolt)
-   Filters tuples by tortoise ID (configurable in constructor)
-   Input schema: `(json)` - parsed as (id, top, tour, cellule, total, maxcel)
-   Output schema: `(id, top, nom, nbCellsParcourus, total, maxcel)`
-   Must assign a custom tortoise name
-   Calculate cumulative cells traveled: `cellule + (tour * maxcel)`

Pattern to follow:

-   Parse incoming JSON using `javax.json`
-   Extract fields: id, top, tour, cellule, total, maxcel
-   Check if id matches filter ID
-   Calculate cells traveled
-   Build new JSON with output schema
-   Emit tuple

### Step 2: Implement Exit2Bolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit2Bolt.java`

Key requirements:

-   Terminal bolt (implements IRichBolt)
-   Constructor takes: `int port`
-   Input schema: `(id, top, nom, nbCellsParcourus, total, maxcel)`
-   Output: JSON tuple to TCP port using `StreamEmiter`
-   Must preserve all fields in output JSON

### Step 3: Create TopologyT2

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT2.java`

Key requirements:

-   Topology structure:
    ```
    InputStreamSpout (9001) → MyTortoiseBolt (id=3) → Exit2Bolt (9005)
    ```
-   Constructor parameters: `portINPUT` and `portOUTPUT`
-   Use `shuffleGrouping()` for data distribution
-   Configuration: Use default Storm config (from TopologyT1 as template)
-   Submission name: "topoT2"

### Step 4: Build and Test

```bash
# In Terminal 3
mvn package

# Submit topology to Storm
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005

# In Terminal 4 (listener)
cd /ggmd-storm-listner
mvn package
./startListner.sh 9005
```

**Expected Output**: JSON objects with fields: `id, top, nom, nbCellsParcourus, total, maxcel`

Example:

```json
{
    "id": 3,
    "top": 15,
    "nom": "Caroline",
    "nbCellsParcourus": 42,
    "total": 10,
    "maxcel": 145
}
```

---

## Task T3: Calcul du rang (Calculate Rank)

**Objective**: Implement a stateless bolt that calculates and ranks all tortoises.

### Step 1: Implement GiveRankBolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/GiveRankBolt.java`

Key requirements:

-   **Important**: This is a stateful bolt! Must maintain state of all tortoises to rank them
-   Extends `BaseStatefulBolt<KeyValueState<String, JsonArray>>`
-   Input schema: `(json)` - parsed as (id, top, tour, cellule, total, maxcel)
-   Output schema: `(id, top, rang, total, maxcel)`
-   Ranking calculation:
    -   Position on track: `cellule + (tour * maxcel)` (absolute position)
    -   Compare position of all tortoises with same `top`
    -   Rank them 1, 2, 3... based on position
    -   Handle ties with 'ex' suffix: "1ex", "2ex", etc.
-   Must store last known position of each tortoise in state

Algorithm:

1. Receive tuple with (id, top, tour, cellule, total, maxcel)
2. Calculate absolute position: `position = cellule + (tour * maxcel)`
3. Store in state: `state.put("tortoise_" + id, position)`
4. When position value changes for this `top`:
    - Get all positions from state
    - Sort by position (descending)
    - Assign ranks with 'ex' suffix for ties
    - Emit tuple with (id, top, rang, total, maxcel)

### Step 2: Implement Exit3Bolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit3Bolt.java`

Key requirements:

-   Terminal bolt (implements IRichBolt)
-   Constructor takes: `int port`
-   Input schema: `(id, top, rang, total, maxcel)`
-   Output: JSON tuple to TCP port

### Step 3: Create TopologyT3

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT3.java`

Key requirements:

-   Topology structure:
    ```
    InputStreamSpout (9001) → GiveRankBolt → Exit3Bolt (9005)
    ```
-   Use `fieldsGrouping("top")` to ensure same observation goes to same executor
-   This allows state to work correctly (all tuples with same top together)

### Step 4: Build and Test

```bash
# Kill previous topology via Storm UI (KILL button, set 0)
# Then:

mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT3 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Expected Output**:

```json
{"id": 3, "top": 15, "rang": "3", "total": 10, "maxcel": 145}
{"id": 1, "top": 15, "rang": "1ex", "total": 10, "maxcel": 145}
```

---

## Task T4: Affectation des points bonus (Calculate Bonus Points)

**Objective**: Implement a stateful bolt that accumulates bonus points every 15 observations.

### Step 1: Implement ComputeBonusBolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/ComputeBonusBolt.java`

Key requirements:

-   **Stateful bolt**: Extends `BaseStatefulBolt<KeyValueState<String, Integer>>`
-   Must be preceded by: `MyTortoiseBolt` and `GiveRankBolt` in pipeline
-   Input schema: `(id, top, rang, total, maxcel)`
-   Output schema: `(id, tops, score)`
-   Bonus calculation:
    -   Every 15 tops (observations), accumulate bonus points
    -   Bonus points = `total - parseInt(rang)`
    -   Example: 10 total participants, rank "1" = 9 points, rank "2ex" = 8 points
    -   `tops` field format: "t5-t14" (first and last top in the 15-top window)
    -   `score` field: cumulative total of all bonuses earned so far

Algorithm:

1. Maintain state: `currentScore_<id>` and `lastBonusTop_<id>`
2. For each tuple:
    - If `top % 15 == 0` (bonus calculation point):
        - Extract rank from previous tuple's rang field
        - Calculate bonus: `bonus = total - parseInt(rang.replace("ex", ""))`
        - Add to score: `currentScore = state.get("score_" + id, 0) + bonus`
        - Store new score in state
        - Emit: (id, "t{top-14}-t{top}", currentScore)

### Step 2: Implement Exit4Bolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit4Bolt.java`

Key requirements:

-   Terminal bolt (implements IRichBolt)
-   Constructor takes: `int port`
-   Input schema: `(id, tops, score)` OR `(id, top, nom, points)` (check task.md line 56)
-   Note: task.md line 51 says output is (id, tops, score), but line 56 says Exit4Bolt takes (id, top, nom, points)
-   Reconcile by using: Input to Exit4Bolt is `(id, tops, score)` from ComputeBonusBolt
-   Output: JSON to port

### Step 3: Create TopologyT4

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT4.java`

Key requirements:

-   Topology structure:
    ```
    InputStreamSpout (9001)
      → MyTortoiseBolt (id=3)
      → GiveRankBolt
      → ComputeBonusBolt
      → Exit4Bolt (9005)
    ```
-   Use `fieldsGrouping()` for state grouping
-   State should be partitioned by tortoise ID

### Step 4: Build and Test

```bash
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT4 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Expected Output**: (emitted every 15 observations)

```json
{"id": 3, "tops": "t0-t14", "score": 8}
{"id": 3, "tops": "t15-t29", "score": 15}
```

---

## Task T5: Vitesse moyenne (Average Speed with Windowing)

**Objective**: Implement a stateless windowed bolt for speed calculation.

### Step 1: Implement SpeedBolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/SpeedBolt.java`

Key requirements:

-   **Windowed stateless bolt**: Extends `BaseWindowedBolt`
-   Input schema: `(id, top, nom, nbCellsParcourus, total, maxcel)` (from MyTortoiseBolt)
-   Output schema: `(id, nom, tops, vitesse)`
-   Window configuration: 10 tuples window, sliding every 5 tuples
-   Speed calculation:
    -   Collect all tuples in window (10 tuples)
    -   Find min and max `nbCellsParcourus` in window
    -   Speed = `(max - min) / 10 cells per top`
    -   `tops` format: "t{first_top}-t{last_top}"
    -   `vitesse`: decimal with 2 decimal places

Algorithm:

1. Receive TupleWindow with 10 tuples
2. Parse each tuple's JSON
3. Extract: id, nom, top, nbCellsParcourus
4. Find minimum and maximum nbCellsParcourus
5. Calculate speed = (max - min) / 10
6. Get first and last top values
7. Build output tuple: (id, nom, "t{first}-t{last}", speed)

Bolt configuration in topology:

```java
builder.setBolt("speed", new SpeedBolt()
    .withWindow(new Count(10), new Count(5))  // 10-tuple window, slide 5
    , parallelism)
    .shuffleGrouping("tortoiseBolt");
```

### Step 2: Implement Exit5Bolt

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/operator/Exit5Bolt.java`

Key requirements:

-   Terminal bolt (implements IRichBolt)
-   Input schema: `(id, nom, tops, vitesse)` (note: task.md line 69 says (id, top, nom, vitesse))
-   Output to TCP port

### Step 3: Create TopologyT5

Create file: `/storm/examples/ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT5.java`

Key requirements:

-   Topology structure:
    ```
    InputStreamSpout (9001)
      → MyTortoiseBolt (id=3)
      → SpeedBolt (window: 10 tuples, slide 5)
      → Exit5Bolt (9005)
    ```
-   Use `shuffleGrouping()` (no state needed)

### Step 4: Build and Test

```bash
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT5 9001 9005

# In listener terminal:
./startListner.sh 9005
```

**Expected Output**: (emitted every 5 tuples after first 10)

```json
{"id": 3, "nom": "Caroline", "tops": "t0-t9", "vitesse": 3.2}
{"id": 3, "nom": "Caroline", "tops": "t5-t14", "vitesse": 2.8}
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
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT6 9001 9005

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
# Terminal 1: Start cluster
docker compose up

# Terminal 2: Start stream
docker compose exec client /bin/bash
cd /ggmd-storm-stream && mvn package
./startStream.sh tortoise 10 145 9001

# Terminal 3: Build and submit topology (repeat for each T2-T6)
docker compose exec -it client /bin/bash
cd /storm/examples/ggmd-storm-topology
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005

# Terminal 4: Listen to output
docker compose exec -it client /bin/bash
cd /ggmd-storm-listner && mvn package
./startListner.sh 9005

# Stop topology via UI or:
storm kill topoT2 -w 0
```

---

## Important Notes

1. **Modify InputStreamSpout host**: Change `"127.0.0.1"` to `"client"` in topologies (see README.md)
2. **Kill topology before next**: Always stop previous topology before testing new one
3. **Check logs**: Use Storm UI at http://localhost:8081 to monitor topology
4. **State management**: Use `fieldsGrouping()` for stateful bolts to ensure consistent state
5. **JSON parsing**: Use `javax.json` library for JSON manipulation
6. **Tuple schema**: Always declare output fields in `declareOutputFields()`
