/**
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.permissionsex.data;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import ninja.leaping.permissionsex.backend.DataStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class SubjectCache {
    private final String type;
    private final DataStore dataStore;
    private final LoadingCache<String, ImmutableSubjectData> cache;
    private final Map<String, Caching<ImmutableSubjectData>> cacheHolders = new ConcurrentHashMap<>();
    private final CacheListenerHolder<String, ImmutableSubjectData> listeners = new CacheListenerHolder<>();

    public SubjectCache(final String type, final DataStore dataStore) {
        this.type = type;
        this.dataStore = dataStore;
        cache = CacheBuilder.newBuilder()
                .maximumSize(512)
                .build(new CacheLoader<String, ImmutableSubjectData>() {
                    @Override
                    public ImmutableSubjectData load(String identifier) throws Exception {
                        return dataStore.getData(type, identifier, clearListener(identifier));
                    }
                });
    }

    public ImmutableSubjectData getData(String identifier, Caching<ImmutableSubjectData> listener) throws ExecutionException {
        Preconditions.checkNotNull(identifier, "identifier");

        ImmutableSubjectData ret = cache.get(identifier);
        if (listener != null) {
            listeners.addListener(identifier, listener);
        }
        return ret;
    }

    public ListenableFuture<ImmutableSubjectData> doUpdate(String identifier, Function<ImmutableSubjectData, ImmutableSubjectData> action) {
        ImmutableSubjectData data;
        try {
            data = getData(identifier, null);
        } catch (ExecutionException e) {
            return Futures.immediateFailedFuture(e);
        }

        ImmutableSubjectData newData = action.apply(data);
        if (newData != data) {
            return update(identifier, newData);
        } else {
            return Futures.immediateFuture(data);
        }
    }

    public void load(String identifier) throws ExecutionException {
        Preconditions.checkNotNull(identifier, "identifier");

        cache.get(identifier);
    }

    public void invalidate(String identifier) {
        Preconditions.checkNotNull(identifier, "identifier");

        cache.invalidate(identifier);
        cacheHolders.remove(identifier);
        listeners.removeAll(identifier);
    }

    public void cacheAll() {
        for (String ident : dataStore.getAllIdentifiers(type)) {
            try {
                cache.get(ident);
            } catch (ExecutionException e) {
                // oh noes, but we'll still squash it
            }
        }
    }

    public boolean isRegistered(String identifier) {
        Preconditions.checkNotNull(identifier, "identifier");

        return dataStore.isRegistered(type, identifier);
    }

    public ListenableFuture<ImmutableSubjectData> update(String identifier, ImmutableSubjectData newData) {
        Preconditions.checkNotNull(identifier, "identifier");
        Preconditions.checkNotNull(newData, "newData");

        return dataStore.setData(type, identifier, newData);
    }

    private Caching<ImmutableSubjectData> clearListener(final String name) {
        Caching<ImmutableSubjectData> ret = new Caching<ImmutableSubjectData>() {
            @Override
            public void clearCache(ImmutableSubjectData newData) {
                cache.put(name, newData);
                listeners.call(name, newData);
            }
        };
        cacheHolders.put(name, ret);
        return ret;
    }

    public void addListener(String identifier, Caching<ImmutableSubjectData> listener) {
        Preconditions.checkNotNull(identifier, "identifier");
        Preconditions.checkNotNull(listener, "listener");

        listeners.addListener(identifier, listener);

    }

    public String getType() {
        return type;
    }

    public Iterable<String> getAllIdentifiers() {
        return dataStore.getAllIdentifiers(type);
    }
}