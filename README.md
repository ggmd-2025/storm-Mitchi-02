[![Open in Codespaces](https://classroom.github.com/assets/launch-codespace-2972f46106e565e64193e422d61a12cf1da4916b45550586e14ef0a7c637dd04.svg)](https://classroom.github.com/open-in-codespaces?assignment_repo_id=21808861)

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://github.com/codespaces/new?hide_repo_select=true&ref=main&repo=1097878214&machine=standardLinuxXL)

### Run Topology in a Docker-based Storm Cluster

#### Terminal 1: Storm Cluster

-   Start cluster:

```sh
docker compose up
```

#### Terminal 2: Stream Producer

Connect to the client container:

```sh
docker compose exec client /bin/bash
```

Compile and start the stream producer:

```sh
cd /ggmd-storm-stream
mvn package
./startStream.sh tortoise 10 150 9001
```

#### Terminal 3: Storm Topology

Connect to the client container:

```sh
docker compose exec -it client /bin/bash
```

> [!IMPORTANT]  
> Modify `src/main/java/stormTP/topology/TopologyT1.java`  
> Replace `new InputStreamSpout("127.0.0.1", portINPUT);` code with  
> `new InputStreamSpout("client", portINPUT);`

Compile the storm topology:

```sh
cd /storm/examples/ggmd-storm-topology/
mvn package
```

Submit topology to cluster:

```sh
# run cluster for 120 secs
storm jar target/stormTP-0.1.jar \
      stormTP.topology.TopologyT1 9001 9005
```

Check the Storm UI at http://localhost:8081.

> Are you using Github Codespaces? Look in the PORTS section to discover the hostname you have to use to connect to the Storm UI.

#### Terminal 4: Topology consumer (listener)

Connect to the client container:

```sh
docker compose exec -it client /bin/bash
```

Compile and run the listener:

> [!IMPORTANT]  
> Modify file `ggmd-storm-listner/src/main/java/StreamListner.java`.  
> Replace `"127.0.0.1"` with `supervisor`

```sh
cd /ggmd-storm-listner/
mvn package

./startListner.sh 9005
```
