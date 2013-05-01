/*
 * Copyright 2012 Midokura Pte. Ltd.
 */
package org.midonet.midolman

import rules.{RuleResult, NatTarget, Condition}
import java.util.{HashSet => JHashSet, UUID}

import akka.testkit.TestProbe
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.slf4j.LoggerFactory
import guice.actors.OutgoingMessage

import org.midonet.midolman.DeduplicationActor.DiscardPacket
import layer3.Route
import layer3.Route.NextHop
import topology.LocalPortActive
import org.midonet.packets._
import topology.VirtualToPhysicalMapper.HostRequest
import util.SimulationHelper
import org.midonet.cluster.data.ports.{LogicalRouterPort, MaterializedBridgePort,
    MaterializedRouterPort}
import util.RouterHelper
import org.midonet.midolman.PacketWorkflowActor.PacketIn


@RunWith(classOf[JUnitRunner])
class FloatingIpTestCase extends VirtualConfigurationBuilders with RouterHelper {
    private final val log = LoggerFactory.getLogger(classOf[FloatingIpTestCase])

    // Router port one connecting to host VM1
    val routerIp1 = IPv4Addr.fromString("192.168.111.1")
    val routerRange1 = new IPv4Subnet(IPv4Addr.fromString("192.168.111.0"), 24)
    val routerMac1 = MAC.fromString("22:aa:aa:ff:ff:ff")
    // Interior router port connecting to bridge
    val routerIp2 = IPv4Addr.fromString("192.168.222.1")
    val routerRange2 = new IPv4Subnet(IPv4Addr.fromString("192.168.222.0"), 24)
    val routerMac2 = MAC.fromString("22:ab:cd:ff:ff:ff")
    // VM1: remote host to ping
    val vm1Ip = IPv4Addr.fromString("192.168.111.2")
    val vm1Mac = MAC.fromString("02:23:24:25:26:27")
    // VM2
    val vm2Ip = IPv4Addr.fromString("192.168.222.2")
    val vm2Mac = MAC.fromString("02:DD:AA:DD:AA:03")

    val subnet1 = new IPv4Subnet(IPv4Addr.fromString("192.168.111.0"), 24)
    val subnet2 = new IPv4Subnet(IPv4Addr.fromString("192.168.222.0"), 24)

    // Other stuff
    var brPort2 : MaterializedBridgePort = null
    val vm2PortName = "VM2"
    var vm2PortNumber = 0
    var rtrPort1 : MaterializedRouterPort = null
    val rtrPort1Name = "RouterPort1"
    var rtrPort1Num = 0
    var rtrPort2 : LogicalRouterPort = null

    val floatingIP = IPv4Addr.fromString("10.0.173.5")

    private var packetsEventsProbe: TestProbe = null

    override def beforeTest() {
        packetsEventsProbe = newProbe()
        actors().eventStream.subscribe(packetsEventsProbe.ref, classOf[PacketsExecute])

        val host = newHost("myself", hostId())
        host should not be null
        val router = newRouter("router")
        router should not be null

        initializeDatapath() should not be (null)
        requestOfType[HostRequest](vtpProbe())
        requestOfType[OutgoingMessage](vtpProbe())

        // set up materialized port on router
        rtrPort1 = newExteriorRouterPort(router, routerMac1,
            routerIp1.toString,
            routerRange1.getAddress.toString,
            routerRange1.getPrefixLen)
        rtrPort1 should not be null
        materializePort(rtrPort1, host, rtrPort1Name)
        val portEvent = requestOfType[LocalPortActive](portsProbe)
        portEvent.active should be(true)
        portEvent.portID should be(rtrPort1.getId)
        dpController().underlyingActor.vifToLocalPortNumber(rtrPort1.getId) match {
            case Some(portNo : Short) => rtrPort1Num = portNo
            case None => fail("Not able to find data port number for Router port 1")
        }

        newRoute(router, "0.0.0.0", 0,
            routerRange1.getAddress.toString, routerRange1.getPrefixLen,
            NextHop.PORT, rtrPort1.getId,
            new IntIPv4(Route.NO_GATEWAY).toUnicastString, 10)

        // set up logical port on router
        rtrPort2 = newInteriorRouterPort(router, routerMac2,
            routerIp2.toString(),
            routerRange2.getAddress.toString,
            routerRange2.getPrefixLen)
        rtrPort2 should not be null

        newRoute(router, "0.0.0.0", 0,
            routerRange2.getAddress.toString, routerRange2.getPrefixLen,
            NextHop.PORT, rtrPort2.getId,
            new IntIPv4(Route.NO_GATEWAY).toUnicastString, 10)

        // create bridge link to router's logical port
        val bridge = newBridge("bridge")
        bridge should not be null

        val brPort1 = newInteriorBridgePort(bridge)
        brPort1 should not be null
        clusterDataClient().portsLink(rtrPort2.getId, brPort1.getId)

        // add a materialized port on bridge, logically connect to VM2
        brPort2 = newExteriorBridgePort(bridge)
        brPort2 should not be null

        materializePort(brPort2, host, vm2PortName)
        requestOfType[LocalPortActive](portsProbe)
        dpController().underlyingActor.vifToLocalPortNumber(brPort2.getId) match {
            case Some(portNo : Short) => vm2PortNumber = portNo
            case None => fail("Not able to find data port number for bridge port 2")
        }

        log.info("Setting up rule chain")

        val preChain = newInboundChainOnRouter("pre_routing", router)
        val postChain = newOutboundChainOnRouter("post_routing", router)

        // DNAT rule
        // assign floatingIP to VM1's private addr
        val dnatCond = new Condition()
        dnatCond.nwDstIp = new IPv4Subnet(floatingIP, 32)
        val dnatTarget = new NatTarget(vm1Ip.toIntIPv4().addressAsInt,
                                       vm1Ip.toIntIPv4().addressAsInt, 0, 0)
        val dnatRule = newForwardNatRuleOnChain(preChain, 1, dnatCond,
            RuleResult.Action.ACCEPT, Set(dnatTarget), isDnat = true)
        dnatRule should not be null

        // SNAT rules
        // assign floatingIP to VM1's private addr
        val snatCond = new Condition()
        snatCond.nwSrcIp = subnet1
        snatCond.outPortIds = new JHashSet[UUID]()
        snatCond.outPortIds.add(rtrPort2.getId)
        val snatTarget = new NatTarget(floatingIP.toIntIPv4().addressAsInt,
                                       floatingIP.toIntIPv4().addressAsInt, 0, 0)
        val snatRule = newForwardNatRuleOnChain(postChain, 1, snatCond,
            RuleResult.Action.ACCEPT, Set(snatTarget), isDnat = false)
        snatRule should not be null

        // TODO needed?
        clusterDataClient().routersUpdate(router)

        flowProbe().expectMsgType[DatapathController.DatapathReady].datapath should not be (null)
        drainProbes()
    }

    def test() {
        log.info("Feeding ARP cache on VM2")
        feedArpCache(vm2PortName, vm2Ip.getIntAddress(), vm2Mac,
            routerIp2.getIntAddress(), routerMac2)
        requestOfType[PacketIn](packetInProbe)
        requestOfType[DiscardPacket](discardPacketProbe)
        drainProbes()

        log.info("Feeding ARP cache on VM1")
        feedArpCache(rtrPort1Name, vm1Ip.getIntAddress(), vm1Mac,
            routerIp1.toIntIPv4().addressAsInt(), routerMac1)
        requestOfType[PacketIn](packetInProbe)
        requestOfType[DiscardPacket](discardPacketProbe)
        drainProbes()

        log.info("Sending a tcp packet VM2 -> floating IP, should be DNAT'ed")
        injectTcp(vm2PortName, vm2Mac, vm2Ip.toIntIPv4(), 20301, routerMac2,
                  floatingIP.toIntIPv4(), 80, syn = true)
        var pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        var eth = applyOutPacketActions(pktOut)
        log.debug("Packet out: {}", pktOut)
        var ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceIPAddress should be === vm2Ip
        ipPak.getDestinationIPAddress should be === vm1Ip

        log.info("Replying with tcp packet floatingIP -> VM2, should be SNAT'ed")
        injectTcp(rtrPort1Name, vm1Mac, vm1Ip.toIntIPv4(), 20301, routerMac1,
                  vm2Ip.toIntIPv4(), 80, syn = true)
        pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        eth = applyOutPacketActions(pktOut)
        log.debug("packet out: {}", pktOut)
        ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceAddress should be (floatingIP.toIntIPv4().addressAsInt)
        ipPak.getDestinationAddress should be (vm2Ip.toIntIPv4().addressAsInt)

        log.info("Sending tcp packet from VM2 -> VM1, private ips, no NAT")
        injectTcp(vm2PortName, vm2Mac, vm2Ip.toIntIPv4(), 20301, routerMac2,
                  vm1Ip.toIntIPv4(), 80, syn = true)
        pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        eth = applyOutPacketActions(pktOut)
        log.debug("packet out: {}", pktOut)
        ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceAddress should be (vm2Ip.toIntIPv4().addressAsInt)
        ipPak.getDestinationAddress should be (vm1Ip.toIntIPv4().addressAsInt)

        log.info("ICMP echo, VM1 -> VM2, should be SNAT'ed")
        injectIcmpEchoReq(rtrPort1Name, vm1Mac, vm1Ip.toIntIPv4(), routerMac1,
                          vm2Ip.toIntIPv4())
        pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        eth = applyOutPacketActions(pktOut)
        log.debug("packet out: {}", pktOut)
        ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceAddress should be (floatingIP.toIntIPv4().addressAsInt)
        ipPak.getDestinationAddress should be (vm2Ip.toIntIPv4().addressAsInt)

        log.info("ICMP echo, VM2 -> floatingIp, should be DNAT'ed")
        injectIcmpEchoReq(rtrPort1Name, vm2Mac, vm2Ip.toIntIPv4(), routerMac1,
                          floatingIP.toIntIPv4())
        pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        eth = applyOutPacketActions(pktOut)
        log.debug("packet out: {}", pktOut)
        ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceAddress should be (vm2Ip.toIntIPv4().addressAsInt)
        ipPak.getDestinationAddress should be (vm1Ip.toIntIPv4().addressAsInt)

        log.info("ICMP echo, VM1 -> floatingIp, should be DNAT'ed, but not SNAT'ed")
        injectIcmpEchoReq(rtrPort1Name, vm1Mac, vm1Ip.toIntIPv4(), routerMac1,
                          floatingIP.toIntIPv4())
        pktOut = requestOfType[PacketsExecute](packetsEventsProbe).packet
        pktOut should not be null
        pktOut.getPacket should not be null
        eth = applyOutPacketActions(pktOut)
        log.debug("packet out: {}", pktOut)
        ipPak = eth.getPayload.asInstanceOf[IPv4]
        ipPak should not be null
        ipPak.getSourceAddress should be (vm1Ip.toIntIPv4().addressAsInt)
        ipPak.getDestinationAddress should be (vm1Ip.toIntIPv4().addressAsInt)

    }

}