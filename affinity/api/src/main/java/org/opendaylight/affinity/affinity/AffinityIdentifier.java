/*
 * Copyright (c) 2013 Plexxi, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.affinity.affinity;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/* Affinity identifier */
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class AffinityIdentifier<T> implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    @XmlAttribute
    private String name;
    @XmlAttribute
    private T value;

    public T get() {
        return value;
    }
    public void set(T t) {
	value = t;
    }
    public void setName(String name) {
	this.name = name;
    }
    public String getName(String name) {
	return (this.name);
    }
    public String toString() {
	return "AffinityIdentifier [name= " + this.name + " value= " + value.toString() + "]";
    }
}

