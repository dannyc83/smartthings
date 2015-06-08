/*
 *  EPIC! LIFX Bulb
 *  Version: 1.0
 *  Written by Ben Koehler (@triphius)
 * 
 *  https://github.com/triphius/smartthings/blob/master/epic-lifx-bulb.groovy
 *
 *  Last Updated: June 7, 2015
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Based on Nicolas Cerveaux's (@zzarbi) LIFX 2.0 Device Type
 *  https://github.com/zzarbi/smartthings/blob/master/device/lifx/lifx-bulb-2.groovy
 */

metadata {
    definition (name: "EPIC! LIFX Bulb", namespace: "triphius", author: "Ben Koehler") {
        capability "Polling"
        capability "Switch"
        capability "Switch Level"
        capability "Color Control"
        capability "Refresh"
        
        command "setAdjustedColor"
	command "setAdjustedWhite"
    }

    simulator {
    }

    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.Lighting.light14", backgroundColor:"#79b821", nextState:"turningOff"
            state "off", label:'${name}', action:"switch.on", icon:"st.Lighting.light14", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', icon:"st.Lighting.light14", backgroundColor:"#79b821"
            state "turningOff", label:'${name}', icon:"st.Lighting.light14", backgroundColor:"#ffffff"
        }
        controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 2, inactiveLabel: false) {
            state "level", action:"switch level.setLevel"
        }
        controlTile("rgbSelector", "device.color", "color", height: 3, width: 3, inactiveLabel: false) {
            state "color", action:"setAdjustedColor"
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        main(["switch"])
        details(["switch","levelSliderControl","rgbSelector","refresh"])
    }
}

private debug(data){
    if(parent.appSettings.debug == "true"){
        log.debug(data)
    }
}

private getAccessToken() {
    return parent.appSettings.accessToken;
}

private sendCommand(path, method="GET", body=null) {
    def accessToken = getAccessToken()
    def pollParams = [
        uri: "https://api.lifx.com:443",
        path: "/v1beta1/"+path+".json",
        headers: ["Content-Type": "application/x-www-form-urlencoded", "Authorization": "Bearer ${accessToken}"],
        body: body
    ]
    debug(method+" Http Params ("+pollParams+")")
    
    try{
        if(method=="GET"){
            httpGet(pollParams) { resp ->            
                parseResponse(resp)
            }
        }else if(method=="PUT") {
            httpPut(pollParams) { resp ->            
                parseResponse(resp)
            }
        }
    } catch(Exception e){
        debug("___exception: " + e)
    }
}

private parseResponse(resp) {
    debug("Response: "+resp.data)
    if(resp.status == 200) {
        if (resp.data) {
            if(resp.data.power){
                def brightness = Math.ceil(resp.data.brightness*100)
                def hue = Math.ceil(resp.data.color.hue / 3.6)
                def saturation = Math.ceil(resp.data.color.saturation*100)
                
                //update switch
                if(device.currentValue("switch")!=resp.data.power){
                    debug("Update switch to "+resp.data.power)
                    sendEvent(name: "switch", value: resp.data.power)
                }
                
                // update level
                if(brightness != device.currentValue("level")){
                    debug('Update level to '+brightness)
                    sendEvent(name: 'level', value: brightness)
                }
                
                // update hue
                if(hue != device.currentValue("hue")){
                    debug('Update hue to '+hue)
                    sendEvent(name: 'hue', value: hue)
                }
                
                // update saturation
                if(saturation != device.currentValue("saturation")){
                    debug('Update saturation to '+saturation)
                    sendEvent(name: 'saturation', value: saturation)
                }
            }
        }
    }else if(resp.status == 201){
        debug("Something was created")
    }
}

//parse events into attributes
def parse(value) {
    debug("Parsing '${value}' for ${device.deviceNetworkId}")
}


private sendAdjustedColor(data) {
    def hue = Math.ceil(data.hue*3.6)
    def saturation = data.saturation/100
    def duration = data.duration
    def level = data.level

    sendCommand("lights/"+device.deviceNetworkId+"/color", "PUT", 'color=hue%3A'+hue+'%20saturation%3A'+saturation+'%20brightness%3A'+level+'%25&duration='+duration+'&power_on=false')
}

def setAdjustedColor(value) {
    def data = [:]
    data.hue = value.hue
    data.saturation = value.saturation
    data.level = value.level
    
    if (value.duration) {
		data.duration = value.duration
    } else {
		data.duration = 1
	}
    
    sendAdjustedColor(data)
    sendEvent(name: 'hue', value: value.hue)
    sendEvent(name: 'saturation', value: value.saturation)
    sendEvent(name: 'duration', value: value.duration)
    sendEvent(name: 'level', value: value.level)
}


private sendAdjustedWhite(data) {
    def kelvin = data.kelvin
    def duration = data.duration
    def level = data.level
    
    sendCommand("lights/"+device.deviceNetworkId+"/color", "PUT", 'color=kelvin%3A'+kelvin+'%20brightness%3A'+level+'%25&duration='+duration+'&power_on=false')
}

def setAdjustedWhite(value) {
    def data = [:]
    data.kelvin = value.kelvin
    data.duration = value.duration
    data.level = value.level
    
    sendAdjustedWhite(data)
    sendEvent(name: 'kelvin', value: value.kelvin)
    sendEvent(name: 'duration', value: value.duration)
    sendEvent(name: 'level', value: value.level)
}


def setLevel(double value) {
    def data = [:]
    data.hue = device.currentValue("hue")
    data.saturation = device.currentValue("saturation")
    data.duration = 1
    data.level = value

    sendAdjustedColor(data)
    sendEvent(name: 'level', value: value)
}


def on() {
    sendCommand("lights/"+device.deviceNetworkId+"/power", "PUT", "state=on&duration=1")
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendCommand("lights/"+device.deviceNetworkId+"/power", "PUT", "state=off&duration=1")
    sendEvent(name: "switch", value: "off")
}

def refresh() {
    sendCommand("lights/"+device.deviceNetworkId)
}

def poll() {
    refresh()
}
