# Implementation Summary: Storm Tortoise Race Topology

## Overview

Complete implementation of 5 Storm topologies (T2-T6) for processing tortoise race data streams. All 15 required files have been created and are ready for testing.

---

## Files Created

### Operator Bolts (10 files)

#### Processing Bolts
1. **[MyTortoiseBolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/MyTortoiseBolt.java)** - Stateless
   - Parses stream array format: `{"timestamp": ..., "runners": [...]}`
   - Filters tortoise by ID (configured in constructor)
   - Outputs: `(id, top, nom, nbCellsParcourus, total, maxcel)`

2. **[GiveRankBolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/GiveRankBolt.java)** - Stateful
   - Maintains position state for all tortoises
   - Ranks tortoises 1-10 by position on track
   - Handles ties with 'ex' suffix (e.g., "1ex", "2ex")
   - Outputs: `(id, top, rang, total, maxcel)`

3. **[ComputeBonusBolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/ComputeBonusBolt.java)** - Stateful
   - Accumulates bonus points in state
   - Emits every 15 observations
   - Bonus formula: `total - rank_number`
   - Outputs: `(id, tops, score)`

4. **[SpeedBolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/SpeedBolt.java)** - Windowed Stateless
   - Window: 10 tuples, sliding every 5 tuples
   - Calculates speed as cells traveled per observation
   - Outputs: `(id, nom, tops, vitesse)`

5. **[RankEvolutionBolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/RankEvolutionBolt.java)** - Windowed Stateful
   - Tracks rank changes over time windows
   - Determines progression/constant/regression
   - Outputs: `(id, nom, date, evolution)`

#### Exit/Output Bolts
6. **[Exit2Bolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/Exit2Bolt.java)** - Terminal for T2
7. **[Exit3Bolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/Exit3Bolt.java)** - Terminal for T3
8. **[Exit4Bolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/Exit4Bolt.java)** - Terminal for T4
9. **[Exit5Bolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/Exit5Bolt.java)** - Terminal for T5
10. **[Exit6Bolt.java](ggmd-storm-topology/src/main/java/stormTP/operator/Exit6Bolt.java)** - Terminal for T6

All exit bolts:
- Accept JSON from input bolt
- Send to TCP port using `StreamEmiter`
- Acknowledge tuples for reliability

### Topology Classes (5 files)

11. **[TopologyT2.java](ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT2.java)**
    - Flow: `InputStreamSpout → MyTortoiseBolt → Exit2Bolt`
    - Grouping: `shuffleGrouping`

12. **[TopologyT3.java](ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT3.java)**
    - Flow: `InputStreamSpout → GiveRankBolt → Exit3Bolt`
    - Grouping: `fieldsGrouping` (for state)

13. **[TopologyT4.java](ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT4.java)**
    - Flow: `InputStreamSpout → MyTortoiseBolt → GiveRankBolt → ComputeBonusBolt → Exit4Bolt`
    - Grouping: `shuffleGrouping` → `fieldsGrouping` → `fieldsGrouping`

14. **[TopologyT5.java](ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT5.java)**
    - Flow: `InputStreamSpout → MyTortoiseBolt → SpeedBolt → Exit5Bolt`
    - Grouping: `shuffleGrouping` → windowed (10/5)

15. **[TopologyT6.java](ggmd-storm-topology/src/main/java/stormTP/topology/TopologyT6.java)**
    - Flow: `InputStreamSpout → MyTortoiseBolt → GiveRankBolt → RankEvolutionBolt → Exit6Bolt`
    - Grouping: `shuffleGrouping` → `fieldsGrouping` → `fieldsGrouping`

---

## Key Design Decisions

### 1. JSON Format Handling
**Challenge**: Stream sends array of runners in single message
```json
{"timestamp": 1234567890, "runners": [...]}
```

**Solution**: MyTortoiseBolt detects and parses array, emitting individual tuples
- Checks `containsKey("runners")` to detect format
- Loops through array and filters by tortoise ID
- Emits one tuple per matching tortoise

### 2. State Management
**Approach**: Use `BaseStatefulBolt<KeyValueState<String, String>>`
- All state keys use string format: `"pos_3"`, `"score_5"`, etc.
- State persists across tuple processing
- `fieldsGrouping()` ensures same tortoise goes to same executor

**Bolts Using State**:
- GiveRankBolt: Stores positions for ranking
- ComputeBonusBolt: Accumulates bonus scores
- RankEvolutionBolt: Tracks rank for evolution detection

### 3. Windowing
**SpeedBolt**: Extends `BaseWindowedBolt`
- Window size: 10 tuples
- Slide: 5 tuples (50% overlap)
- Calculates speed from min/max position in window

**RankEvolutionBolt**: Extends `BaseStatefulWindowedBolt`
- Combines state + windowing
- Receives `TupleWindow` instead of individual tuples
- Compares first and last rank in window

### 4. Reliable Processing
All tuples:
- Emit with anchor: `collector.emit(tuple, new Values(...))`
- Acknowledged: `collector.ack(tuple)`
- Ensures no data loss in Storm cluster

---

## Data Flow

```
InputStreamSpout (port 9001)
    ↓
    JSON: {"timestamp": ..., "runners": [{"id": 0, ...}, {"id": 1, ...}, ...]}
    ↓
MyTortoiseBolt (filters to tortoise 3)
    ↓
    JSON: {"id": 3, "top": X, "nom": "Caroline", "nbCellsParcourus": Y, ...}
    ↓
    Branches into T2, T3, T4, T5, T6 topologies
```

### T2: Direct output to port 9005
```
MyTortoiseBolt → Exit2Bolt → TCP port 9005
```

### T3: Ranking calculation
```
MyTortoiseBolt → GiveRankBolt (stateful) → Exit3Bolt → TCP port 9005
```

### T4: Bonus points
```
MyTortoiseBolt → GiveRankBolt → ComputeBonusBolt (stateful) → Exit4Bolt → TCP port 9005
```

### T5: Speed calculation
```
MyTortoiseBolt → SpeedBolt (windowed 10/5) → Exit5Bolt → TCP port 9005
```

### T6: Rank evolution
```
MyTortoiseBolt → GiveRankBolt → RankEvolutionBolt (windowed+stateful) → Exit6Bolt → TCP port 9005
```

---

## Testing Strategy

See [TEST_GUIDE.md](TEST_GUIDE.md) for detailed testing procedure.

**Quick Test**:
```bash
# Terminal 1
docker compose up

# Terminal 2 (wait for "Server Started ....")
docker compose exec client /bin/bash
cd /ggmd-storm-stream && mvn package && ./startStream.sh tortoise 10 150 9001

# Terminal 3 (within 5 seconds)
docker compose exec -it client /bin/bash
cd /storm/examples/ggmd-storm-topology && mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005

# Terminal 4
docker compose exec -it client /bin/bash
cd /ggmd-storm-listner && mvn package && ./startListner.sh 9005
```

**Expected Output in Terminal 4**:
```json
{"id":3,"top":5,"nom":"Caroline","nbCellsParcourus":7,"total":10,"maxcel":150}
{"id":3,"top":10,"nom":"Caroline","nbCellsParcourus":12,"total":10,"maxcel":150}
```

---

## Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| "Broken pipe" | No topology connected | Submit topology within 5 seconds of stream start |
| "Connection refused" | Stream not listening | Ensure stream shows "Server Started ...." |
| No output | Exit bolt not emitting | Check topology in Storm UI, verify no errors |
| "Cannot parse JSON" | Wrong format | MyTortoiseBolt handles new format automatically |
| State errors | Stateful bolt misconfigured | Use `fieldsGrouping()` and extend `BaseStatefulBolt` |

---

## Verification Checklist

Before submitting for grading:

- [ ] All 15 files created
- [ ] MyTortoiseBolt parses array format correctly
- [ ] GiveRankBolt handles ties with 'ex' suffix
- [ ] ComputeBonusBolt emits every 15 observations
- [ ] SpeedBolt uses 10/5 sliding window
- [ ] RankEvolutionBolt determines progression correctly
- [ ] All exit bolts send to correct port
- [ ] All topologies can be submitted without compilation errors
- [ ] Test T2 produces correct output
- [ ] No "Broken pipe" errors
- [ ] Storm UI shows all executors running

---

## Files Modified from Original

1. **MyTortoiseBolt.java** (NEW)
   - Added support for parsing `{"timestamp": ..., "runners": [...]}`  format
   - Original code expected flat runner objects

All other files are implementations following the task requirements.

---

## Summary

✅ Complete implementation of 5 Storm topologies
✅ 10 custom operator/bolt classes
✅ 5 topology classes
✅ Support for stateless, stateful, and windowed operations
✅ Proper JSON parsing and tuple handling
✅ Ready for testing and grading

Total Implementation: **~2000 lines of Java code**
