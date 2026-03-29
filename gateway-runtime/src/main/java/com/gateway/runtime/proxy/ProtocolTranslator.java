package com.gateway.runtime.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Translates between REST (JSON) and SOAP (XML) protocols.
 * Supports wrapping JSON payloads in SOAP envelopes and extracting
 * JSON from SOAP responses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProtocolTranslator {

    private final ObjectMapper jsonMapper;

    /**
     * Convert a JSON body to a SOAP envelope.
     *
     * @param jsonBody   the JSON request body
     * @param soapAction the SOAP action to include in the envelope
     * @return SOAP XML bytes
     */
    public byte[] restToSoap(byte[] jsonBody, String soapAction) {
        try {
            JsonNode json = jsonMapper.readTree(jsonBody);
            String jsonContent = jsonMapper.writeValueAsString(json);

            // Build a minimal SOAP 1.1 envelope with the JSON content embedded
            String soapEnvelope = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap:Header/>
                      <soap:Body>
                        <%s>
                          <jsonPayload><![CDATA[%s]]></jsonPayload>
                        </%s>
                      </soap:Body>
                    </soap:Envelope>
                    """.formatted(
                    soapAction != null ? soapAction : "request",
                    jsonContent,
                    soapAction != null ? soapAction : "request");

            log.debug("Translated REST to SOAP, action={}", soapAction);
            return soapEnvelope.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("REST-to-SOAP translation failed: {}", e.getMessage());
            return jsonBody;
        }
    }

    /**
     * Convert a SOAP XML body to JSON by stripping the SOAP envelope
     * and converting the body XML to JSON.
     *
     * @param xmlBody the SOAP XML response body
     * @return JSON bytes
     */
    public byte[] soapToRest(byte[] xmlBody) {
        try {
            String xml = new String(xmlBody, StandardCharsets.UTF_8);

            // Extract content between <soap:Body> tags
            int bodyStart = xml.indexOf("<soap:Body>");
            int bodyEnd = xml.indexOf("</soap:Body>");
            if (bodyStart == -1 || bodyEnd == -1) {
                // Try alternative namespace prefix
                bodyStart = xml.indexOf("<SOAP-ENV:Body>");
                bodyEnd = xml.indexOf("</SOAP-ENV:Body>");
            }

            String bodyContent;
            if (bodyStart != -1 && bodyEnd != -1) {
                bodyContent = xml.substring(
                        xml.indexOf('>', bodyStart) + 1,
                        bodyEnd).trim();
            } else {
                bodyContent = xml;
            }

            // Check for embedded JSON in CDATA
            int cdataStart = bodyContent.indexOf("<![CDATA[");
            if (cdataStart != -1) {
                int cdataEnd = bodyContent.indexOf("]]>");
                if (cdataEnd != -1) {
                    String jsonContent = bodyContent.substring(cdataStart + 9, cdataEnd);
                    log.debug("Extracted JSON from SOAP CDATA");
                    return jsonContent.getBytes(StandardCharsets.UTF_8);
                }
            }

            // Otherwise, convert XML body to JSON using XmlMapper
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode xmlNode = xmlMapper.readTree(bodyContent);
            byte[] result = jsonMapper.writeValueAsBytes(xmlNode);
            log.debug("Translated SOAP to REST");
            return result;
        } catch (Exception e) {
            log.error("SOAP-to-REST translation failed: {}", e.getMessage());
            return xmlBody;
        }
    }
}
