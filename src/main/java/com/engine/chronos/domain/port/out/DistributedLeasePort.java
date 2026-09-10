package com.engine.chronos.domain.port.out;

import java.time.Duration;
import java.util.List;

public interface DistributedLeasePort {

    boolean acquirePartition(int partitionBucket, String nodeOwner, Duration duration);

    void renewPartition(int partitionBucket, String nodeOwner, Duration duration);

    void releasePartition(int partitionBucket, String nodeOwner);

    List<Integer> getOwnedPartitions(String nodeOwner);
}
