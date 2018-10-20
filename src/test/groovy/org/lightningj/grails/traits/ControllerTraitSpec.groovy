/*
 *************************************************************************
 *                                                                       *
 *  LightningJ                                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public License   *
 *  (LGPL-3.0-or-later)                                                  *
 *  License as published by the Free Software Foundation; either         *
 *  version 3 of the License, or any later version.                      *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.lightningj.grails.traits

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.lightningj.lnd.wrapper.message.WalletBalanceResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Unit tests for ControllerTrait
 * Created by philip on 2018-03-16.
 */
class ControllerTraitSpec extends Specification {

    ControllerTrait controllerTrait

    def "Verify that if request format is JSON is JSON parsed"(){
        setup:
        controllerTrait = new TestController(mockRequest('{"confirmed_balance":400, "total_balance": 500}','json'))
        when:
        WalletBalanceResponse m = controllerTrait.asMessage WalletBalanceResponse
        then:
        m.totalBalance == 500
    }

    def "Verify that if request format is JSON and input data is invalid is IllegalArgumentException thrown"(){
        setup:
        controllerTrait = new TestController(mockRequest('{"confirmed_balance":400, "total_balance": "500"}','json'))
        when:
        controllerTrait.asMessage WalletBalanceResponse
        then:
        def e = thrown IllegalArgumentException
        e.message == 'Error parsing input JSON data: \'{"confirmed_balance":400, "total_balance": "500"}\', message: Error converting JSON to Message: org.glassfish.json.JsonStringImpl cannot be cast to javax.json.JsonNumber'

    }

    def "Verify that if request format is XML is XML parsed"(){
        setup:
        controllerTrait = new TestController(mockRequest('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><WalletBalanceResponse xmlns="http://lightningj.org/xsd/lndjapi_1_0"><totalBalance>500</totalBalance><confirmedBalance>400</confirmedBalance><unconfirmedBalance>0</unconfirmedBalance></WalletBalanceResponse>','xml'))
        when:
        WalletBalanceResponse m = controllerTrait.asMessage ()
        then:
        m.totalBalance == 500
    }

    def "Verify that if request format is XML and input data is invalid is IllegalArgumentException thrown"(){
        setup:
        controllerTrait = new TestController(mockRequest('{"confirmed_balance":400, "total_balance": 500}','xml'))
        when:
        controllerTrait.asMessage WalletBalanceResponse
        then:
        def e = thrown IllegalArgumentException
        e.message == "Error parsing input XML data: '{\"confirmed_balance\":400, \"total_balance\": 500}', message: Content is not allowed in prolog."

    }

    def "Verify that render generates JSON data if format is json"(){
        setup:
        controllerTrait = new TestController(mockRequest('{"confirmed_balance":400, "total_balance": 500}','json'))
        when:
        WalletBalanceResponse m = controllerTrait.asMessage WalletBalanceResponse
        m.setTotalBalance(1000)
        controllerTrait.render(m)
        then:
        controllerTrait.response.contentType == "application/json;charset=utf-8"
        controllerTrait.response.format == "json"
        controllerTrait.response.contentAsString == "{\"total_balance\":1000,\"confirmed_balance\":400,\"unconfirmed_balance\":0}"
    }

    def "Verify that render generates XML data if format is xml"(){
        setup:
        controllerTrait = new TestController(mockRequest('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><WalletBalanceResponse xmlns="http://lightningj.org/xsd/lndjapi_1_0"><totalBalance>500</totalBalance><confirmedBalance>400</confirmedBalance><unconfirmedBalance>0</unconfirmedBalance></WalletBalanceResponse>','xml'))
        when:
        WalletBalanceResponse m = controllerTrait.asMessage WalletBalanceResponse
        m.setTotalBalance(1000)
        controllerTrait.render(m)
        then:
        controllerTrait.response.contentType == "application/xml;charset=utf-8"
        controllerTrait.response.format == "xml"
        controllerTrait.response.contentAsString == "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><WalletBalanceResponse xmlns=\"http://lightningj.org/xsd/lndjapi_1_0\"><totalBalance>1000</totalBalance><confirmedBalance>400</confirmedBalance><unconfirmedBalance>0</unconfirmedBalance></WalletBalanceResponse>"
    }

    private def mockRequest(String data, String format){
        def request =  new MockHttpServletRequest(
                characterEncoding: "UTF-8",
                contentType: format == 'json' ? "application/json" : "application/xml",
                content: data.bytes)
        request.addHeader("Accept", 'json' ? "application/json" : "application/xml")
        request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT,format)
        return request
    }

    class TestController implements ControllerTrait{

        HttpServletRequest request
        HttpServletResponse response

        TestController(HttpServletRequest request, HttpServletResponse response = new MockHttpServletResponse()){
            this.request = request
            this.response = response


            GrailsWebRequest webRequest = new GrailsWebRequest(request,response, new MockServletContext())
            RequestContextHolder.setRequestAttributes(webRequest)

        }

        @Override
        HttpServletRequest getRequest(){
            return request
        }

        @Override
        HttpServletResponse getResponse(){
            return response
        }


    }
}
