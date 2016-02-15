package com.hazelcast.map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.Node;
import com.hazelcast.map.MapPartitionLostListenerStressTest.EventCollectingMapPartitionLostListener;
import com.hazelcast.map.impl.MapPartitionLostEventFilter;
import com.hazelcast.map.impl.MapService;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.nio.Address;
import com.hazelcast.partition.AbstractPartitionLostListenerTest;
import com.hazelcast.internal.partition.InternalPartition;
import com.hazelcast.partition.IPartitionLostEvent;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class MapPartitionLostListenerTest extends AbstractPartitionLostListenerTest {

    @Override
    public int getNodeCount() {
        return 2;
    }

    @Test
    public void test_partitionLostListenerInvoked() {
        List<HazelcastInstance> instances = getCreatedInstancesShuffledAfterWarmedUp(1);
        HazelcastInstance instance = instances.get(0);

        final EventCollectingMapPartitionLostListener listener = new EventCollectingMapPartitionLostListener(0);
        instance.getMap(getIthMapName(0)).addPartitionLostListener(listener);

        final IPartitionLostEvent internalEvent = new IPartitionLostEvent(1, 0, null);

        MapService mapService = getNode(instance).getNodeEngine().getService(MapService.SERVICE_NAME);
        mapService.onPartitionLost(internalEvent);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                List<MapPartitionLostEvent> events = listener.getEvents();
                assertEquals(1, events.size());
                MapPartitionLostEvent event = events.get(0);
                assertEquals(internalEvent.getPartitionId(), event.getPartitionId());
                assertNotNull(event.toString());
            }
        });
    }

    @Test
    public void test_partitionLostListenerInvoked_whenEntryListenerIsAlsoRegistered() {
        List<HazelcastInstance> instances = getCreatedInstancesShuffledAfterWarmedUp(1);
        HazelcastInstance instance = instances.get(0);

        final EventCollectingMapPartitionLostListener listener = new EventCollectingMapPartitionLostListener(0);
        instance.getMap(getIthMapName(0)).addPartitionLostListener(listener);
        instance.getMap(getIthMapName(0)).addEntryListener(mock(EntryAddedListener.class), true);

        final IPartitionLostEvent internalEvent = new IPartitionLostEvent(1, 0, null);

        MapService mapService = getNode(instance).getNodeEngine().getService(MapService.SERVICE_NAME);
        mapService.onPartitionLost(internalEvent);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                List<MapPartitionLostEvent> events = listener.getEvents();
                assertEquals(1, events.size());
                MapPartitionLostEvent event = events.get(0);
                assertEquals(internalEvent.getPartitionId(), event.getPartitionId());
                assertNotNull(event.toString());
            }
        });
    }

    @Test
    public void test_partitionLostListenerInvoked_whenNodeCrashed() {
        List<HazelcastInstance> instances = getCreatedInstancesShuffledAfterWarmedUp();

        HazelcastInstance survivingInstance = instances.get(0);
        HazelcastInstance terminatingInstance = instances.get(1);

        final EventCollectingMapPartitionLostListener listener = new EventCollectingMapPartitionLostListener(0);
        survivingInstance.getMap(getIthMapName(0)).addPartitionLostListener(listener);

        final Set<Integer> survivingPartitionIds = new HashSet<Integer>();
        Node survivingNode = getNode(survivingInstance);
        Address survivingAddress = survivingNode.getThisAddress();

        for (InternalPartition partition : survivingNode.getPartitionService().getPartitions()) {
            if (survivingAddress.equals(partition.getReplicaAddress(0))) {
                survivingPartitionIds.add(partition.getPartitionId());
            }
        }

        terminatingInstance.getLifecycleService().terminate();
        waitAllForSafeState(survivingInstance);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                List<MapPartitionLostEvent> events = listener.getEvents();
                assertFalse(events.isEmpty());

                for (MapPartitionLostEvent event : events) {
                    assertFalse(survivingPartitionIds.contains(event.getPartitionId()));
                }
            }
        });
    }

    @Test
    public void testMapPartitionLostEventFilter() {
        MapPartitionLostEventFilter filter = new MapPartitionLostEventFilter();
        assertEquals(new MapPartitionLostEventFilter(), filter);
        assertFalse(filter.eval(null));
    }
}
