# Groupthink for Hubitat

Hubitat exposes multiple ways to create group devices, primarily Room Lighting
and its predecessor Groups and Scenes. When a group device is triggered, the
underlying real devices don't always respond on the first attempt. However,
since the group appears to be on, subsequent executions of rules frequently
don't send a fresh activation signal.

This app monitors the `groupState` property optionally exposed by a group
device; when a group is turned on or off, if the `groupState` doesn't update to
reflect the desired change within a configurable number of seconds, it resends
the indicated state multiple times.

Better late than never.

# Change Log

* [9/1/2023]   Initial release
