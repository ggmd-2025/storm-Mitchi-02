# Quick Start: Testing the Implementation

## One-Minute Setup

```bash
# Terminal 1: Start cluster
cd /Users/ilyasbenhammadi/dev/tiw/ggmd/tp/tp3
docker compose up
# Wait ~5 seconds for startup

# Terminal 2: Start stream
docker compose exec client /bin/bash
cd /ggmd-storm-stream && mvn package && ./startStream.sh tortoise 10 150 9001
# STOP and wait here - you should see "Server Started ...." and JSON output

# Terminal 3: Submit topology (within 5 seconds of seeing "Server Started")
docker compose exec -it client /bin/bash
cd /storm/examples/ggmd-storm-topology && mvn package
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005
# Should see submission messages, no errors

# Terminal 4: Listen to output
docker compose exec -it client /bin/bash
cd /ggmd-storm-listner && mvn package && ./startListner.sh 9005
# Should see JSON output within 10 seconds
```

## What You Should See

**Terminal 2 (Stream):**
```
Server Started ....
{"timestamp":1764065100151,"runners":[{"id":0,...},{"id":1,...},...]}
{"timestamp":1764065105158,"runners":[{"id":0,...},{"id":1,...},...]}
...
```

**Terminal 3 (Topology):**
```
Submitting topology topoT2 to cluster ...
[Success messages]
```

**Terminal 4 (Listener) - After ~10 seconds:**
```
{"id":3,"top":5,"nom":"Caroline","nbCellsParcourus":7,"total":10,"maxcel":150}
{"id":3,"top":10,"nom":"Caroline","nbCellsParcourus":12,"total":10,"maxcel":150}
...
```

## Key Timing

- **Stream starts**: Listens and waits for client
- **Topology submission**: Must happen **within 5-10 seconds** of stream start
- **First output**: Appears after topology connects (~5-10 seconds more)
- **Listener**: Can start anytime, will catch data from queue

## If Something Goes Wrong

```bash
# Connection failed?
# 1. Stop stream (Ctrl+C in Terminal 2)
# 2. Start fresh stream
# 3. Immediately submit topology (within 5 seconds)

# No output?
# 1. Check Storm UI: http://localhost:8081
# 2. Check topology status
# 3. Check for errors in executor logs

# Broken pipe error?
# Topology didn't connect in time
# - Restart stream
# - Be faster submitting topology
```

## Testing Each Topology

```bash
# To test different topologies, just change the JAR command:

# Test T2 (Filter)
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT2 9001 9005

# Test T3 (Rank)
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT3 9001 9005

# Test T4 (Bonus)
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT4 9001 9005

# Test T5 (Speed)
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT5 9001 9005

# Test T6 (Evolution)
storm jar target/stormTP-0.1.jar stormTP.topology.TopologyT6 9001 9005

# Before each new topology:
# 1. Kill previous: storm kill topoT2 -w 0
# 2. Wait 5 seconds
# 3. Submit new topology
```

## Stream Parameters

```bash
# Current setup:
./startStream.sh tortoise 10 150 9001
#                 ^^^^^^^ ^^  ^^^ ^^^^
#                 animal  num cells port
#                         runners

# You can change these, but keep defaults for testing:
# - tortoise: slower (5s per message), easier to debug
# - 10 runners: matches task.md examples
# - 150 cells: standard track size
# - 9001: matches topologies
```

## Output Fields by Topology

**T2 Output**: `{id, top, nom, nbCellsParcourus, total, maxcel}`
**T3 Output**: `{id, top, rang, total, maxcel}`
**T4 Output**: `{id, tops, score}` (every 15 observations)
**T5 Output**: `{id, nom, tops, vitesse}` (every 5 observations, after first 10)
**T6 Output**: `{id, nom, date, evolution}` (per window)

## Success Criteria

- [ ] Topology submits without errors
- [ ] Listener receives JSON output
- [ ] Output has correct fields for the topology
- [ ] No "Broken pipe" errors
- [ ] Data appears within 15 seconds of topology submission

## Files Modified/Created

- ✅ MyTortoiseBolt.java - Fixed to parse array format
- ✅ All 14 other files created fresh

## Next Steps

1. Run quick start above
2. Check output matches expected format
3. For detailed testing, see TEST_GUIDE.md
4. For detailed explanation, see IMPLEMENTATION_SUMMARY.md
5. For implementation notes, see sol.md

---

**Ready?** Start Terminal 1 and follow the "One-Minute Setup" above!
