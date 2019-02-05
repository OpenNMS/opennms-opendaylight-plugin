# opennms-odl-plugin

## Goal

This plugin was developed with two primary goals in mind:
1. Integrate with Opendaylight controllers in order to provide visiblity into fault, performance, and topology data from OpenNMS
1. Help shape the implementation and features of the OpenNMS Integration API

## Prerequisites

This plugin requires OpenNMS and the OpenNMS Integration API compiled from the `features/controller-api` branches.

We assume that you have an existing Opendaylight controller up and running (tested with Oxygen-SR4).
See `ODL.md` for instructions on setting up a test environment if you don't already have one.

## Build & install

Build and install the plugin into your local Maven repository using:
```
mvn clean install
```

From the OpenNMS Karaf shell:
```
feature:repo-add mvn:org.opennms.plugins.odl/odl-karaf-features/1.0.0-SNAPSHOT/xml
config:edit org.opennms.plugins.opendaylight
property-set controllerUrl http://opendaylight:8181
config:update
feature:install opennms-plugins-odl
```

Update automatically:
```
bundle:watch *
```

## Using the plugin

Verify connectivity with the controller:
```
admin@opennms> health:check
```

Render the requisition using:
```
admin@opennms> provision:show-import -x opendaylight
```

Trigger the import using:
```
./bin/send-event.pl uei.opennms.org/internal/importer/reloadImport --parm 'url requisition://opendaylight'
```
