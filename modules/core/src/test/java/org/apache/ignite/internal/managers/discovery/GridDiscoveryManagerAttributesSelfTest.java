/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.managers.discovery;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.junits.common.*;

import static org.apache.ignite.configuration.IgniteDeploymentMode.*;

/**
 * Tests for node attributes consistency checks.
 */
public class GridDiscoveryManagerAttributesSelfTest extends GridCommonAbstractTest {
    /** */
    private static final String PREFER_IPV4 = "java.net.preferIPv4Stack";

    /** */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** */
    private static IgniteDeploymentMode mode = SHARED;

    /** */
    private static boolean p2pEnabled;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpDiscoverySpi disc = new TcpDiscoverySpi();

        disc.setIpFinder(IP_FINDER);

        cfg.setIncludeProperties(PREFER_IPV4);
        cfg.setDeploymentMode(mode);
        cfg.setPeerClassLoadingEnabled(p2pEnabled);
        cfg.setDiscoverySpi(disc);

        return cfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testPreferIpV4StackTrue() throws Exception {
        testPreferIpV4Stack(true);
    }

    /**
     * @throws Exception If failed.
     */
    public void testPreferIpV4StackFalse() throws Exception {
        testPreferIpV4Stack(false);
    }

    /**
     * This test should output warning to log on 3rd grid start:
     * <pre>
     * [10:47:05,534][WARN ][Thread-68][GridDiscoveryManager] Local node's value of 'java.net.preferIPv4Stack'
     * system property differs from remote node's (all nodes in topology should have identical value)
     * [locPreferIpV4=false, rmtPreferIpV4=true, locId8=b1cad004, rmtId8=16193477]
     * </pre>
     *
     * @throws Exception If failed.
     */
    public void testPreferIpV4StackDifferentValues() throws Exception {
        try {
            System.setProperty(PREFER_IPV4, "true");

            for (int i = 0; i < 2; i++) {
                Ignite g = startGrid(i);

                assert "true".equals(g.cluster().localNode().attribute(PREFER_IPV4));
            }

            System.setProperty(PREFER_IPV4, "false");

            startGrid(2);
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDifferentDeploymentModes() throws Exception {
        try {
            startGrid(1);

            mode = CONTINUOUS;

            try {
                startGrid(2);

                fail();
            }
            catch (IgniteCheckedException e) {
                assertTrue(e.getCause().getMessage().startsWith("Remote node has deployment mode different from"));
            }
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDifferentPeerClassLoadingEnabledFlag() throws Exception {
        try {
            startGrid(1);

            p2pEnabled = true;

            try {
                startGrid(2);

                fail();
            }
            catch (IgniteCheckedException e) {
                assertTrue(e.getCause().getMessage().startsWith("Remote node has peer class loading enabled flag " +
                    "different from"));
            }
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param preferIpV4 {@code java.net.preferIPv4Stack} system property value.
     * @throws Exception If failed.
     */
    private void testPreferIpV4Stack(boolean preferIpV4) throws Exception {
        try {
            String val = String.valueOf(preferIpV4);

            System.setProperty(PREFER_IPV4, val);

            for (int i = 0; i < 2; i++) {
                Ignite g = startGrid(i);

                assert val.equals(g.cluster().localNode().attribute(PREFER_IPV4));
            }
        }
        finally {
            stopAllGrids();
        }
    }
}
