# opennms-odl-plugin

This plugin requires OpenNMS and the OpenNMS Integration API compiled from the `features/controller-api` branches.

# Build & install

Build and install the plugin into your local Maven repository using:
```
mvn clean install
```

From the OpenNMS Karaf shell:
```
feature:repo-add mvn:org.opennms.plugins.odl/odl-karaf-features/1.0.0-SNAPSHOT/xml
feature:install opennms-plugins-odl
bundle:watch *
```

# Getting Started

Download a copy of Opendaylight Oxygen-SR4:
```
wget https://nexus.opendaylight.org/content/repositories/public/org/opendaylight/integration/karaf/0.8.4/karaf-0.8.4.tar.gz
tar zxvf karaf-0.8.4.tar.gz
```

Start the controller:
```
./karaf-0.8.4/bin/karaf
```

Install the following features:
```
feature:install odl-mdsal-clustering odl-mdsal-all odl-restconf odl-l2switch-switch odl-dlux-core odl-l2switch-switch-ui odl-tsdr-openflow-statistics-collector

```

Fire up Containernet:
```
docker pull containernet/containernet
docker run --name containernet -it --rm --privileged --pid='host' -v /var/run/docker.sock:/var/run/docker.sock containe
rnet/containernet /bin/bash
```

Use a script that connects to a remote controller:
```
#!/usr/bin/python
"""
This is the most simple example to showcase Containernet.
"""
from mininet.net import Mininet
from mininet.net import Containernet
from mininet.node import Controller, RemoteController
from mininet.topolib import TreeTopo
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
setLogLevel('info')

c0 = RemoteController('c0', ip='192.168.238.174', port=6633)
topo = TreeTopo(depth=2, fanout=2)
net = Mininet(topo=topo, build=False)
net.addController(c0)
net.build()
net.start()
info('*** Running CLI\n')
CLI(net)
info('*** Stopping network')
net.stop()
```

Start the script, and when the shell is ready, run:
```
containernet> pingall
*** Ping: testing ping reachability
h1 -> h2 h3 h4 
h2 -> h1 h3 h4 
h3 -> h1 h2 h4 
h4 -> h1 h2 h3 
*** Results: 0% dropped (12/12 received)
```

On OpenNMS:
```
config:edit org.ops4j.pax.url.mvn
property-set org.ops4j.pax.url.mvn.repositories http://nexus.opendaylight.org/content/repositories/public@id=odl
config:update
```

```
config:edit org.ops4j.pax.url.mvn
property-set org.ops4j.pax.url.mvn.repositories http://nexus.opendaylight.org/content/repositories/public@id=odl
config:update
```
