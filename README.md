# A minimal blockchain implementation to study game theory behind Nakamoto Consensus

[![Build Status](https://travis-ci.org/vjgorla/bctest-java.svg?branch=master)](https://travis-ci.org/vjgorla/bctest-java)
[![Coverage Status](https://coveralls.io/repos/github/vjgorla/bctest-java/badge.svg?branch=master)](https://coveralls.io/github/vjgorla/bctest-java?branch=master)

Includes a simple peer-to-peer protocol implementation. Nodes use this to coordinate in building the blockchain. Proof-of-work is based on SHA256. Nodes reorg to move to the longest chain when they see one. There is also a difficulty retarget mechanism as part of the consensus rules. 

Blocks are essentially empty, and there is no merkel tree or an in-built token. This purely to study game theory aspects of the consensus protocol and how it achieves byzantine fault tolerence.

### How to run
* Run ```bctest-java>mvnw clean install```
* Start a node ```bctest-java>java -jar target\bctest-0.0.1-SNAPSHOT-jar-with-dependencies.jar -port 3001```
* Start another node ```bctest>java -jar target\bctest-0.0.1-SNAPSHOT-jar-with-dependencies.jar -port 3002 -peer http://localhost:3001```

To simulate latency in peer-to-peer network, block relay is delayed by 3secs by default. Increasing the delay by setting ```-delay``` (in milliseconds) will increase the probability of chain reorgs.

### Todo
* Nodes reorg to move to a chain with most work (not longest)
