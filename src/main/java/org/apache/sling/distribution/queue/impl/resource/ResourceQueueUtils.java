/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sling.distribution.queue.impl.resource;


import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.distribution.DistributionRequestType;
import org.apache.sling.distribution.packaging.DistributionPackageInfo;
import org.apache.sling.distribution.queue.DistributionQueueEntry;
import org.apache.sling.distribution.queue.DistributionQueueItem;
import org.apache.sling.distribution.queue.DistributionQueueItemState;
import org.apache.sling.distribution.queue.DistributionQueueItemStatus;


import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ResourceQueueUtils {

    // prefix for queue entry ids
    private static final String ID_START = "distrq-";

    // resource folder for queue roots
    private static final String RESOURCE_ROOT = "sling:Folder";

    // resource type for internal ordered folders
    public static final String RESOURCE_FOLDER = "sling:OrderedFolder";

    // resource type for internal entries
    private static final String RESOURCE_ITEM = "nt:unstructured";

    private static final String DISTRIBUTION_PACKAGE_PREFIX = "distribution.";
    private static final String DISTRIBUTION_PACKAGE_ID = DISTRIBUTION_PACKAGE_PREFIX + "item.id";
    private static final String DISTRIBUTION_PACKAGE_SIZE = DISTRIBUTION_PACKAGE_PREFIX + "package.size";


    private static final AtomicLong itemCounter = new AtomicLong(0);


    private static Map<String, Object> serializeItem(DistributionQueueItem queueItem) {

        Map<String, Object> properties = new HashMap<String, Object>();

        for (String key : queueItem.keySet()) {
            Object value = queueItem.get(key);

            if (DistributionPackageInfo.PROPERTY_REQUEST_TYPE.equals(key)) {
                if (value instanceof DistributionRequestType) {
                    value = ((DistributionRequestType) value).name();
                }
            }

            if (value != null) {
                properties.put(DISTRIBUTION_PACKAGE_PREFIX + key, value);
            }
        }

        properties.put(DISTRIBUTION_PACKAGE_ID, queueItem.getPackageId());
        properties.put(DISTRIBUTION_PACKAGE_SIZE, queueItem.getSize());

        return properties;
    }

    private static DistributionQueueItem deserializeItem(ValueMap valueMap) {

        String packageId = valueMap.get(DISTRIBUTION_PACKAGE_ID, String.class);
        Long sizeProperty = valueMap.get(DISTRIBUTION_PACKAGE_SIZE, Long.class);
        long size = sizeProperty == null ? -1 : sizeProperty;

        Map<String, Object> properties = new HashMap<String, Object>();

        for (String key : valueMap.keySet()) {
            if (key.startsWith(DISTRIBUTION_PACKAGE_PREFIX)) {
                String infoKey = key.substring(DISTRIBUTION_PACKAGE_PREFIX.length());
                Object value = valueMap.get(key);

                if (DistributionPackageInfo.PROPERTY_REQUEST_TYPE.equals(infoKey)) {
                    if (value instanceof String) {
                        value = DistributionRequestType.valueOf((String) value);
                    }
                }

                properties.put(infoKey, value);
            }
        }

        DistributionQueueItem queueItem = new DistributionQueueItem(packageId, size, properties);
        return queueItem;
    }

    static DistributionQueueEntry readEntry(Resource queueRoot, Resource resource) {

        if (resource == null) {
            return null;
        }

        if (!resource.getPath().startsWith(queueRoot.getPath() + "/")) {
            return null;
        }

        if (!resource.isResourceType(RESOURCE_ITEM)) {
            return null;
        }

        String queueName = queueRoot.getName();
        DistributionQueueItem queueItem = deserializeItem(resource.getValueMap());
        DistributionQueueItemStatus queueItemStatus = new DistributionQueueItemStatus(DistributionQueueItemState.QUEUED, queueName);

        String entryId = getIdFromPath(queueRoot.getPath(), resource.getPath());

        return new DistributionQueueEntry(entryId, queueItem, queueItemStatus);
    }

    static List<DistributionQueueEntry> getEntries(Resource queueRoot, int skip, int limit) {
        Iterator<Resource> it = new ResourceIterator(queueRoot, RESOURCE_FOLDER, false, true);

        List<DistributionQueueEntry> entries = new ArrayList<DistributionQueueEntry>();

        int i = 0;
        while (it.hasNext()) {
            Resource resource = it.next();

            if (i++ < skip) {
                continue;
            }

            DistributionQueueEntry entry = readEntry(queueRoot, resource);
            entries.add(entry);

            if (entries.size() >= limit) {
                break;
            }
        }

        return entries;

    }


    static DistributionQueueEntry getHead(Resource root) {
        Iterator<DistributionQueueEntry> it =  getEntries(root, 0, 1).iterator();

        if (it.hasNext()) {
            return it.next();
        }

        return null;
    }

    public static Resource getRootResource(ResourceResolver resourceResolver, String rootPath) throws PersistenceException {
        Resource resource =  ResourceUtil.getOrCreateResource(resourceResolver, rootPath, RESOURCE_FOLDER, RESOURCE_ROOT, true);

        return resource;
    }

    public static Resource getResourceById(Resource root, String entryId)  {
        String entryPath = getPathFromId(root.getPath(), entryId);
        return root.getResourceResolver().getResource(entryPath);
    }



    public static Resource createResource(Resource root, String entryId, DistributionQueueItem queueItem) throws PersistenceException {

        ResourceResolver resourceResolver = root.getResourceResolver();
        String entryPath = getPathFromId(root.getPath(), entryId);

        Map<String, Object> properties = serializeItem(queueItem);

        properties.put("sling:resourceType", RESOURCE_ITEM);
        Resource resourceItem =  ResourceUtil.getOrCreateResource(resourceResolver, entryPath, properties,
                RESOURCE_FOLDER, true);

        resourceResolver.commit();

        return resourceItem;
    }

    public static void deleteResource(Resource resource) throws PersistenceException {
        ResourceResolver resolver = resource.getResourceResolver();

        String path = resource.getPath();

        try {
            resolver.delete(resource);
            resolver.commit();
        } catch (PersistenceException var10) {
            resolver.revert();
            resolver.refresh();
            resource = resolver.getResource(path);
            if (resource != null) {
                resolver.delete(resource);
                resolver.commit();
            }
        }
    }


    public static int getResourceCount(Resource root) {
        ResourceResolver resolver = root.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);

        StringBuilder buf = new StringBuilder();
        buf.append("/jcr:root");
        buf.append(root.getPath());
        buf.append("//element(*,");
        buf.append(RESOURCE_ITEM);
        buf.append(")");

        try {
            QueryManager qManager = session.getWorkspace().getQueryManager();
            Query q = qManager.createQuery(buf.toString(), "xpath");
            final QueryResult res = q.execute();

            NodeIterator it = res.getNodes();
            return  (int) it.getSize();
        } catch (RepositoryException e) {
            return -1;
        }
    }


    public static String getUniqueEntryId() {
        String entryPath = getUniqueEntryPath();
        return escapeId(entryPath);
    }

    private static String getUniqueEntryPath() {
        final Calendar now = Calendar.getInstance();
        final StringBuilder sb = new StringBuilder();
        sb.append(getTimePath(now));
        sb.append('/');
        sb.append(UUID.randomUUID().toString().replace("-", ""));
        sb.append('_');
        sb.append(itemCounter.getAndIncrement());

        return sb.toString();
    }

    /**
     * Transforms current time to path 2018/01/03/23/54
     * @param now the current time
     * @return the serialized time
     */
    public static String getTimePath(Calendar now) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/HH/mm");

        return sdf.format(now.getTime());
    }


    /**
     * Checks if path is safe to delete at this time.
     * A path is safe to delete if the nowPath does not overlap with it.
     *
     * @param nowPath represents a full path of current time (e.g. 2018/01/03/23/54)
     * @param path the path to be checked (it can be a partial path e.g. 2018/01)
     * @return true if checked path is in the past
     */
    public static boolean isSafeToDelete(String nowPath, String path) {

        // should not happen
        if (nowPath.length() < path.length()) {
            return false;
        }

        nowPath = nowPath.substring(0, path.length());

        return nowPath.compareTo(path) > 0;
    }



    private static String getPathFromId(String roothPath, String entryId) {
        String entryPath = unescapeId(entryId);
        return roothPath + "/" + entryPath;
    }

    private static String getIdFromPath(String rootPath, String path) {

        if (path.startsWith(rootPath)) {
            String entryPath = path.substring(rootPath.length()+1);

            String entryId = escapeId(entryPath);

            return entryId;
        }
        throw new IllegalArgumentException("entry path does not start with " + rootPath);
    }


    private static String escapeId(String jobId) {
        //return id;
        if (jobId == null) {
            return null;
        }
        return ID_START + jobId.replace("/", "--");
    }

    public static String unescapeId(String itemId) {
        if (itemId == null) {
            return null;
        }
        if (!itemId.startsWith(ID_START)) {
            return null;
        }

        return itemId.replace(ID_START, "").replace("--", "/");
    }

}