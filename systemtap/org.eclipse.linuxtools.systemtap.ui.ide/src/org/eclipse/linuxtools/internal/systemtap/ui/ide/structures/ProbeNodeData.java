/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - initial API and implementation
 *******************************************************************************/

package org.eclipse.linuxtools.internal.systemtap.ui.ide.structures;

import java.text.MessageFormat;

public class ProbeNodeData implements ISearchableNode {

    private final String name;

    @Override
    public boolean isRegexSearch() {
        return true;
    }

    /**
     * @return a regex to search a file with for the definition of this probe.
     */
    @Override
    public String getSearchToken() {
        return MessageFormat.format(ProbeParser.probeRegex, name);
    }

    @Override
    public String toString() {
        return getSearchToken();
    }

    /**
     * Create a new instance of probe node information.
     * @param line A line of text generated by running "stap -L" which
     * provides all information pertaining to a probe point, or at least
     * the probe's name.
     */
    public ProbeNodeData(String line) {
        int spaceIndex = line.indexOf(' ');
        name = (spaceIndex != -1 ? line.substring(0, spaceIndex) : line).trim();
    }

}
