/*
 * Copyright (c) 2013 Plexxi, Inc.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.affinity.analytics.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.affinity.affinity.AffinityGroup;
import org.opendaylight.affinity.affinity.AffinityLink;
import org.opendaylight.affinity.affinity.IAffinityManager;
import org.opendaylight.affinity.analytics.IAnalyticsManager;
import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.core.Host;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.address.EthernetAddress;
import org.opendaylight.controller.sal.reader.FlowOnNode;
import org.opendaylight.controller.sal.reader.IReadServiceListener;
import org.opendaylight.controller.sal.reader.NodeConnectorStatistics;
import org.opendaylight.controller.sal.reader.NodeDescription;
import org.opendaylight.controller.sal.reader.NodeTableStatistics;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.statisticsmanager.IStatisticsManager;

public class AnalyticsManager implements IReadServiceListener, IAnalyticsManager {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsManager.class);

    private IAffinityManager affinityManager;
    private IStatisticsManager statisticsManager;
    private IfIptoHost hostTracker;

    private Map<MatchField, Host> destinationHostCache;
    private Map<MatchField, Host> sourceHostCache;
    private Map<Host, Map<Host, HostStats>> hostsToStats;

    void init() {
        log.debug("INIT called!");
        this.destinationHostCache = new HashMap<MatchField, Host>();
        this.sourceHostCache = new HashMap<MatchField, Host>();
        this.hostsToStats = new HashMap<Host, Map<Host, HostStats>>();
    }

    void destroy() {
        log.debug("DESTROY called!");
    }

    void start() {
        log.debug("START called!");
    }

    void started(){
    }

    void stop() {
        log.debug("STOP called!");
    }

    void setAffinityManager(IAffinityManager a) {
        this.affinityManager = a;

        // TODO: Testing
        AffinityGroup ag1 = new AffinityGroup("testAG1");
        ag1.add("10.0.0.1");
        ag1.add("10.0.0.2");
        AffinityGroup ag2 = new AffinityGroup("testAG2");
        ag2.add("10.0.0.3");
        ag2.add("10.0.0.4");
        this.affinityManager.addAffinityGroup(ag1);
        this.affinityManager.addAffinityGroup(ag2);
        AffinityLink al = new AffinityLink("testAL", ag1, ag2);
        this.affinityManager.addAffinityLink(al);
        // TODO: End testing
    }

    void unsetAffinityManager(IAffinityManager a) {
        if (this.affinityManager.equals(a)) {
            this.affinityManager = null;
        }
    }

    void setStatisticsManager(IStatisticsManager s) {
        this.statisticsManager = s;
    }

    void unsetStatisticsManager(IStatisticsManager s) {
        if (this.statisticsManager.equals(s)) {
            this.statisticsManager = null;
        }
    }

    void setHostTracker(IfIptoHost h) {
        this.hostTracker = h;
    }

    void unsetHostTracker(IfIptoHost h) {
        if (this.hostTracker.equals(h)) {
            this.hostTracker = null;
        }
    }

    /* Returns the destination host associated with this flow, if one
     * exists.  Returns null otherwise.
     */
    protected Host getDestinationHostFromFlow(Flow flow, Set<HostNodeConnector> hosts) {
        Match match = flow.getMatch();
        MatchField dst = null;

        // Flow has to have DL_DST field or NW_DST field to proceed
        if (match.isPresent(MatchType.DL_DST)) {
            dst = match.getField(MatchType.DL_DST);
        } else if (match.isPresent(MatchType.NW_DST)) {
            dst = match.getField(MatchType.NW_DST);
        } else { 
            return null;
        }

        // Check cache
        Host cacheHit = this.destinationHostCache.get(dst);
        if (cacheHit != null) {
            return cacheHit;
        }

        // Find the destination host
        Host dstHost = null;
        for (HostNodeConnector h : hosts) {
            
            // DL_DST => compare on MAC address strings
            if (match.isPresent(MatchType.DL_DST)) {
                String dstMac = MatchType.DL_DST.stringify(dst.getValue());
                String hostMac = ((EthernetAddress) h.getDataLayerAddress()).getMacAddress();
                if (dstMac.equals(hostMac)) {
                    dstHost = h;
                    this.destinationHostCache.put(dst, dstHost); // Add to cache
                    break;
                }
            }
          
            // NW_DST => compare on IP address (of type InetAddress)
            else if (match.isPresent(MatchType.NW_DST)) {
                InetAddress hostIP = h.getNetworkAddress();
                if (dst.getValue().equals(hostIP)) {
                    dstHost = h;
                    this.destinationHostCache.put(dst, dstHost); // Add to cache
                    break;
                }
            }
        }

        return dstHost;
    }

    /* Returns the source Host associated with this flow, if one
     * exists.  Returns null otherwise.
     */
    protected Host getSourceHostFromFlow(Flow flow, Set<HostNodeConnector> hosts) {

        Host srcHost = null;
        Match match = flow.getMatch();

        // Flow must have IN_PORT field (DL_SRC rarely (never?)
        // exists).
        if (match.isPresent(MatchType.IN_PORT)) {
            MatchField inPort = match.getField(MatchType.IN_PORT);

            // Check cache
            Host cacheHit = this.sourceHostCache.get(inPort);
            if (cacheHit != null) {
                return cacheHit;
            }

            // Find the source host by comparing the NodeConnectors
            NodeConnector inPortNc = (NodeConnector) inPort.getValue();
            for (HostNodeConnector h : hosts) {
                NodeConnector hostNc = h.getnodeConnector();
                if (hostNc.equals(inPortNc)) {
                    srcHost = h;
                    this.sourceHostCache.put(inPort, h); // Add to cache
                    break;
                }
            }
        }

        return srcHost;
    }

    public long getByteCountBetweenHosts(Host src, Host dst) {

        long byteCount = 0;
        if (this.hostsToStats.get(src) != null &&
            this.hostsToStats.get(src).get(dst) != null) {
            byteCount = this.hostsToStats.get(src).get(dst).getByteCount();
        }

        return byteCount;
    }

    public double getBitRateBetweenHosts(Host src, Host dst) {
        double bitRate = 0;
        if (this.hostsToStats.get(src) != null &&
            this.hostsToStats.get(src).get(dst) != null) {
            bitRate = this.hostsToStats.get(src).get(dst).getBitRate();
        }

        return bitRate;
    }

    public double getBitRateOnAffinityLink(AffinityLink al) {
        double maxDuration = 0;
        int totalBytes = 0;
        List<Entry<Host, Host>> flows = this.affinityManager.getAllFlowsByHost(al);
        for (Entry<Host, Host> flow : flows) {
            Host h1 = flow.getKey();
            Host h2 = flow.getValue();
            if (this.hostsToStats.get(h1) != null &&
                this.hostsToStats.get(h1).get(h2) != null) {
                totalBytes += getByteCountBetweenHosts(h1, h2);
                double duration = this.hostsToStats.get(h1).get(h2).getDuration();
                if (duration > maxDuration) {
                    maxDuration = duration;
                }
            }
        }
        if (maxDuration == 0.0) {
            return 0.0;
        } else {
            return (totalBytes * 8.0) / maxDuration;
        }
    }

    public long getByteCountOnAffinityLink(AffinityLink al) {
        long b = 0;
        List<Entry<Host, Host>> flows = this.affinityManager.getAllFlowsByHost(al);
        for (Entry<Host, Host> flow : flows) {
            Host h1 = flow.getKey();
            Host h2 = flow.getValue();
            b += getByteCountBetweenHosts(h1, h2);
        }

        return b;
    }

    @Override
    public void nodeFlowStatisticsUpdated(Node node, List<FlowOnNode> flowStatsList) {

        Set<HostNodeConnector> allHosts = this.hostTracker.getAllHosts();
        for (FlowOnNode f : flowStatsList) {
            Host srcHost = getSourceHostFromFlow(f.getFlow(), allHosts);
            Host dstHost = getDestinationHostFromFlow(f.getFlow(), allHosts);

            if (srcHost == null || dstHost == null) {
                log.debug("Error: source or destination is null in nodeFlowStatisticsUpdated");
                continue;
            }

            if (this.hostsToStats.get(srcHost) == null) {
                this.hostsToStats.put(srcHost, new HashMap<Host, HostStats>());
            }
            if (this.hostsToStats.get(srcHost).get(dstHost) == null) {
                this.hostsToStats.get(srcHost).put(dstHost, new HostStats());
            }
            this.hostsToStats.get(srcHost).get(dstHost).setStatsFromFlow(f);
        }
    }

    @Override
    public void nodeConnectorStatisticsUpdated(Node node, List<NodeConnectorStatistics> ncStatsList) {
        // Not interested in this update
    }

    @Override
    public void nodeTableStatisticsUpdated(Node node, List<NodeTableStatistics> tableStatsList) {
        // Not interested in this update
    }

    @Override
    public void descriptionStatisticsUpdated(Node node, NodeDescription nodeDescription) {
        // Not interested in this update
    }
}
