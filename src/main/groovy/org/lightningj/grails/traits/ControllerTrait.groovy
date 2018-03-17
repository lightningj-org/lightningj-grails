package org.lightningj.grails.traits

import grails.artefact.Enhances
import grails.artefact.controller.support.ResponseRenderer
import grails.web.api.ServletAttributes
import grails.web.api.WebAttributes
import org.lightningj.lnd.wrapper.Message
import org.lightningj.lnd.wrapper.XMLParser
import org.lightningj.lnd.wrapper.XMLParserFactory

import javax.json.Json
import javax.json.JsonReader
import javax.xml.bind.UnmarshalException
import javax.xml.bind.Unmarshaller


/**
 * Trait for controllers given access to easy parsing and rendering of JSON and XML data
 * using the methods asMessage and render.
 *
 * Created by philip on 2018-03-16.
 */
@Enhances("Controller")
trait ControllerTrait implements WebAttributes, ResponseRenderer, ServletAttributes{

    /**
     * Method to parse request data from a format during content negotiation to
     * a Message.
     *
     * @param messageType the Message class to parse data into.
     * @return a parsed Message.
     * @throws IllegalArgumentException if input data was invalid.
     */
    Message asMessage(Class messageType) throws IllegalArgumentException{

        Message retval

        request.withFormat {
            json {
                String data
                try {
                    data = request.inputStream.text
                    JsonReader jsonReader = Json.createReader(new StringReader(data))
                    retval = messageType.newInstance(jsonReader)
                }catch(Exception e){
                    throw new IllegalArgumentException("Error parsing input JSON data: '" +
                            (data == null ? "" : data) +
                            "', message: " + (e.message == null ? "" : e.message))
                }
            }
            xml {
                byte[] data
                try {
                    data = request.inputStream.bytes
                    retval = getXmlParser().unmarshall(data)
                }catch(Exception e){
                    throw new IllegalArgumentException("Error parsing input XML data: '" +
                            (data == null ? "" : new String(data,"UTF-8")) +
                            "', message: " + getXMLErrorMessage(e))
                }
            }
        }
        return retval
    }

    /**
     * Method to render a message to JSON or XML using format during content negotiation.
     * @param message
     */
    void render(Message message){

        response.withFormat {
            json {
                render text: message.toJsonAsString(false), contentType: getResponseContentType(['application/json', 'text/json'])
            }
            xml {
                render text: new String(getXmlParser().marshall(message),"UTF-8"), contentType: getResponseContentType(['application/xml', 'text/xml'])
            }
        }
    }

    XMLParser xmlParser = null
    private XMLParser getXmlParser(){
        if(xmlParser == null){
            xmlParser = new XMLParserFactory().getXMLParser("1.0")
        }
        return xmlParser
    }

    private String getXMLErrorMessage(Exception e){
        String retval = null
        if(e.message == null) {
            if (e instanceof UnmarshalException) {
                retval = ((UnmarshalException) e).linkedException.message
            }
        }

        if(retval == null){
            retval == ""
        }

        return retval
    }

    private getResponseContentType(List supported){
        String acceptHeader = request.getHeader("Accept")
        if(supported.contains(acceptHeader)){
            return acceptHeader
        }
        return supported[0]
    }


}
