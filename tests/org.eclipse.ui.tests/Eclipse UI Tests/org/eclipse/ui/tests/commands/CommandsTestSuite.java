/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.tests.commands;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Tests for all areas of command support for the platform.
 */
public final class CommandsTestSuite extends TestSuite {

    /**
     * Returns the suite. This is required to use the JUnit Launcher.
     */
    public static final Test suite() {
        return new CommandsTestSuite();
    }

    /**
     * Construct the test suite.
     */
    public CommandsTestSuite() {
        addTest(new TestSuite(Bug66182Test.class));
    }
}