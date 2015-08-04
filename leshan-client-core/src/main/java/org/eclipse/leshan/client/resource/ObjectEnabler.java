/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;

public class ObjectEnabler extends BaseObjectEnabler {

    // TODO we should manage that in a threadsafe way
    private Map<Integer, LwM2mInstanceEnabler> instances;
    private Class<? extends LwM2mInstanceEnabler> instanceClass;

    public ObjectEnabler(int id, ObjectModel objectModel, Map<Integer, LwM2mInstanceEnabler> instances,
            Class<? extends LwM2mInstanceEnabler> instanceClass) {
        super(id, objectModel);
        this.instances = new HashMap<Integer, LwM2mInstanceEnabler>(instances);
        this.instanceClass = instanceClass;
        for (Entry<Integer, LwM2mInstanceEnabler> entry : this.instances.entrySet()) {
            listenInstance(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public List<Integer> getAvailableInstance() {
        List<Integer> ids = new ArrayList<Integer>(instances.keySet());
        Collections.sort(ids);
        return ids;
    }

    @Override
    protected CreateResponse doCreate(CreateRequest request) {
        try {
            // TODO manage case where instanceid is not available
            LwM2mInstanceEnabler newInstance = instanceClass.newInstance();
            newInstance.setObjectModel(getObjectModel());

            for (LwM2mResource resource : request.getResources()) {
                newInstance.write(resource.getId(), resource);
            }
            instances.put(request.getPath().getObjectInstanceId(), newInstance);
            listenInstance(newInstance, request.getPath().getObjectInstanceId());

            return new CreateResponse(ResponseCode.CREATED, request.getPath().toString());
        } catch (InstantiationException | IllegalAccessException e) {
            // TODO not really a bad request ...
            return new CreateResponse(ResponseCode.BAD_REQUEST);
        }
    }

    @Override
    protected ValueResponse doRead(ReadRequest request) {
        LwM2mPath path = request.getPath();

        // Manage Object case
        if (path.isObject()) {
            List<LwM2mObjectInstance> lwM2mObjectInstances = new ArrayList<>();
            System.out.println("INSTANCES======"+instances);
            for (Entry<Integer, LwM2mInstanceEnabler> entry : instances.entrySet()) {
                lwM2mObjectInstances.add(getLwM2mObjectInstance(entry.getKey(), entry.getValue()));
            }
            return new ValueResponse(ResponseCode.CONTENT, new LwM2mObject(getId(),
                    lwM2mObjectInstances.toArray(new LwM2mObjectInstance[0])));
        }

        // Manage Instance case
        LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null)
            return new ValueResponse(ResponseCode.NOT_FOUND);

        if (path.getResourceId() == null) {
            return new ValueResponse(ResponseCode.CONTENT, getLwM2mObjectInstance(path.getObjectInstanceId(), instance));
        }

        // Manage Resource case
        return instance.read(path.getResourceId());
    }

    private LwM2mObjectInstance getLwM2mObjectInstance(int instanceid, LwM2mInstanceEnabler instance) {
        final List<LwM2mResource> resources = new ArrayList<>();
        for (ResourceModel resourceModel : getObjectModel().resources.values()) {
            if (resourceModel.operations.isReadable()) {
                ValueResponse response = instance.read(resourceModel.id);
                if (response.getCode() == ResponseCode.CONTENT && response.getContent() instanceof LwM2mResource)
                    resources.add((LwM2mResource) response.getContent());
            }
        }
        return new LwM2mObjectInstance(instanceid, resources.toArray(new LwM2mResource[0]));
    }

    @Override
    protected LwM2mResponse doWrite(WriteRequest request) {
        LwM2mPath path = request.getPath();

        // Manage Instance case
        LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null)
            return new LwM2mResponse(ResponseCode.NOT_FOUND);

        if (path.getResourceId() == null) {
            for (LwM2mResource resource : ((LwM2mObjectInstance) request.getNode()).getResources().values()) {
                instance.write(resource.getId(), resource);
            }
            return new LwM2mResponse(ResponseCode.CHANGED);
        }

        // Manage Resource case
        return instance.write(path.getResourceId(), (LwM2mResource) request.getNode());
    }

    @Override
    protected LwM2mResponse doExecute(ExecuteRequest request) {
        LwM2mPath path = request.getPath();
        LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null) {
            return new LwM2mResponse(ResponseCode.NOT_FOUND);
        }
        return instance.execute(path.getResourceId(), request.getParameters());
    }

    @Override
    protected LwM2mResponse doDelete(DeleteRequest request) {
        LwM2mPath path = request.getPath();
        if (!instances.containsKey(path.getObjectInstanceId())) {
            return new LwM2mResponse(ResponseCode.NOT_FOUND);
        }
        instances.remove(request.getPath().getObjectInstanceId());
        return new LwM2mResponse(ResponseCode.DELETED);
    }

    private void listenInstance(LwM2mInstanceEnabler instance, final int instanceId) {
        instance.addResourceChangedListener(new ResourceChangedListener() {
            @Override
            public void resourceChanged(int resourceId) {
                getNotifySender().sendNotify(getId() + "/" + instanceId + "/" + resourceId);
            }
        });
    }

}
