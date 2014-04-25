/*******************************************************************************
 * Copyright (c) 2004, 2009 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - initial API and implementation
 *******************************************************************************/

package org.eclipse.linuxtools.internal.rpm.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * I18n messages.
 *
 */
public class Messages {

    private static final String BUNDLE_NAME = "org.eclipse.linuxtools.internal.rpm.ui.rpm_strings"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private Messages() {
    }

    /**
     * Returns the string for the given key.
     * @param key The key we are looking for.
     * @return The found value.
     */
    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
