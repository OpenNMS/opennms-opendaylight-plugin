# TODO

Improvements we would like to make to the plugin and the related components.

## API Enhancements

* Extend the poller configuration -> provide a definition for our node in a new package?
  * Poller should be able to add service monitors/entries to packages during a reload, and have these monitored
  * on existing nodes

## Showcase

* Re-compile and re-deploy new code on the fly without having to    * Example where it can be used: collect a new metric, and add a new graph def

## General

* Add support for multiple controllers, or clusters of controllers
* Add support for controller clusters (round-robin rest requests?)
* Structure things in a way to allow for multiple controllers to be running different versions
* Remove assumptions from support augmentations

## Integration

### Flows [25.0.0]

* We need a working deep dive tool with this environment
** Ideally using IPFIX with both ingress/egress stats

### Thresholds [25.0.0]

* We should be able to trigger a threshold alarm and make it appear on the link

### Minion support [25.0.0]

* Should be able to do the same with Minion
* I should be able to easily route my own RPC and Sink calls
  * One bundle exposes the server
  * Another bundle exposes the client
  * Implement using gRPC -> HTTP2 -> Java 9+

### Training [25.0.0]

* Show how to use the situation feedback features
  * `feature:install opennms-situation-feedback`
* Use the feedback for training - see xref:training_with_ludwig.adoc[Training with Ludwig].

### Performance [*]

* Use for performance testing - create lab with 5k switches and 50k hosts
  * Test with https://maxinet.github.io/
* How
  * Use only async calls
  * Use more specific queries

### Self-Monitoring [*]

* We should be expose statistics and faults for the engine itself
  * [Fault] Can't connect to ODL server
  * [Perf] ODL Statistics via JMX
