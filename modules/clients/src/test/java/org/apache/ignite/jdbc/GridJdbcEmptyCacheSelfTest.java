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

package org.apache.ignite.jdbc;

import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.junits.common.*;

import java.sql.*;

import static org.apache.ignite.cache.GridCacheMode.*;
import static org.apache.ignite.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Tests for empty cache.
 */
public class GridJdbcEmptyCacheSelfTest extends GridCommonAbstractTest {
    /** IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Cache name. */
    private static final String CACHE_NAME = "cache";

    /** URL. */
    private static final String URL = "jdbc:gridgain://127.0.0.1/" + CACHE_NAME;

    /** Statement. */
    private Statement stmt;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        CacheConfiguration cache = defaultCacheConfiguration();

        cache.setName(CACHE_NAME);
        cache.setCacheMode(PARTITIONED);
        cache.setBackups(1);
        cache.setWriteSynchronizationMode(FULL_SYNC);

        cfg.setCacheConfiguration(cache);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(disco);

        cfg.setRestEnabled(true);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGrid();

        Class.forName("org.gridgain.jdbc.GridJdbcDriver");
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        stmt = DriverManager.getConnection(URL).createStatement();

        assert stmt != null;
        assert !stmt.isClosed();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        if (stmt != null) {
            stmt.getConnection().close();
            stmt.close();

            assert stmt.isClosed();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testSelectNumber() throws Exception {
        ResultSet rs = stmt.executeQuery("select 1");

        int cnt = 0;

        while (rs.next()) {
            assert rs.getInt(1) == 1;
            assert "1".equals(rs.getString(1));

            cnt++;
        }

        assert cnt == 1;
    }

    /**
     * @throws Exception If failed.
     */
    public void _testSelectString() throws Exception {
        ResultSet rs = stmt.executeQuery("select 'str'");

        int cnt = 0;

        while (rs.next()) {
            assertEquals("str", rs.getString(1));

            cnt++;
        }

        assert cnt == 1;
    }
}
