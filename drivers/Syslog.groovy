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
 *
 */

/* Notes

2020-08-18 - staylorx
  - Received version from original author (great start!)
  - Attemping RFC5424 format for syslog
  - Date/time stamping with the hub timezone

*/

metadata {
    definition (name: "Syslog", namespace: "hubitatuser12", author: "Hubitat User 12") {
        capability "Initialize"
    }
    command "disconnect"

    preferences {
        input("ip", "text", title: "Syslog IP Address", description: "ip address of the syslog server", required: true)
        input("port", "number", title: "Syslog IP Port", description: "syslog port (UDP)", defaultValue: 514, required: true)
        input("hostname", "text", title: "Hub Hostname", description: "hostname of the hub; leave empty for IP address")
        input("logEnable", "bool", title: "Enable debug logging", description: "", defaultValue: false)
    }
}

import groovy.json.JsonSlurper
import hubitat.device.HubAction
import hubitat.device.Protocol

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void installed() {
    if (logEnable) log.debug "installed()"
    updated()
}

void updated() {
    if (logEnable) log.debug "updated()"
    initialize()
    //turn off debug logs after 30 minutes
    if (logEnable) runIn(1800,logsOff)
}

void parse(String description) {
    
    def hub = location.hubs[0]
    // If I can't get a hostname, an IP address will do.
    if (!hostname?.trim()) {
      hostname = hub.getDataValue("localIP")
    }
    
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
            
            // we get date-space-time but would like ISO8601
            if (logEnable) log.debug "timezone from hub is ${location.timeZone}"
            def dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
            def date = Date.parse(dateFormat, descData.time)
            
            // location timeZone comes from the geolocation of the hub. It's possible it's not set?
            def isoDate = date.format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", location.timeZone)
            if (logEnable) log.debug "time we get = ${descData.time}; time we want ${isoDate}"
            
            // made up PROCID or MSGID //TODO find PROCID and MSGID in the API?
            def constructedString = "<${priority}>1 ${isoDate} ${hostname} Hubitat - - [sd_id_1@32473 device_name=\"${descData.name}\" device_id=\"${descData.id}\"] ${descData.msg}"
            if (logEnable) log.debug "sending: ${constructedString}"
            
            sendHubCommand(new HubAction(constructedString, Protocol.LAN, [destinationAddress: "${ip}:${port}", type: HubAction.Type.LAN_TYPE_UDPCLIENT, ignoreResponse:true]))
        } else {
            log.warn "No log server set"
        }
    }
}

void connect() {
    if (logEnable) log.debug "attempting connection"
    try {
        interfaces.webSocket.connect("http://localhost:8080/logsocket")
        pauseExecution(1000)
    } catch(e) {
        log.error "initialize error: ${e.message}"
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
    if (logEnable) log.debug "initialize()"
    runIn(5, "connect")
}

void webSocketStatus(String message) {
	// handle error messages and reconnect
    if (logEnable) log.debug "Got status ${message}" 
    if(message.startsWith("failure")) {
        // reconnect in a little bit
        runIn(5, connect)
    }
}
