/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
            // facility = 1 (user), severity = 6 (informational)
            // facility * 8 + severity = 14
            def priority
            switch (descData.level) {
                case "info":
                    priority = 14
                    break
                case "warn":
                    priority = 12
                    break
                case "error":
                    priority = 11
                    break
                default:
                    priority = 15
            }
            
            sendHubCommand(new HubAction("<${priority}>1 ${descData.time} Hubitat ${descData.name} ${descData.id} ${descData.msg}", Protocol.LAN, [destinationAddress: "${ip}:514", type: HubAction.Type.LAN_TYPE_UDPCLIENT, ignoreResponse:true]))
        } else {
            log.warn "No log server set"
        }
    }
}

void connect() {
    log.debug "attempting connection"
    try {
        interfaces.webSocket.connect("http://localhost:8080/logsocket")
        pauseExecution(1000)
    } catch(e) {
        log.debug "initialize error: ${e.message}"
        logger.error("Exception", e)
    }
}

void disconnect() {
    interfaces.webSocket.close()
}

void uninstalled() {
    disconnect()
}

void initialize() {
    runIn(5, "connect")
}



void webSocketStatus(String message) {
	// handle error messages and reconnect
    log.debug "Got status ${message}" 
    if(message.startsWith("failure")) {
        // reconnect in a little bit
        runIn(5, connect)
    }
}
