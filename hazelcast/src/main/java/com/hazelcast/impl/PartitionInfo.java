/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hazelcast.impl;

import com.hazelcast.nio.Address;

import javax.swing.event.ChangeListener;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class PartitionInfo {
    public static final int MAX_REPLICA_COUNT = 7;

    private final int partitionId;
    private final AtomicReferenceArray<Address> addresses = new AtomicReferenceArray<Address>(MAX_REPLICA_COUNT);
    private final ChangeListener changeListener;

    public PartitionInfo(int partitionId, ChangeListener changeListener) {
        this.partitionId = partitionId;
        this.changeListener = changeListener;
    }

    public PartitionInfo(int partitionId) {
        this(partitionId, null);
    }

    public int getPartitionId() {
        return partitionId;
    }

    public Address getOwner() {
        return addresses.get(0);
    }

    public void setOwner(Address ownerAddress) {
        setReplicaAddress(0, ownerAddress);
    }

    public void setReplicaAddress(int index, Address address) {
        if (changeListener != null) {
            Address currentAddress = addresses.get(index);
            boolean changed;
            if (currentAddress == null) {
                changed = (address != null);
            } else {
                changed = !currentAddress.equals(address);
            }
            if (changed) {
                changeListener.stateChanged(null);
            }
        }
        addresses.set(index, address);
    }

    public Address getReplicaAddress(int index) {
        return (addresses != null && addresses.length() > index)
                ? addresses.get(index) : null;
    }

    public PartitionInfo copy() {
        PartitionInfo p = new PartitionInfo(partitionId, changeListener);
        p.setPartitionInfo(this);
        return p;
    }

    public void setPartitionInfo(PartitionInfo partitionInfo) {
        for (int i = 0; i < MAX_REPLICA_COUNT; i++) {
            setReplicaAddress(i, partitionInfo.getReplicaAddress(i));
        }
    }

    public boolean isBackup(Address address, int backupCount) {
        int backup = Math.min(backupCount + 1, MAX_REPLICA_COUNT);
        for (int i = 1; i < backup; i++) {
            if (address.equals(getReplicaAddress(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean isOwnerOrBackup(Address address, int backupCount) {
        int backup = Math.min(backupCount + 1, MAX_REPLICA_COUNT);
        for (int i = 0; i < backup; i++) {
            if (address.equals(getReplicaAddress(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Partition [" + partitionId + "]{\n");
        for (int i = 0; i < MAX_REPLICA_COUNT; i++) {
            Address address = addresses.get(i);
            if (address != null) {
                sb.append(i + ":" + address);
                sb.append("\n");
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    public void onDeadAddress(Address deadAddress) {
        for (int i = 0; i < MAX_REPLICA_COUNT; i++) {
            if (deadAddress.equals(addresses.get(i))) {
                setReplicaAddress(i, null); // to guarantee that last guy is set to null
                for (int a = i; a + 1 < MAX_REPLICA_COUNT; a++) {
                    setReplicaAddress(a, addresses.get(a + 1));
                }
                return;
            }
        }
    }

    public int getReplicaIndexOf(Address address) {
        for (int i = 0; i < MAX_REPLICA_COUNT; i++) {
            if (address.equals(addresses.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartitionInfo that = (PartitionInfo) o;
        if (partitionId != that.partitionId) return false;
        if (addresses != null ? !addresses.equals(that.addresses) : that.addresses != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = partitionId;
        for (int i = 0; i < MAX_REPLICA_COUNT; i++) {
            Address address = addresses.get(i);
            result = 31 * result + (address != null ? address.hashCode() : 0);
        }
        return result;
    }
}
