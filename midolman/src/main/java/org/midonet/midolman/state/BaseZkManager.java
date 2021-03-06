/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.midolman.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.serialization.Serializer;
import org.midonet.util.functors.CollectionFunctors;
import org.midonet.util.functors.Functor;

/**
 * Contains common functionality for interacting with ZkManager. When
 * making a new ZkManager, you will generally want to extend
 * AbstractZkManager, which provides basic operations like get()
 * and exists() for free.
 *
 * You should extend BaseZkManager only if your new ZkManager does not
 * manage a single-node primary resource, or if if it does not have a
 * single key (e.g., TaggableConfigZkManager has a multi-part key).
 */
public abstract class BaseZkManager {

    protected final static Logger log =
            LoggerFactory.getLogger(BaseZkManager.class);

    protected final ZkManager zk;
    protected final PathBuilder paths;
    protected final Serializer serializer;

    /**
     * Constructor.
     *
     * @param zk
     *         Zk data access class
     * @param paths
     *         PathBuilder class to construct ZK paths
     * @param serializer
     *         ZK data serialization class
     */
    public BaseZkManager(ZkManager zk, PathBuilder paths,
                         Serializer serializer) {
        this.zk = zk;
        this.paths = paths;
        this.serializer = serializer;
    }

    /**
     * Gets the children of the specified node and converts them
     * to a list of UUIDs. Logs an error for, but otherwise ignores,
     * any children which are not valid UUIDs.
     */
    public List<UUID> getUuidList(String path) throws StateAccessException {
        return getUuidList(path, null);
    }

    /**
     * Gets the children of the specified node and converts them
     * to a list of UUIDs. Logs an error for, but otherwise ignores,
     * any children which are not valid UUIDs.
     *
     * @param watcher Optional watcher, to be notified of a future update.
     */
    protected List<UUID> getUuidList(String path, Runnable watcher)
            throws StateAccessException {
        Set<String> idStrs = zk.getChildren(path, watcher);
        List<UUID> ids = new ArrayList<>(idStrs.size());
        for (String idStr : idStrs) {
            try {
                ids.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                // Nothing we can do but log an error and move on.
                log.error("'{}' at path '{}' is not a valid UUID. Zookeeper " +
                        "data may be corrupted.",
                        new Object[]{idStr, path, ex});
            }
        }
        return ids;
    }

    /**
     * Gets the children of the specified node and converts them
     * to a set of UUIDs. Logs an error for, but otherwise ignores,
     * any children which are not valid UUIDs.
     */
    protected Set<UUID> getUuidSet(String path) throws StateAccessException {
        return getUuidSet(path, null);
    }

    /**
     * Gets the children of the specified node and converts them
     * to a set of UUIDs. Logs an error for, but otherwise ignores,
     * any children which are not valid UUIDs.
     *
     * @param watcher Optional watcher, to be notified of a future update.
     */
    protected Set<UUID> getUuidSet(String path, Runnable watcher)
            throws StateAccessException {
        Set<String> idStrs = zk.getChildren(path, watcher);
        Set<UUID> ids = new HashSet<>(idStrs.size());
        for (String idStr : idStrs) {
            try {
                ids.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                // Nothing we can do but log an error and move on.
                log.error("'{}' at path '{}' is not a valid UUID. Zookeeper" +
                        "data may be corrupted.",
                        new Object[]{idStr, path, ex});
            }
        }
        return ids;
    }

    /**
     * Gets the config for the specified resource ID asynchronously.
     *
     * @param clazz Class used for deserializing the config.
     * @param callback Receives the config when available.
     * @param watcher Optional watcher to be notified of a future update.
     */
    protected <T> void getAsync(String path, final Class<T> clazz,
                                DirectoryCallback<T> callback,
                                Directory.TypedWatcher watcher) {
        zk.asyncGet(
                path,
                DirectoryCallbackFactory.transform(
                        callback,
                        new Functor<byte[], T>() {
                            @Override
                            public T apply(byte[] arg0) {
                                try {
                                    return serializer.deserialize(arg0, clazz);
                                } catch (SerializationException e) {
                                    log.warn("Could not deserialize " +
                                            clazz.getSimpleName() + " data");
                                    return null;
                                }
                            }
                        }),
                watcher);
    }

    protected void getUUIDSetAsync(String path,
                                   DirectoryCallback<Set<UUID>> callback,
                                   Directory.TypedWatcher watcher) {
        zk.asyncGetChildren(
                path,
                DirectoryCallbackFactory.transform(
                        callback, CollectionFunctors.strSetToUUIDSet),
                watcher);
    }
}
