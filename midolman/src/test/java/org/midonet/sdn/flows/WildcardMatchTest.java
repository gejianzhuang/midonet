/*
 * Copyright 2012 Midokura Europe SARL
 */

package org.midonet.sdn.flows;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.midonet.odp.FlowMatches.tcpFlow;

import org.midonet.odp.FlowMatch;
import org.midonet.odp.FlowMatches;
import org.midonet.packets.IntIPv4;
import org.midonet.packets.IPAddr;
import org.midonet.packets.IPv4Addr;

public class WildcardMatchTest {

    @Test
    public void testDefaultCtor() {
        WildcardMatch wmatch = new WildcardMatch();
        assertThat(wmatch.getUsedFields(), hasSize(0));
    }

    @Test
    public void testSetDlDest() {
        WildcardMatch wmatch = new WildcardMatch();
        byte[] dlDest = { 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };
        String dlDestStr = "0a:0b:0c:0d:0e:0f";
        wmatch.setDataLayerDestination(dlDestStr);
        Assert.assertArrayEquals(dlDest, wmatch.getDataLayerDestination());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.EthernetDestination));
    }

    @Test
    public void testSetDlSource() {
        WildcardMatch wmatch = new WildcardMatch();
        byte[] dlSource = { 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };
        String dlSourceStr = "0a:0b:0c:0d:0e:0f";
        wmatch.setDataLayerSource(dlSourceStr);
        Assert.assertArrayEquals(dlSource, wmatch.getDataLayerSource());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.EthernetSource));
    }

    @Test
    public void testSetDlType() {
        WildcardMatch wmatch = new WildcardMatch();
        short dlType = 0x11ee;
        wmatch.setDataLayerType(dlType);
        Assert.assertEquals(dlType, wmatch.getDataLayerType());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.EtherType));
    }

    @Test
    public void testSetInputPort() {
        WildcardMatch wmatch = new WildcardMatch();
        short inPort = 0x11ee;
        wmatch.setInputPort(inPort);
        assertThat(wmatch.getInputPort(), is(inPort));
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.InputPortNumber));
    }

    @Test
    public void testSetNwProto() {
        WildcardMatch wmatch = new WildcardMatch();
        byte nwProto = 0x11;
        wmatch.setNetworkProtocol(nwProto);
        Assert.assertEquals(nwProto, wmatch.getNetworkProtocol());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.NetworkProtocol));
    }

    @Test
    public void testSetTpDest() {
        WildcardMatch wmatch = new WildcardMatch();
        int tpDest = 0x11ee;
        wmatch.setTransportDestination(tpDest);
        Assert.assertEquals(tpDest, wmatch.getTransportDestination());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.TransportDestination));
    }

    @Test
    public void testSetTpDestHigh() {
        WildcardMatch wmatch = new WildcardMatch();
        int tpDest = 0xA8CA;
        wmatch.setTransportDestination(tpDest);
        Assert.assertEquals(tpDest, wmatch.getTransportDestination());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
                contains(WildcardMatch.Field.TransportDestination));
    }

    @Test
    public void testSetTpSource() {
        WildcardMatch wmatch = new WildcardMatch();
        int tpSource = 0x11ee;
        wmatch.setTransportSource(tpSource);
        Assert.assertEquals(tpSource, wmatch.getTransportSource());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.TransportSource));
    }

    @Test
    public void testSetTpSourceHigh() {
        WildcardMatch wmatch = new WildcardMatch();
        int tpSource = 0xA8CA;
        wmatch.setTransportSource(tpSource);
        Assert.assertEquals(tpSource, wmatch.getTransportSource());
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
                contains(WildcardMatch.Field.TransportSource));
    }

    @Test
    public void testSetNwDst_networkRange() {
        int expectedLen = 32;
        WildcardMatch wmatch = new WildcardMatch();
        int nwDest = 0x12345678;
        wmatch.setNetworkDestination(new IPv4Addr().setIntAddress(nwDest));
        IPAddr ipDst = wmatch.getNetworkDestinationIP();
        assertThat(ipDst, notNullValue());
        Assert.assertEquals(ipDst, new IPv4Addr().setIntAddress(nwDest));
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.NetworkDestination));
    }

    @Test
    public void testSetNwDst_unicastAddress() {
        WildcardMatch wmatch = new WildcardMatch();
        int nwDest = 0x12345678;
        wmatch.setNetworkDestination(new IPv4Addr().setIntAddress(nwDest));
        IPAddr ipDst = wmatch.getNetworkDestinationIP();
        assertThat(ipDst, notNullValue());
        Assert.assertEquals(ipDst, new IPv4Addr().setIntAddress(nwDest));
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
                contains(WildcardMatch.Field.NetworkDestination));
    }

    @Test
    public void testSetNwSrc_networkRange() {
        int expectedLen = 32;
        WildcardMatch wmatch = new WildcardMatch();
        int nwSource = 0x12345678;
        wmatch.setNetworkSource(new IPv4Addr().setIntAddress(nwSource));
        IPAddr ipSrc = wmatch.getNetworkSourceIP();
        assertThat(ipSrc, notNullValue());
        Assert.assertEquals(ipSrc, new IPv4Addr().setIntAddress(nwSource));
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
            contains(WildcardMatch.Field.NetworkSource));
    }

    @Test
    public void testSetNwSrc_unicastAddress() {
        WildcardMatch wmatch = new WildcardMatch();
        int nwSource = 0x12345678;
        wmatch.setNetworkSource(new IPv4Addr().setIntAddress(nwSource));
        IPAddr ipSrc = wmatch.getNetworkSourceIP();
        assertThat(ipSrc, notNullValue());
        Assert.assertEquals(ipSrc, new IPv4Addr().setIntAddress(nwSource));
        assertThat(wmatch.getUsedFields(), hasSize(1));
        assertThat(wmatch.getUsedFields(),
                contains(WildcardMatch.Field.NetworkSource));
    }

    @Test
    public void testEqualityRelationByProjection() {

        WildcardMatch wildcard =
            WildcardMatch.fromFlowMatch(
                tcpFlow("ae:b3:77:8c:a1:48", "33:33:00:00:00:16",
                        "192.168.100.1", "192.168.100.2",
                        8096, 1025));

        WildcardMatch projection = wildcard.project(EnumSet.of(
                WildcardMatch.Field.EthernetSource,
                WildcardMatch.Field.EthernetDestination));

        assertThat("A wildcard should not match a projection smaller than it",
                   wildcard, not(equalTo(projection)));

        assertThat("A project should be equal to a wildcard bigger than it.",
                   projection, equalTo(wildcard));
    }

    @Test
    public void testFindableInMap() {
        WildcardMatch wildcard =
            WildcardMatch.fromFlowMatch(
                tcpFlow("ae:b3:77:8c:a1:48", "33:33:00:00:00:16",
                        "192.168.100.1", "192.168.100.2",
                        8096, 1025));

        WildcardMatch projection = wildcard.project(EnumSet.of(
            WildcardMatch.Field.EthernetSource,
            WildcardMatch.Field.EthernetDestination));

        // make a simple wildcard that is a copy of the projection
        WildcardMatch copy = new WildcardMatch();
        copy.setEthernetDestination(projection.getEthernetDestination());
        copy.setEthernetSource(projection.getEthernetSource());

        Map<WildcardMatch, Boolean> map = new HashMap<WildcardMatch, Boolean>();
        map.put(copy, Boolean.TRUE);

        assertThat(
            "We should be able to retrieve a wildcard flow by projection",
            map.get(projection), is(notNullValue()));

        assertThat(
            "We should be able to retrieve a wildcard flow by projection",
            map.get(projection), is(true));
    }

    @Test
    public void testFromFlowMatch() {
        FlowMatch fm = FlowMatches.tcpFlow(
            "02:aa:dd:dd:aa:01", "02:bb:ee:ee:ff:01",
            "192.168.100.2", "192.168.100.3",
            40000, 50000);
        WildcardMatch wcm = WildcardMatch.fromFlowMatch(fm);
        assertThat(wcm.getTransportSourceObject(),
                   equalTo(40000));
        assertThat(wcm.getTransportDestinationObject(),
                   equalTo(50000));

    }
}