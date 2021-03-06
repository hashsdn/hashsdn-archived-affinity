/*
 * Copyright (c) 2013 Plexxi, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.affinity.affinity.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.controller.sal.core.Bandwidth;
import org.opendaylight.controller.sal.core.Latency;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.core.State;
import org.opendaylight.controller.sal.core.UpdateType;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.affinity.affinity.AffinityIdentifier;
import org.opendaylight.affinity.affinity.AffinityGroup;
import org.opendaylight.affinity.affinity.AffinityLink;
import org.opendaylight.controller.sal.core.Host;

public class AffinityManagerImplTest {

    @Test
    public void testAffinityManagerAddRemoveConfig() {
        AffinityManagerImpl affinitymgr = new AffinityManagerImpl();
        affinitymgr.startUp();

	AffinityGroup ag1 = new AffinityGroup("group1");

	// Add a valid IP and confirm. 
	Status ret1 = ag1.add("10.0.0.10");
	Assert.assertTrue(ret1.isSuccess());

	Status ret2 = ag1.add("10.0.0.20");
	Assert.assertTrue(ret2.isSuccess());

	System.out.println(ag1.toString());

	// Add an invalid element. 
	Status ret3 = ag1.add("10");
	System.out.println(ret3);
	Assert.assertTrue(!ret3.isSuccess());

	// Second affinity group.
	AffinityGroup ag2 = new AffinityGroup("group2");
	ag2.add("20.0.0.10");
	ag2.add("20.0.0.20");

	// Add an affinity link from ag1 to ag2. 
	AffinityLink al1 = new AffinityLink();
	al1.setFromGroup(ag1);
	al1.setToGroup(ag2);
	al1.setName("link1");

	// Add a self loop for ag2.
	AffinityLink al2 = new AffinityLink("link2", ag2, ag2);
	al2.setFromGroup(ag2);
	al2.setToGroup(ag2);
	al2.setName("link2");

	System.out.println("Affinity group " + ag1.toString() + ", elements=" + ag1.size());
        Assert.assertTrue(ag1.size() == 2);
        
        Status result;
	result = affinitymgr.addAffinityGroup(ag1);
        Assert.assertTrue(result.isSuccess());

        result = affinitymgr.addAffinityGroup(ag2);
        Assert.assertTrue(result.isSuccess());
	
        result = affinitymgr.addAffinityLink(al1);
        Assert.assertTrue(result.isSuccess());

        result = affinitymgr.addAffinityLink(al2);
        Assert.assertTrue(result.isSuccess());
	
        AffinityGroup ag3 = new AffinityGroup("any");
        ag3.addInetMask("0.0.0.0/0");
        
        AffinityGroup ag4 = new AffinityGroup("servers");
        ag4.addInetMask("20.0.0.0/8");

	// Add an affinity link from ag1 to ag2. 
	AffinityLink al3 = new AffinityLink();
	al3.setFromGroup(ag3);
	al3.setToGroup(ag4);
	al3.setName("link3");
        al3.setWaypoint("20.0.0.11");
        
	result = affinitymgr.addAffinityGroup(ag3);
        Assert.assertTrue(result.isSuccess());
	result = affinitymgr.addAffinityGroup(ag4);
        Assert.assertTrue(result.isSuccess());
	result = affinitymgr.addAffinityLink(al3);
        Assert.assertTrue(result.isSuccess());

        // Print all pairs/flows from the affinity link al3. 
        System.out.println("affinity link " + al3.getName());
        List<Entry<AffinityIdentifier, AffinityIdentifier>> flowlist1;
        flowlist1 = affinitymgr.getAllFlowsByAffinityIdentifier(al3);
        for (Entry<AffinityIdentifier, AffinityIdentifier> flow : flowlist1) {
            System.out.println("flow with from=" + flow.getKey() + " to=" + flow.getValue());
        }

	/* Test the get methods. */

	/* Get all members as hosts */
        //	System.out.println("Affinity group (as Hosts) = " + ag1.getName());
        //	List<Host> hostlist = affinitymgr.getAllElementsByHost(ag1);
	
        //	for (Host h : hostlist) {
        //	    System.out.println("host = " + h.getNetworkAddressAsString());
        //}
	
	/* Get all members as affinity identifiers */
	System.out.println("Affinity group (as Affinity Identifiers) = " + ag1.getName());
	ArrayList<AffinityIdentifier> affylist = affinitymgr.getAllElementsByAffinityIdentifier(ag1);

        String idList = null;
	for (AffinityIdentifier i : affylist) {
            idList = idList + i.toString();
	    System.out.println(idList);
	}

	/* Get all id pairs for an affinity link */
	System.out.println("Affinity link = " + al1.getName());
	List<Entry<AffinityIdentifier, AffinityIdentifier>> flowlist = affinitymgr.getAllFlowsByAffinityIdentifier(al1);
	
	for (Entry<AffinityIdentifier, AffinityIdentifier> flow : flowlist) {
	    System.out.println("flow " + "from: " + flow.getKey().toString() + "to: " + flow.getValue().toString());
	}

	affinitymgr.saveConfiguration();
	/* Constraint checking? */
	result = (affinitymgr.removeAffinityGroup(ag1.getName()));
        Assert.assertTrue(result.isSuccess());
    }
}
