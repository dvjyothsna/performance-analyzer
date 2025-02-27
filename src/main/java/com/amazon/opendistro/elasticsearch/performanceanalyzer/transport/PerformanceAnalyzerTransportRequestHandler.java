/*
 * Copyright <2019> Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistro.elasticsearch.performanceanalyzer.transport;

import com.amazon.opendistro.elasticsearch.performanceanalyzer.http_action.config.PerformanceAnalyzerConfigAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.transport.TransportRequestHandler;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportChannel;
import org.elasticsearch.action.support.replication.TransportReplicationAction.ConcreteShardRequest;
import org.elasticsearch.action.bulk.BulkShardRequest;

public class PerformanceAnalyzerTransportRequestHandler<T extends TransportRequest> implements TransportRequestHandler<T> {
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerTransportRequestHandler.class);
    private TransportRequestHandler<T> actualHandler;

    PerformanceAnalyzerTransportRequestHandler(TransportRequestHandler<T> actualHandler) {
        this.actualHandler = actualHandler;
    }

    PerformanceAnalyzerTransportRequestHandler<T> set(TransportRequestHandler<T> actualHandler) {
        this.actualHandler = actualHandler;
        return this;
    }

    @Override
    public void messageReceived(T request, TransportChannel channel, Task task) throws Exception {
        actualHandler.messageReceived(request, getChannel(request, channel, task) , task);
    }

    private TransportChannel getChannel(T request, TransportChannel channel, Task task) {
        if (PerformanceAnalyzerConfigAction.getInstance() == null || !PerformanceAnalyzerConfigAction.getInstance().isFeatureEnabled()) {
            return channel;
        }

        if (request instanceof ConcreteShardRequest) {
            return getShardBulkChannel(request, channel, task);
        } else {
            return channel;
        }
    }

    private TransportChannel getShardBulkChannel(T request, TransportChannel channel, Task task) {
        String className = request.getClass().getName();
        boolean bPrimary = false;

        if (className.equals("org.elasticsearch.action.support.replication.TransportReplicationAction$ConcreteShardRequest")) {
            bPrimary = true;
        } else if (className.equals("org.elasticsearch.action.support.replication.TransportReplicationAction$ConcreteReplicaRequest")) {
            bPrimary = false;
        } else {
            return channel;
        }

        TransportRequest transportRequest = ((ConcreteShardRequest<?>) request).getRequest();

        if (!(transportRequest instanceof BulkShardRequest)) {
            return channel;
        }

        BulkShardRequest bsr = (BulkShardRequest) transportRequest;
        PerformanceAnalyzerTransportChannel performanceanalyzerChannel = new PerformanceAnalyzerTransportChannel();
        performanceanalyzerChannel.set(channel, System.currentTimeMillis(), bsr.index(), bsr.shardId().id(), bsr.items().length, bPrimary);

        return performanceanalyzerChannel;
    }
}
