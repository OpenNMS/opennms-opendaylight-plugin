# Flow hanlding with OpenNMS + Opendaylight + Containernet

## Overview

This document contains notes related to testing (Net)flow support with OpenNMS + Contairnet where the switches are managed by Opendaylight and monitored by OpenNMS using the plugin.

Using IPFIX looks the most promising, but there are still a few pieces missing before the flows can be associated with the proper nodes and interfaces in OpenNMS.

## Netflow v5

### Setup

```sh
ovs-vsctl -- set Bridge s1 netflow=@nf -- --id=@nf create NetFlow targets=\"${COLLECTOR_IP}:${COLLECTOR_PORT}\" active-timeout=10
```

### Results

Cannot determine source address when forwarding from containernet Docker image to process on host since the packets are NATed.

## sFlow

### Setup

```
ovs-appctl vlog/set sflow:file:dbg
ovs-vsctl -- set Bridge s1 sflow=@s -- --id=@s create sflow target=\"172.17.0.1:6343\" header=128 sampling=10 polling=10
ovs-vsctl -- set Bridge s2 sflow=@s -- --id=@s create sflow target=\"172.17.0.1:6343\" header=128 sampling=10 polling=10
```

### Results

Similar problem as with Netflow v5 - cannot accurately determine the source bridge.

## IPFIX

### Setup

```
ovs-appctl vlog/set ipfix:file:dbg
ovs-vsctl -- set Bridge s1 ipfix=@i -- --id=@i create IPFIX targets=\"172.17.0.1:4730\" obs_domain_id=12 obs_point_id=1
ovs-vsctl -- set Bridge s2 ipfix=@i -- --id=@i create IPFIX targets=\"172.17.0.1:4730\" obs_domain_id=12 obs_point_id=2
```

### Results

No ingress/egress interface is set when using IPFIX with the version of CVS shipped with containernet.
Need an image of containernet running OVS 2.9.0 or greater: https://github.com/openvswitch/ovs/commit/cd32509e4af4f9f7a002a6a5c137718f2173c538#diff-4a02603b0662a197032dc7fc8708f746

Can use the observation domain and point ids in the IPFIX packets to associate the flows with the proper bridge.
Need further enhancements on the OpenNMS side to do this though.

## Generating traffic

Once with environment is setup, you can generate traffic (which in turn generate flows) using:

```
containernet> iperf h1 h4
*** Iperf: testing TCP bandwidth between h1 and h4
*** Results: ['608 Mbits/sec', '612 Mbits/sec']
```

Verify flows were sent:
```
$ sudo tcpdump -ni docker0 port 8877
tcpdump: verbose output suppressed, use -v or -vv for full protocol decode
listening on docker0, link-type EN10MB (Ethernet), capture size 262144 bytes
23:51:32.976460 IP 172.17.0.2.48565 > 172.17.0.1.8877: UDP, length 120
23:51:32.976590 IP 172.17.0.2.44872 > 172.17.0.1.8877: UDP, length 72
23:51:32.976610 IP 172.17.0.2.39960 > 172.17.0.1.8877: UDP, length 72
23:51:33.631353 IP 172.17.0.2.48565 > 172.17.0.1.8877: UDP, length 120
```

Display processing metrics:
```
metrics-display -m flow
```

Should show stats like:
```
logParsing
             count = 3
         mean rate = 0.00 calls/second
     1-minute rate = 0.01 calls/second
     5-minute rate = 0.01 calls/second
    15-minute rate = 0.00 calls/second
               min = 7.58 milliseconds
               max = 8.21 milliseconds
              mean = 7.82 milliseconds
```
