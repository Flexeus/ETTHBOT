package com.uospd.switches;

import com.uospd.switches.exceptions.NoSnmpAnswerException;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SnmpManager{
    private final static int    SNMP_RETRIES   = 3;
    private final static long   SNMP_TIMEOUT   = 1000L;
    private Snmp snmp;
    private TransportMapping<UdpAddress> transport;
    private Target t;

    public SnmpManager(String ip, String community) throws IOException{
          t = getTarget("udp:"+ip+"/161",community);
          transport = new DefaultUdpTransportMapping();
          snmp = new Snmp(transport);
          transport.listen();
    }

    private String send(Target target, String oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);
        ResponseEvent event = snmp.send(pdu, target, null);
        if (event != null) return event.getResponse().get(0).getVariable().toString();
        return "Timeout exceeded";
    }


    public void snmpSet(String oid, int Value) {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid), new Integer32(Value)));
            pdu.setType(PDU.SET);
            ResponseListener listener = new ResponseListener() {
                public void onResponse(ResponseEvent event) {
                    PDU strResponse;
                    ((Snmp) event.getSource()).cancel(event.getRequest(), this);
                    strResponse = event.getResponse();
                    if (strResponse != null) {
                        String result = strResponse.getErrorStatusText();
                        if(!result.equals("Success")) System.out.println("Set Status is: " + result);
                    }
                }
            };
            snmp.send(pdu, t, null, listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void snmpSetString(String oid, String Value) {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid), new OctetString(Value)));
            pdu.setType(PDU.SET);
            ResponseListener listener = new ResponseListener() {
                public void onResponse(ResponseEvent event) {
                    PDU strResponse;
                    ((Snmp) event.getSource()).cancel(event.getRequest(), this);
                    strResponse = event.getResponse();
                    if (strResponse != null) {
                        String result = strResponse.getErrorStatusText();
                        if(!result.equals("Success")) System.out.println("Set Status is: " + result);
                    }
                }
            };
            snmp.send(pdu, t, null, listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public String getResponse(String str) throws NoSnmpAnswerException{
        if(snmp == null) throw new NullPointerException("No SNMP Connection");
        try {
            String res = send2(t, str);
            if (res == null) throw new NullPointerException("SNMP answer is null");
            return res;
        } catch (IOException e) {
            throw new NullPointerException("Something went wrong in snmp response");
        }
    }

    private Target<Address> getTarget(String address, String community) {
        Address targetAddress = GenericAddress.parse(address);
        CommunityTarget<Address> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setRetries(SNMP_RETRIES);
        target.setTimeout(SNMP_TIMEOUT);
        target.setVersion(SnmpConstants.version2c);
        return target;
    }

    public void stop() throws IOException {
        try {
            if (transport != null) {
                transport.close();
                transport = null;
            }
        } finally {
            if (snmp != null) {
                snmp.close();
                snmp = null;
            }
        }
    }

    public Map<String, String> doWalk(String tableOid)
    {
        Map<String, String> result = new TreeMap<>();
        TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
        List<TreeEvent> events = treeUtils.getSubtree(t, new OID(tableOid));
        if (events == null || events.size() == 0) {
            throw new RuntimeException("Error: Unable to read table...");
            //return result;
        }
        for (TreeEvent event : events) {
            if (event == null) continue;
            if (event.isError()) {
                System.out.println("Error: table OID [" + tableOid + "] " + event.getErrorMessage());
                continue;
            }
            VariableBinding[] varBindings = event.getVariableBindings();
            if (varBindings == null || varBindings.length == 0)  continue;
            for (VariableBinding varBinding : varBindings) {
                if (varBinding == null) continue;
                String varaible= varBinding.getVariable().toString();
                result.put("." + varBinding.getOid().toString(), varaible);
            }
        }
        return result;
    }

    public Map<String, String> walkExtract(String tableOid)
    {
        Map<String, String> result = new TreeMap<>();
        TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
        List<TreeEvent> events = treeUtils.getSubtree(t, new OID(tableOid));
        long count = tableOid.chars().filter(c -> !Character.isDigit(c)).count();
        if (events == null || events.size() == 0) {
            throw new RuntimeException("Error: Unable to read table...");
        }
        for (TreeEvent event : events) {
            if (event == null) continue;
            if (event.isError()) {
                System.out.println("Error: table OID [" + tableOid + "] " + event.getErrorMessage());
                continue;
            }
            VariableBinding[] varBindings = event.getVariableBindings();
            if (varBindings == null || varBindings.length == 0) continue;

            for (VariableBinding varBinding : varBindings) {
                if (varBinding == null) continue;
                result.put(varBinding.getOid().subOID((int) count).toString(), varBinding.getVariable().toString());
            }
        }
        return result;
    }

    private String send2(Target target, String oid) throws IOException, NoSnmpAnswerException{
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);
        ResponseEvent event = snmp.send(pdu, target, null);
        if(event == null) return "Timeout exceeded";
        var response = event.getResponse();
        int errorIndex = response.getErrorIndex();
        if(errorIndex > 0) throw new NoSnmpAnswerException(String.format("errorStatus=%s(%d), errorIndex=%d. VBS:%s",
                    response.getErrorStatusText(), response.getErrorStatus(), errorIndex, response.getVariableBindings().get(0)));
        return response.get(0).getVariable().toString();
    }


}
