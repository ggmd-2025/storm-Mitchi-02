# Testing Guide for Storm Topology Implementation

## Overview

This guide provides step-by-step instructions to test each topology (T2-T6) with proper timing and debugging.

---

## Pre-Test Checklist

- [ ] Docker daemon running
- [ ] All 15 Java files created (see sol.md for list)
- [ ] Storm cluster can start (`docker compose up` works)
- [ ] Stream producer can compile (`mvn package` in ggmd-storm-stream)
- [ ] Topology can compile (`mvn package` in ggmd-storm-topology)

---

## Test Sequence

### Pre-Test Setup

Start in 4 terminals:

**Terminal 1**: Start Docker cluster
```bash
cd /Users/ilyasbenhammadi/dev/tiw/ggmd/tp/tp3
docker compose up
# Wait until you see "Supervisor started as PID" and other logs stabilize
```

---

### Test T2: Filtrer une tortue (Filter a Tortoise)

**Terminal 2**: Start stream producer
```bash
docker compose exec client /bin/bash
cd /ggmd-storm-stream
mvn package
./startStream.sh tortoise 10 150 9001
# You should see "Server Started ...." and then JSON output
# Keep this running
```

**Terminal 3** (Within 10 seconds): Build and submit topology
```bash
docker compose exec -it client /bin/bash
cd /storm/examples/ggmd-storm-topology
mvn package
# Check compilation succeeds
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005
# You should see topology submission messages
```

**Terminal 4**: Listen to output
```bash
docker compose exec -it client /bin/bash
cd /ggmd-storm-listner
mvn package
./startListner.sh 9005
# You should see JSON output after a few seconds
```

**Expected Output** (in Terminal 4):
```json
{"id":3,"top":5,"nom":"Caroline","nbCellsParcourus":7,"total":10,"maxcel":150}
{"id":3,"top":10,"nom":"Caroline","nbCellsParcourus":12,"total":10,"maxcel":150}
...
```

**Verify**:
- [ ] JSON has correct fields: id, top, nom, nbCellsParcourus, total, maxcel
- [ ] nom is "Caroline" (tortoise ID 3)
- [ ] nbCellsParcourus increases over time
- [ ] No errors in Storm logs (check http://localhost:8081)

**Stop**: Kill topology before next test
```bash
# In Terminal 3:
storm kill topoT2 -w 0
# Wait 5 seconds
```

---

### Test T3: Calcul du rang (Calculate Rank)

**Terminal 2**: Same stream is still running (no restart needed)

**Terminal 3**: Build and submit new topology
```bash
# Kill previous topology first (see above)
cd /storm/examples/ggmd-storm-topology
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT3 9001 9005
```

**Terminal 4**: Restart listener
```bash
cd /ggmd-storm-listner
./startListner.sh 9005
```

**Expected Output**:
```json
{"id":3,"top":5,"rang":"3","total":10,"maxcel":150}
{"id":3,"top":10,"rang":"4","total":10,"maxcel":150}
...
```

**Verify**:
- [ ] JSON has fields: id, top, rang, total, maxcel
- [ ] rang is a number or number with "ex" suffix (e.g., "1", "2ex")
- [ ] rang values make sense (1-10 for 10 tortoises)
- [ ] Same id and total as input

**Stop**: Kill topology
```bash
storm kill topoT3 -w 0
```

---

### Test T4: Affectation des points bonus (Calculate Bonus)

**Terminal 2**: Stream still running

**Terminal 3**: Submit T4 topology
```bash
storm kill topoT3 -w 0  # Kill previous
cd /storm/examples/ggmd-storm-topology
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT4 9001 9005
```

**Terminal 4**: Restart listener
```bash
./startListner.sh 9005
```

**Expected Output** (only emitted every 15 observations):
```json
{"id":3,"tops":"t0-t14","score":7}
{"id":3,"tops":"t15-t29","score":14}
{"id":3,"tops":"t30-t44","score":22}
...
```

**Verify**:
- [ ] Output appears **every 15 observations** (not continuously)
- [ ] JSON has fields: id, tops, score
- [ ] tops format is "t{X}-t{Y}"
- [ ] score increases (cumulative)

**Stop**: Kill topology
```bash
storm kill topoT4 -w 0
```

---

### Test T5: Vitesse moyenne (Average Speed)

**Terminal 2**: Stream still running

**Terminal 3**: Submit T5 topology
```bash
storm kill topoT4 -w 0  # Kill previous
cd /storm/examples/ggmd-storm-topology
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT5 9001 9005
```

**Terminal 4**: Restart listener
```bash
./startListner.sh 9005
```

**Expected Output** (emitted every 5 observations after first 10):
```json
{"id":3,"nom":"Caroline","tops":"t5-t14","vitesse":1.40}
{"id":3,"nom":"Caroline","tops":"t10-t19","vitesse":2.10}
...
```

**Verify**:
- [ ] Output appears **after initial 10 observations**, then **every 5 observations**
- [ ] JSON has fields: id, nom, tops, vitesse
- [ ] vitesse is a decimal number (cells per observation)
- [ ] tops shows correct window range
- [ ] vitesse values are reasonable (0-5 for 150-cell track)

**Stop**: Kill topology
```bash
storm kill topoT5 -w 0
```

---

### Test T6: Evolution du rang (Rank Evolution)

**Terminal 2**: Stream still running

**Terminal 3**: Submit T6 topology
```bash
storm kill topoT5 -w 0  # Kill previous
cd /storm/examples/ggmd-storm-topology
mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT6 9001 9005
```

**Terminal 4**: Restart listener
```bash
./startListner.sh 9005
```

**Expected Output** (emitted every ~10 seconds of observations):
```json
{"id":3,"nom":"Caroline","date":"2024-11-25T10:35:42.123Z","evolution":"En progression"}
{"id":3,"nom":"Caroline","date":"2024-11-25T10:35:52.234Z","evolution":"Constant"}
...
```

**Verify**:
- [ ] JSON has fields: id, nom, date, evolution
- [ ] evolution is one of: "En progression", "Constant", "En régression"
- [ ] date is ISO-8601 format
- [ ] nom is "Caroline"
- [ ] evolution changes based on rank changes

**Stop**: Kill topology
```bash
storm kill topoT6 -w 0
```

---

## Troubleshooting

### Error: "Broken pipe" in stream
**Cause**: No topology connected within timeout
**Fix**:
1. Stop stream (Ctrl+C)
2. Immediately start new stream
3. **Quickly** (within 5 seconds) submit topology
4. Then start listener

### Error: "Connection refused" when submitting topology
**Cause**: Stream not running or listening on wrong port
**Fix**:
1. Check stream is running in Terminal 2
2. Check it says "Server Started ...."
3. Check port is 9001

### Error: "Cannot parse JSON" in bolt logs
**Cause**: Incorrect JSON format or bolt not handling stream format
**Fix**:
1. Check stream output format: should be `{"timestamp": ..., "runners": [...]}`
2. Check MyTortoiseBolt is parsing the array correctly
3. See Storm logs at http://localhost:8081

### Error: "No output from listener"
**Cause**:
- Exit bolt not emitting
- Topology not processing data
- Listener not listening
**Fix**:
1. Check listener is running in Terminal 4
2. Check topology shows executors in Storm UI
3. Check bolts have no errors in Storm logs
4. Manually restart listener

### Error: "State not initialized" in stateful bolts
**Cause**: StateSpout not properly configured
**Fix**:
1. Ensure `fieldsGrouping()` is used for stateful bolts
2. Check bolt extends `BaseStatefulBolt<KeyValueState<...>>`
3. Check `initState()` is implemented

---

## Performance Expectations

With 10 tortoises on 150-cell track:

- **Stream rate**: 1 message per 5 seconds (tortoise delay)
- **Each message**: 10 runner objects (one per tortoise)
- **T2 filter output**: ~1 output per 5 seconds (tortoise 3 only)
- **T3 rank output**: ~1 output per 5 seconds (one tortoise per message)
- **T4 bonus output**: 1 output per 75 seconds (every 15 observations × 5 seconds)
- **T5 speed output**: 1 output per 25 seconds (after first 10 observations, then every 5)
- **T6 evolution output**: 1 output per window duration (~10 seconds of observations)

---

## Summary Checklist

After completing all 6 tests:

- [ ] T2 filters tortoise 3 correctly
- [ ] T3 ranks tortoises 1-10 with proper tie handling
- [ ] T4 accumulates bonus points every 15 observations
- [ ] T5 calculates speed over 10-observation windows
- [ ] T6 tracks rank evolution over time windows
- [ ] No "Broken pipe" or connection errors
- [ ] All outputs have correct JSON schema
- [ ] No NullPointerException or parsing errors

**Success!** All topologies working correctly.
