metadata {
    definition (name: "Syslog", namespace: "hubitatuser12", author: "Hubitat User 12") {
        capability "Initialize"
    }
    command "disconnect"

    preferences {
        input("ip", "text", title: "Syslog IP Address", description: "ip")
    }
}

import groovy.json.JsonSlurper
import hubitat.device.HubAction
import hubitat.device.Protocol

void installed() {
    updated()
}

void updated() {
    initialize()
}

void parse(String description) {
    def descData = new JsonSlurper().parseText(description)
    // don't log our own messages, we will get into a loop
    if("${descData.id}" != "${device.id}") {
        if(ip != null) {
            sendHubCommand(new HubAction("<14>1 ${descData.time} Hubitat ${descData.name} ${descData.id} ${descData.msg}", Protocol.LAN, [destinationAddress: "${ip}:514", type: HubAction.Type.LAN_TYPE_UDPCLIENT, ignoreResponse:true]))
        } else {
            log.warn "No log server set"
        }
    }
}

void disconnect() {
    interfaces.webSocket.close()
}

void uninstalled() {
    disconnect()
}

void initialize() {
    try {
        interfaces.webSocket.connect("http://localhost:8080/logsocket")
        pauseExecution(1000)
        log.info "connection established"
    } catch(e) {
        log.debug "initialize error: ${e.message}"
        logger.error("Exception", e)
    }
}

void webSocketStatus(String message) {
	// handle error messages and reconnect
}
