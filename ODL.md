# Getting Started with Opendaylight

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
feature:install odl-mdsal-clustering odl-mdsal-all odl-restconf odl-l2switch-switch odl-dlux-core odl-l2switch-switch-ui
```

Fire up Containernet:
```
docker pull containernet/containernet
docker run --name containernet -it -d --privileged --pid='host' -v /var/run/docker.sock:/var/run/docker.sock containernet/containernet
docker exec -it containernet /bin/bash
```

Use a script that connects to a remote controller:
```
cat >net.py <<EOT
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

c0 = RemoteController('c0', ip='172.17.0.1', port=6633)
topo = TreeTopo(depth=2, fanout=2)
net = Mininet(topo=topo, build=False)
net.addController(c0)
net.build()
net.start()
info('*** Running CLI\n')
CLI(net)
info('*** Stopping network')
net.stop()
EOT
```

Start the script:
```
chmod +x net.py
./net.py
```

When the shell is ready, run:
```
containernet> pingall
*** Ping: testing ping reachability
h1 -> h2 h3 h4 
h2 -> h1 h3 h4 
h3 -> h1 h2 h4 
h4 -> h1 h2 h3 
*** Results: 0% dropped (12/12 received)
```

## Adding flow support


ovs-vsctl -- set Bridge s1 netflow=@nf -- --id=@nf create NetFlow targets=\"1.2.3.4:8888\" active-timeout=30

ovs-vsctl -- set Bridge s1 netflow=@nf -- --id=@nf create NetFlow targets=\"1.2.3.4:8888\" active-timeout=30
