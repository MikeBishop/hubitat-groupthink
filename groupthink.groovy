/*
    Groupthink
    Copyright 2023 Mike Bishop,  All Rights Reserved
*/

definition (
    name: "Groupthink", namespace: "evequefou", author: "Mike Bishop", description: "Repeats group commands until all devices respond",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-groupthink/main/groupthink.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Groupthink", install: true, uninstall: true) {
        initialize();
        section() {
            paragraph "Groupthink repeats commands to all devices in a group until all devices respond. " +
                "This should only be used with group activator devices which expose the groupState property."
        }
        section() {
            input "monitored", "capability.switch", title: "Monitored Group Activators",
                required: true, multiple: true, submitOnChange: true

            input "monitorOn", "bool", title: "Monitor on events?",
                submitOnChange: true, defaultValue: true;
            input "monitorOff", "bool", title: "Monitor off events?",
                submitOnChange: true, defaultValue: true;
            def nonGroup = monitored.findAll { it.currentValue("groupState") == null }
            if( nonGroup ) {
                paragraph "The following devices do not expose groupState and will be ignored: \n- " +
                    nonGroup.collect { it.getDisplayName() ?: it.getLabel() }.join("\n- ")
                paragraph "In Groups & Scenes, enable the \"Show group state in group device\" option for these devices. " +
                    "In Room Lighting, enable one of the \"Indicator: Group\" options and monitor either on or off events, not both."
            }

            input "delay", "number", title: "Delay between commands (sec)",
                defaultValue: 5, required: true

            input "maxRetries", "number", title: "Maximum number of retries",
                defaultValue: 20, required: true

            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: false;
        }
    }
}

void installed() {
    initialize();
}

void updated() {
    initialize();
}

void initialize() {
    unsubscribe();
    subscribe(monitored, "switch", "deviceChanged");
}

void deviceChanged(event) {
    debug("deviceChanged: ${event.device} ${event.value}");
    // Device just changed; start fresh
    def now = now();
    def triggerDNI = event.device.getDeviceNetworkId();
    state[triggerDNI] = 0;
    state[triggerDNI +"_last"] = now;
    schedule(triggerDNI, now);
}

void schedule(triggerDNI, triggerTime) {
    runIn(delay, "checkGroup", [
        overwrite: false,
        data: [device: triggerDNI, trigger: triggerTime]
    ]);
}

void checkGroup(props) {
    def triggerDNI = props.device;
    def triggerTime = props.trigger;
    def device = monitored.find { it.getDeviceNetworkId() == triggerDNI };
    def name = device.getDisplayName() ?: device.getLabel();

    if( triggerTime != state[triggerDNI +"_last"] ) {
        // This is an old trigger; ignore it
        debug("checkGroup: ignoring old trigger for ${name}");
        return;
    }

    if( state[triggerDNI] > maxRetries ) {
        clearForDNI(triggerDNI, "checkGroup: ${name} reached max retries; giving up", true);
        return;
    }

    if( !device ) {
        clearForDNI(triggerDNI, "checkGroup: ${name} not selected; giving up");
        return;
    }

    def groupState = device.currentValue("groupState");
    if( groupState == null ) {
        clearForDNI(triggerDNI, "checkGroup: ${name} does not expose groupState; giving up");
        return;
    }

    def desiredState = device.currentValue("switch");
    if( (desiredState == "on" && groupState == "allOn") ||
        (desiredState == "off" && groupState == "allOff")
    ) {
        clearForDNI(triggerDNI, "checkGroup: ${name} reached desired state; done");
        return;
    }

    // Not there yet; try again
    if( desiredState == "on" ) {
        if( device.hasCapability("ColorMode") ) {
            switch( device.currentValue("colorMode") ) {
                case "CT":
                    repeatCT(device);
                    break;
                case "RGB":
                    repeatColor(device);
                    break;
                default:
                    clearForDNI(triggerDNI, "checkGroup: ${device} has unsupported color mode ${device.currentValue("colorMode")}; giving up");
            }
        }
        else if (device.hasCapability("ColorTemperature")) {
            repeatCT(device);
        }
        else if (device.hasCapability("ColorControl")) {
            repeatColor(device);
        }
        else if (device.hasCapability("SwitchLevel")) {
            debug "repeatLevel: ${device} ${device.currentValue("level")}";
            device.setLevel(device.currentValue("level"));
        }
        else {
            debug "repeatOn: ${device}"
            device.on();
        }
    }
    else {
        debug "repeatOff: ${device}"
        device.off();
    }
    state[triggerDNI] = state[triggerDNI] + 1;
    schedule(triggerDNI, triggerTime);
}

void repeatCT(device) {
    def ct = device.currentValue("colorTemperature");
    def level = device.currentValue("level");
    def name = device.getDisplayName() ?: device.getLabel();

    debug "repeatCT: ${name} ${ct} ${level}";
    device.setColorTemperature(ct, level);
}

void repeatColor(device) {
    def hue = device.currentValue("hue");
    def saturation = device.currentValue("saturation");
    def level = device.currentValue("level");
    def name = device.getDisplayName() ?: device.getLabel();

    debug "repeatColor: ${name} ${hue} ${saturation} ${level}";
    device.setColor([
        hue: hue,
        saturation: saturation,
        level: level
    ]);
}

void clearForDNI(String triggerDNI, String reason, boolean warn = false) {
    if( warn ) {
        warn(reason);
    }
    else {
        debug(reason);
    }
    state.remove(triggerDNI);
    state.remove(triggerDNI +"_last");
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug(msg)
    }
}

void warn(String msg) {
    log.warn(msg)
}

void error(Exception ex) {
    log.error "${ex} at ${ex.getStackTrace()}"
}

void error(String msg) {
    log.error msg
}
