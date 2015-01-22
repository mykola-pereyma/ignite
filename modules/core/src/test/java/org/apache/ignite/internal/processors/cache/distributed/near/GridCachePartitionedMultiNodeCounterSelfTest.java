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

package org.apache.ignite.internal.processors.cache.distributed.near;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.affinity.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.apache.ignite.transactions.*;
import org.apache.ignite.internal.processors.cache.distributed.dht.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.apache.ignite.cache.GridCacheAtomicityMode.*;
import static org.apache.ignite.cache.GridCacheMode.*;
import static org.apache.ignite.cache.GridCacheDistributionMode.*;
import static org.apache.ignite.cache.GridCachePreloadMode.*;
import static org.apache.ignite.transactions.IgniteTxConcurrency.*;
import static org.apache.ignite.transactions.IgniteTxIsolation.*;

/**
 * Multiple put test.
 */
@SuppressWarnings({"UnusedAssignment", "TooBroadScope", "PointlessBooleanExpression", "PointlessArithmeticExpression"})
public class GridCachePartitionedMultiNodeCounterSelfTest extends GridCommonAbstractTest {
    /** Debug flag. */
    private static final boolean DEBUG = false;

    /** */
    private static final int DFLT_BACKUPS = 1;

    /** */
    private static final int RETRIES = 100;

    /** Log frequency. */
    private static final int LOG_FREQ = RETRIES < 100 || DEBUG ? 1 : RETRIES / 5;

    /** */
    private static final String CNTR_KEY = "CNTR_KEY";

    /** */
    private TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    private static CountDownLatch startLatchMultiNode;

    /** */
    private static AtomicInteger globalCntrMultiNode;

    /** */
    private static AtomicBoolean lockedMultiNode = new AtomicBoolean(false);

    /** */
    private int backups = DFLT_BACKUPS;

    /** Constructs test. */
    public GridCachePartitionedMultiNodeCounterSelfTest() {
        super(/* don't start grid */ false);
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpDiscoverySpi spi = new TcpDiscoverySpi();

        spi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(spi);

        // Default cache configuration.
        CacheConfiguration cacheCfg = defaultCacheConfiguration();

        cacheCfg.setPreloadMode(NONE);
        cacheCfg.setCacheMode(PARTITIONED);
        cacheCfg.setDistributionMode(NEAR_PARTITIONED);
        cacheCfg.setAtomicityMode(TRANSACTIONAL);
        cacheCfg.setWriteSynchronizationMode(GridCacheWriteSynchronizationMode.FULL_SYNC);
        cacheCfg.setBackups(backups);

        cfg.setCacheConfiguration(cacheCfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        backups = DFLT_BACKUPS;
    }

    /**
     * @param cache Cache.
     * @return Affinity.
     */
    private static GridCacheAffinity<String> affinity(GridCache<String, Integer> cache) {
        return cache.affinity();
    }

    /**
     * @param g Grid.
     * @return Near cache.
     */
    private static GridNearCacheAdapter<String, Integer> near(Ignite g) {
        return (GridNearCacheAdapter<String, Integer>)((GridKernal)g).<String, Integer>internalCache();
    }

    /**
     * @param g Grid.
     * @return DHT cache.
     */
    private static GridDhtCacheAdapter<String, Integer> dht(Ignite g) {
        return near(g).dht();
    }

    /**
     * @param msg Error message.
     * @param g Grid.
     * @param primary Primary flag.
     * @param v1 V1.
     * @param v2 V2.
     * @return String for assertion.
     */
    private static String invalid(String msg, Ignite g, boolean primary, int v1, int v2) {
        return msg + " [grid=" + g.name() + ", primary=" + primary + ", v1=" + v1 + ", v2=" + v2 +
            (!primary ?
                ", nearEntry=" + near(g).peekEx(CNTR_KEY) :
                ", dhtEntry=" + dht(g).peekEx(CNTR_KEY) + ", dhtNear=" + near(g).peekEx(CNTR_KEY)) +
            ']';
    }

    /**
     * @param max Maximum index of grid nodes.
     * @param exclude Exlude array.
     * @return List of grids.
     */
    public List<Ignite> grids(int max, Ignite... exclude) {
        List<Ignite> ignites = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            Ignite g = grid(i);

            if (!U.containsObjectArray(exclude, g))
                ignites.add(g);
        }

        return ignites;
    }

    /** @throws Exception If failed. */
    public void testMultiNearAndPrimary() throws Exception {
//        resetLog4j(Level.INFO, true, GridCacheTxManager.class.getName());

        backups = 1;

        int gridCnt = 4;
        int priThreads = 2;
        int nearThreads = 2;

        startGrids(gridCnt);

        try {
            checkNearAndPrimary(gridCnt, priThreads, nearThreads);
        }
        finally {
            stopAllGrids();
        }
    }

    /** @throws Exception If failed. */
    public void testOneNearAndPrimary() throws Exception {
//        resetLog4j(Level.INFO, true, GridCacheTxManager.class.getName());

        backups = 1;

        int gridCnt = 2;
        int priThreads = 5;
        int nearThreads = 5;

        startGrids(gridCnt);

        try {
            checkNearAndPrimary(gridCnt, priThreads, nearThreads);
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param nodeIds Node IDs.
     * @return Grid names.
     */
    private Collection<String> gridNames(Collection<UUID> nodeIds) {
        Collection<String> names = new ArrayList<>(nodeIds.size());

        for (UUID nodeId : nodeIds)
            names.add(G.ignite(nodeId).name());

        return names;
    }

    /**
     * @param gridCnt Grid count.
     * @param priThreads Primary threads.
     * @param nearThreads Near threads.
     * @throws Exception If failed.
     */
    private void checkNearAndPrimary(int gridCnt, int priThreads, int nearThreads) throws Exception {
        assert gridCnt > 0;
        assert priThreads >= 0;
        assert nearThreads >= 0;

        X.println("*** Retries: " + RETRIES);
        X.println("*** Log frequency: " + LOG_FREQ);

        GridCacheAffinity<String> aff = affinity(grid(0).<String, Integer>cache(null));

        Collection<ClusterNode> affNodes = aff.mapKeyToPrimaryAndBackups(CNTR_KEY);

        X.println("*** Affinity nodes [key=" + CNTR_KEY + ", nodes=" + U.nodeIds(affNodes) + ", gridNames=" +
            gridNames(U.nodeIds(affNodes)) + ']');

        assertEquals(1 + backups, affNodes.size());

        ClusterNode first = F.first(affNodes);

        assert first != null;

        final Ignite pri = G.ignite(first.id());

        List<Ignite> nears = grids(gridCnt, pri);

        final UUID priId = pri.cluster().localNode().id();

        // Initialize.
        pri.cache(null).put(CNTR_KEY, 0);
//        nears.get(0).cache(null).put(CNTR_KEY, 0);

        assertNull(near(pri).peekEx(CNTR_KEY));

        final GridCacheEntryEx<String, Integer> dhtEntry = dht(pri).peekEx(CNTR_KEY);

        assertNotNull(dhtEntry);

        assertEquals(Integer.valueOf(0), dhtEntry.rawGet());

        final AtomicInteger globalCntr = new AtomicInteger(0);

        Collection<Thread> threads = new LinkedList<>();

        final CountDownLatch startLatch = new CountDownLatch(gridCnt);

        final AtomicBoolean locked = new AtomicBoolean(false);

        if (priThreads > 0) {
            final AtomicInteger logCntr = new AtomicInteger();

            for (int i = 0; i < priThreads; i++) {
                info("*** Starting primary thread: " + i);

                threads.add(new Thread(new Runnable() {
                    @Override public void run() {
                        info("*** Started primary thread ***");

                        try {
                            startLatch.countDown();
                            startLatch.await();

                            for (int i = 0; i < RETRIES; i++) {
                                if (DEBUG)
                                    info("***");

                                int cntr = logCntr.getAndIncrement();

                                if (DEBUG || cntr % LOG_FREQ == 0)
                                    info("*** Primary Iteration #" + i + ": " + cntr + " ***");

                                if (DEBUG)
                                    info("***");

                                GridCache<String, Integer> c = pri.cache(null);

                                Integer oldCntr = c.peek(CNTR_KEY);

                                GridCacheEntryEx<String, Integer> dhtNear = near(pri).peekEx(CNTR_KEY);

                                try (IgniteTx tx = c.txStart(PESSIMISTIC, REPEATABLE_READ)) {
                                    if (DEBUG)
                                        info("Started tx [grid=" + pri.name() + ", primary=true, xid=" + tx.xid() +
                                            ", oldCntr=" + oldCntr + ", node=" + priId + ", dhtEntry=" + dhtEntry +
                                            ", dhtNear=" + dhtNear + ']');

                                    // Initial lock.
                                    int curCntr = c.get(CNTR_KEY);

                                    assertTrue("Lock violation: " + tx, locked.compareAndSet(false, true));

                                    if (dhtNear == null)
                                        dhtNear = near(pri).peekEx(CNTR_KEY);

                                    if (DEBUG)
                                        info("Read counter [grid=" + pri.name() + ", primary=true, curCntr=" + curCntr +
                                            ", oldCntr=" + oldCntr + ", node=" + priId + ", dhtEntry=" + dhtEntry +
                                            ", dhtNear=" + dhtNear + ']');

                                    int global = globalCntr.get();

                                    assert curCntr >= global : invalid("Counter mismatch", pri, true, curCntr, global);

                                    int newCntr = curCntr + 1;

                                    if (DEBUG)
                                        info("Setting global counter [old=" + global + ", new=" + newCntr + ']');

                                    assert globalCntr.compareAndSet(global, newCntr) : invalid("Invalid global counter",
                                        pri, true, newCntr, global);

                                    int prev = c.put(CNTR_KEY, newCntr);

                                    if (DEBUG)
                                        info("Put new value [grid=" + pri.name() + ", primary=true, prev=" + prev +
                                            ", newCntr=" + newCntr + ']');

                                    assert curCntr == prev : invalid("Counter mismatch", pri, true, curCntr, prev);

                                    assertTrue("Lock violation: " + tx, locked.compareAndSet(true, false));

                                    tx.commit();

                                    if (DEBUG)
                                        info("Committed tx: " + tx);
                                }
                            }
                        }
                        catch (Throwable e) {
                            error(e.getMessage(), e);

                            fail(e.getMessage());
                        }
                    }
                }, "primary-t#" + i));
            }
        }

        if (nearThreads > 0) {
            int tid = 0;

            final AtomicInteger logCntr = new AtomicInteger();

            for (final Ignite near : nears) {
                for (int i = 0; i < nearThreads; i++) {
                    info("*** Starting near thread: " + i);

                    threads.add(new Thread(new Runnable() {
                        @Override public void run() {
                            info("*** Started near thread ***");

                            UUID nearId = near.cluster().localNode().id();

                            GridCacheEntryEx<String, Integer> nearEntry = near(near).peekEx(CNTR_KEY);

                            try {
                                startLatch.countDown();
                                startLatch.await();

                                for (int i = 0; i < RETRIES; i++) {
                                    if (DEBUG)
                                        info("***");

                                    int cntr = logCntr.getAndIncrement();

                                    if (DEBUG || cntr % LOG_FREQ == 0)
                                        info("*** Near Iteration #" + i + ": " + cntr + " ***");

                                    if (DEBUG)
                                        info("***");

                                    GridCache<String, Integer> c = near.cache(null);

                                    Integer oldCntr = c.peek(CNTR_KEY);

                                    try (IgniteTx tx = c.txStart(PESSIMISTIC, REPEATABLE_READ)) {
                                        if (DEBUG)
                                            info("Started tx [grid=" + near.name() + ", primary=false, xid=" +
                                                tx.xid() + ", oldCntr=" + oldCntr + ", node=" + nearId +
                                                ", nearEntry=" + nearEntry + ']');

                                        // Initial lock.
                                        Integer curCntr = c.get(CNTR_KEY);

                                        nearEntry = near(near).peekEx(CNTR_KEY);

                                        assert curCntr != null : "Counter is null [nearEntry=" + nearEntry +
                                            ", dhtEntry=" + dht(near).peekEx(CNTR_KEY) + ']';

                                        if (DEBUG)
                                            info("Read counter [grid=" + near.name() + ", primary=false, curCntr=" +
                                                curCntr + ", oldCntr=" + oldCntr + ", node=" + nearId +
                                                ", nearEntry=" + nearEntry + ']');

                                        assert locked.compareAndSet(false, true) : "Lock violation: " + tx;

                                        int global = globalCntr.get();

                                        assert curCntr >= global : invalid("Counter mismatch", near, false, curCntr,
                                            global);

                                        int newCntr = curCntr + 1;

                                        if (DEBUG)
                                            info("Setting global counter [old=" + global + ", new=" + newCntr + ']');

                                        assert globalCntr.compareAndSet(global, newCntr) :
                                            invalid("Invalid global counter", near, false, newCntr, global);

                                        int prev = c.put(CNTR_KEY, newCntr);

                                        if (DEBUG)
                                            info("Put new value [grid=" + near.name() + ", primary=false, prev=" +
                                                prev + ", newCntr=" + newCntr + ']');

                                        assert curCntr == prev : invalid("Counter mismatch", near, false, curCntr,
                                            prev);

                                        assertTrue("Lock violation: " + tx, locked.compareAndSet(true, false));

                                        tx.commit();

                                        if (DEBUG)
                                            info("Committed tx: " + tx);
                                    }
                                }
                            }
                            catch (Throwable t) {
                                error(t.getMessage(), t);

                                fail(t.getMessage());
                            }
                        }
                    }, "near-#" + tid + "-t#" + i));
                }

                tid++;
            }
        }

        for (Thread t : threads)
            t.start();

        for (Thread t : threads)
            t.join();

        X.println("*** ");

        Map<String, Integer> cntrs = new HashMap<>();

        for (int i = 0; i < gridCnt; i++) {
            Ignite g = grid(i);

            dht(g).context().tm().printMemoryStats();
            near(g).context().tm().printMemoryStats();

            GridCache<String, Integer> cache = grid(i).cache(null);

            int cntr = nearThreads > 0 && nears.contains(g) ? cache.get(CNTR_KEY) : cache.peek(CNTR_KEY);

            X.println("*** Cache counter [grid=" + g.name() + ", cntr=" + cntr + ']');

            cntrs.put(g.name(), cntr);
        }

        int updateCnt = priThreads + nears.size() * nearThreads;

        int exp = RETRIES * updateCnt;

        for (Map.Entry<String, Integer> e : cntrs.entrySet())
            assertEquals("Counter check failed on grid [grid=" + e.getKey() +
                ", dhtEntry=" + dht(G.ignite(e.getKey())).peekEx(CNTR_KEY) +
                ", nearEntry=" + near(G.ignite(e.getKey())).peekExx(CNTR_KEY) + ']',
                exp, e.getValue().intValue());

        X.println("*** ");
    }

    /** @throws Exception If failed. */
    public void testMultiNearAndPrimaryMultiNode() throws Exception {
        int gridCnt = 4;

        startGrids(gridCnt);

        try {
            checkNearAndPrimaryMultiNode(gridCnt);
        }
        finally {
            stopAllGrids();
        }
    }

    /** @throws Exception If failed. */
    public void testOneNearAndPrimaryMultiNode() throws Exception {
        int gridCnt = 2;

        startGrids(gridCnt);

        try {
            checkNearAndPrimaryMultiNode(gridCnt);
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param gridCnt Grid count.
     * @throws Exception If failed.
     */
    private void checkNearAndPrimaryMultiNode(int gridCnt) throws Exception {
        GridCacheAffinity<String> aff = affinity(grid(0).<String, Integer>cache(null));

        Collection<ClusterNode> affNodes = aff.mapKeyToPrimaryAndBackups(CNTR_KEY);

        assertEquals(1 + backups, affNodes.size());

        Ignite pri = G.ignite(F.first(affNodes).id());

        // Initialize.
        pri.cache(null).put(CNTR_KEY, 0);

        assertNull(near(pri).peekEx(CNTR_KEY));

        GridCacheEntryEx<String, Integer> dhtEntry = dht(pri).peekEx(CNTR_KEY);

        assertNotNull(dhtEntry);

        assertEquals(Integer.valueOf(0), dhtEntry.rawGet());

        startLatchMultiNode = new CountDownLatch(gridCnt);

        globalCntrMultiNode = new AtomicInteger(0);

        lockedMultiNode.set(false);

        // Execute task on all grid nodes.
        pri.compute().broadcast(new IncrementItemJob(pri.name()));

        info("*** ");

        for (int i = 0; i < gridCnt; i++) {
            Ignite g = grid(i);

            GridCache<String, Integer> cache = grid(i).cache(null);

            int cntr = cache.peek(CNTR_KEY);

            info("*** Cache counter [grid=" + g.name() + ", cntr=" + cntr + ']');

            assertEquals(RETRIES * gridCnt, cntr);
        }

        info("*** ");
    }

    /** Job incrementing counter. */
    private static class IncrementItemJob implements IgniteCallable<Boolean> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** */
        @IgniteLoggerResource
        private IgniteLogger log;

        /** */
        private final String pid;

        /** @param pid Primary node id. */
        IncrementItemJob(String pid) {
            this.pid = pid;
        }

        /** {@inheritDoc} */
        @Override public Boolean call() throws IgniteCheckedException, InterruptedException {
            assertNotNull(ignite);

            startLatchMultiNode.countDown();
            startLatchMultiNode.await();

            if (pid.equals(ignite.name()))
                onPrimary();
            else
                onNear();

            return true;
        }

        /** Near node. */
        private void onNear() {
            Ignite near = ignite;

            UUID nearId = ignite.cluster().localNode().id();

            GridCacheEntryEx<String, Integer> nearEntry = near(near).peekEx(CNTR_KEY);

            try {
                for (int i = 0; i < RETRIES; i++) {
                    if (DEBUG)
                        log.info("***");

                    if (DEBUG || i % LOG_FREQ == 0)
                        log.info("*** Near Iteration #" + i + " ***");

                    if (DEBUG)
                        log.info("***");

                    GridCache<String, Integer> c = near.cache(null);

                    Integer oldCntr = c.peek(CNTR_KEY);

                    try (IgniteTx tx = c.txStart(PESSIMISTIC, REPEATABLE_READ)) {
                        if (DEBUG)
                            log.info("Started tx [grid=" + near.name() + ", primary=false, xid=" + tx.xid() +
                                ", oldCntr=" + oldCntr + ", node=" + nearId + ", nearEntry=" + nearEntry + ']');

                        // Initial lock.
                        int curCntr = c.get(CNTR_KEY);

                        assertTrue(lockedMultiNode.compareAndSet(false, true));

                        if (DEBUG)
                            log.info("Read counter [grid=" + near.name() + ", primary=false, curCntr=" + curCntr +
                                ", oldCntr=" + oldCntr + ", node=" + nearId + ", nearEntry=" + nearEntry + ']');

                        int global = globalCntrMultiNode.get();

                        assert curCntr >= global : invalid("Counter mismatch", near, false, curCntr, global);

                        int newCntr = curCntr + 1;

                        if (DEBUG)
                            log.info("Setting global counter [old=" + global + ", new=" + newCntr + ']');

                        assert globalCntrMultiNode.compareAndSet(global, newCntr) : invalid("Invalid global counter",
                            near, false, newCntr, global);

                        int prev = c.put(CNTR_KEY, newCntr);

                        if (DEBUG)
                            log.info("Put new value [grid=" + near.name() + ", primary=false, prev=" + prev +
                                ", newCntr=" + newCntr + ']');

                        assert curCntr == prev : invalid("Counter mismatch", near, false, curCntr, prev);

                        assertTrue(lockedMultiNode.compareAndSet(true, false));

                        tx.commit();

                        if (DEBUG)
                            log.info("Committed tx: " + tx);
                    }
                }
            }
            catch (Throwable t) {
                log.error(t.getMessage(), t);

                fail(t.getMessage());
            }
        }

        /** Primary node. */
        private void onPrimary() {
            try {
                Ignite pri = ignite;

                for (int i = 0; i < RETRIES; i++) {
                    if (DEBUG)
                        log.info("***");

                    if (DEBUG || i % LOG_FREQ == 0)
                        log.info("*** Primary Iteration #" + i + " ***");

                    if (DEBUG)
                        log.info("***");

                    GridCache<String, Integer> c = pri.cache(null);

                    Integer oldCntr = c.peek(CNTR_KEY);

                    GridCacheEntryEx<String, Integer> dhtNear = near(pri).peekEx(CNTR_KEY);

                    try (IgniteTx tx = c.txStart(PESSIMISTIC, REPEATABLE_READ)) {
                        if (DEBUG)
                            log.info("Started tx [grid=" + pri.name() + ", primary=true, xid=" + tx.xid() +
                                ", oldCntr=" + oldCntr + ", node=" + pri.name() + ", dhtEntry=" +
                                dht(pri).peekEx(CNTR_KEY) + ", dhtNear=" + dhtNear + ']');

                        // Initial lock.
                        int curCntr = c.get(CNTR_KEY);

                        assertTrue(lockedMultiNode.compareAndSet(false, true));

                        if (dhtNear == null)
                            dhtNear = near(pri).peekEx(CNTR_KEY);

                        if (DEBUG)
                            log.info("Read counter [grid=" + pri.name() + ", primary=true, curCntr=" + curCntr +
                                ", oldCntr=" + oldCntr + ", node=" + pri.name() + ", dhtEntry=" +
                                dht(pri).peekEx(CNTR_KEY) + ", dhtNear=" + dhtNear + ']');

                        int global = globalCntrMultiNode.get();

                        assert curCntr >= global : invalid("Counter mismatch", pri, true, curCntr, global);

                        int newCntr = curCntr + 1;

                        if (DEBUG)
                            log.info("Setting global counter [old=" + global + ", new=" + newCntr + ']');

                        assert globalCntrMultiNode.compareAndSet(global, newCntr) : invalid("Invalid global counter",
                            pri, true, newCntr, global);

                        int prev = c.put(CNTR_KEY, newCntr);

                        if (DEBUG) {
                            log.info("Put new value [grid=" + pri.name() + ", primary=true, prev=" + prev +
                                ", newCntr=" + newCntr + ']');
                        }

                        assert curCntr == prev : invalid("Counter mismatch", pri, true, curCntr, prev);

                        assertTrue(lockedMultiNode.compareAndSet(true, false));

                        tx.commit();

                        if (DEBUG)
                            log.info("Committed tx: " + tx);
                    }
                }
            }
            catch (Exception e) {
                log.error(e.getMessage(), e);

                fail(e.getMessage());
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(IncrementItemJob.class, this);
        }
    }
}
