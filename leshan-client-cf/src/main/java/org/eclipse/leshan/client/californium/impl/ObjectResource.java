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
package org.eclipse.leshan.client.californium.impl;

import java.util.Iterator;
import java.util.List;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationContainer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.LinkObject;
import org.eclipse.leshan.ObserveSpec;
import org.eclipse.leshan.client.resource.LinkFormattable;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.NotifySender;
import org.eclipse.leshan.client.util.ObserveSpecParser;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.codec.InvalidValueException;
import org.eclipse.leshan.core.node.codec.LwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeEncoder;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.ContentFormatHelper;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.DiscoverRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteAttributesRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.DiscoverResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;
import org.eclipse.leshan.util.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CoAP {@link Resource} in charge of handling requests for of a lwM2M Object.
 */
public class ObjectResource extends CoapResource implements LinkFormattable, NotifySender {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectResource.class);

    private final LwM2mObjectEnabler nodeEnabler;

    public ObjectResource(final LwM2mObjectEnabler nodeEnabler) {
        super(Integer.toString(nodeEnabler.getId()));
        this.nodeEnabler = nodeEnabler;
        this.nodeEnabler.setNotifySender(this);
        setObservable(true);
    }

    @Override
    public void handleRequest(final Exchange exchange) {
        try {
            super.handleRequest(exchange);
        } catch (final Exception e) {
            LOG.error("Exception while handling a request on the /rd resource", e);
            // unexpected error, we should sent something like a INTERNAL_SERVER_ERROR.
            // but it would not be LWM2M compliant. so BAD_REQUEST for now...
            exchange.sendResponse(new Response(ResponseCode.BAD_REQUEST));
        }
    }

    @Override
    public void handleGET(final CoapExchange exchange) {
        final String URI = exchange.getRequestOptions().getUriPathString();

        // Manage Discover Request
        if (exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {
            final DiscoverResponse response = nodeEnabler.discover(new DiscoverRequest(URI));
            exchange.respond(fromLwM2mCode(response.getCode()), LinkObject.serialyse(response.getObjectLinks()),
                    MediaTypeRegistry.APPLICATION_LINK_FORMAT);
        }
        // Manage Observe Request
        else if (exchange.getRequestOptions().hasObserve()) {
            final ValueResponse response = nodeEnabler.observe(new ObserveRequest(URI));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CONTENT) {
                final LwM2mPath path = new LwM2mPath(URI);
                final LwM2mNode content = response.getContent();
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                final ContentFormat contentFormat = ContentFormatHelper.compute(path, content, model);
                exchange.respond(ResponseCode.CONTENT, LwM2mNodeEncoder.encode(content, contentFormat, path, model));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        }
        // Manage Read Request
        else {
            final ValueResponse response = nodeEnabler.read(new ReadRequest(URI));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CONTENT) {
                final LwM2mPath path = new LwM2mPath(URI);
                final LwM2mNode content = response.getContent();
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                final ContentFormat contentFormat = ContentFormatHelper.compute(path, content, model);
                exchange.respond(ResponseCode.CONTENT, LwM2mNodeEncoder.encode(content, contentFormat, path, model));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        }
    }

    @Override
    public void handlePUT(final CoapExchange coapExchange) {
        final String URI = coapExchange.getRequestOptions().getUriPathString();

        // get Observe Spec
        ObserveSpec spec = null;
        if (coapExchange.advanced().getRequest().getOptions().getURIQueryCount() != 0) {
            final List<String> uriQueries = coapExchange.advanced().getRequest().getOptions().getUriQuery();
            spec = ObserveSpecParser.parse(uriQueries);
        }

        // Manage Write Attributes Request
        if (spec != null) {
            final LwM2mResponse response = nodeEnabler.writeAttributes(new WriteAttributesRequest(URI, spec));
            coapExchange.respond(fromLwM2mCode(response.getCode()));
            return;
        }
        // Manage Write Request (replace)
        else {
            final LwM2mPath path = new LwM2mPath(URI);
            final ContentFormat contentFormat = ContentFormat.fromCode(coapExchange.getRequestOptions().getContentFormat());
            LwM2mNode lwM2mNode;
            try {
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                lwM2mNode = LwM2mNodeDecoder.decode(coapExchange.getRequestPayload(), contentFormat, path, model);
                final LwM2mResponse response = nodeEnabler.write(new WriteRequest(URI, lwM2mNode, contentFormat, true));
                coapExchange.respond(fromLwM2mCode(response.getCode()));
                return;
            } catch (final InvalidValueException e) {
                coapExchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
                return;
            }

        }
    }

    @Override
    public void handlePOST(final CoapExchange exchange) {
        final String URI = exchange.getRequestOptions().getUriPathString();
        final LwM2mPath path = new LwM2mPath(URI);

        // Manage Execute Request
        if (path.isResource()) {
            final LwM2mResponse response = nodeEnabler.execute(new ExecuteRequest(URI));
            exchange.respond(fromLwM2mCode(response.getCode()));
            return;
        }

        // Manage Create Request
        try {
            final ContentFormat contentFormat = ContentFormat.fromCode(exchange.getRequestOptions().getContentFormat());
            final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
            final LwM2mNode lwM2mNode = LwM2mNodeDecoder.decode(exchange.getRequestPayload(), contentFormat, path, model);
            if (!(lwM2mNode instanceof LwM2mObjectInstance)) {
                exchange.respond(ResponseCode.BAD_REQUEST);
                return;
            }
            final LwM2mResource[] resources = ((LwM2mObjectInstance) lwM2mNode).getResources().values()
                    .toArray(new LwM2mResource[0]);
            final CreateResponse response = nodeEnabler.create(new CreateRequest(URI, resources, contentFormat));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CREATED) {
                exchange.setLocationPath(response.getLocation());
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        } catch (final InvalidValueException e) {
            exchange.respond(ResponseCode.BAD_REQUEST);
            return;
        }
    }

    @Override
    public void handleDELETE(final CoapExchange coapExchange) {
        // Manage Delete Request
        final String URI = coapExchange.getRequestOptions().getUriPathString();
        final LwM2mResponse response = nodeEnabler.delete(new DeleteRequest(URI));
        coapExchange.respond(fromLwM2mCode(response.getCode()));
    }

    @Override
    public void sendNotify(final String URI) {
        notifyObserverRelationsForResource(URI);
    }

    /*
     * Override the default behavior so that requests to sub resources (typically /ObjectId/*) are handled by this
     * resource.
     */
    @Override
    public Resource getChild(final String name) {
        return this;
    }

    // TODO leshan-code-cf: this code should be factorize in a leshan-core-cf project.
    // duplicated from org.eclipse.leshan.server.californium.impl.RegisterResource
    public static ResponseCode fromLwM2mCode(final org.eclipse.leshan.ResponseCode code) {
        Validate.notNull(code);

        switch (code) {
        case CREATED:
            return ResponseCode.CREATED;
        case DELETED:
            return ResponseCode.DELETED;
        case CHANGED:
            return ResponseCode.CHANGED;
        case CONTENT:
            return ResponseCode.CONTENT;
        case BAD_REQUEST:
            return ResponseCode.BAD_REQUEST;
        case UNAUTHORIZED:
            return ResponseCode.UNAUTHORIZED;
        case NOT_FOUND:
            return ResponseCode.NOT_FOUND;
        case METHOD_NOT_ALLOWED:
            return ResponseCode.METHOD_NOT_ALLOWED;
        case FORBIDDEN:
            return ResponseCode.FORBIDDEN;
        default:
            throw new IllegalArgumentException("Invalid CoAP code for LWM2M response: " + code);
        }
    }

    // TODO this code not be here, this is not its responsibility to do that.
    @Override
    public String asLinkFormat() {
        final StringBuilder linkFormat = LinkFormat.serializeResource(this).append(
                LinkFormat.serializeAttributes(getAttributes()));
        linkFormat.deleteCharAt(linkFormat.length() - 1);
        return linkFormat.toString();
    }

    /*
     * TODO: Observe HACK we should see if this could not be integrated in californium
     * http://dev.eclipse.org/mhonarc/lists/cf-dev/msg00181.html
     */
    private final ObserveRelationContainer observeRelations = new ObserveRelationContainer();

    @Override
    public void addObserveRelation(final ObserveRelation relation) {
        synchronized (observeRelations) {
            super.addObserveRelation(relation);
            observeRelations.add(relation);
        }
    }

    @Override
    public void removeObserveRelation(final ObserveRelation relation) {
        synchronized (observeRelations) {
            super.removeObserveRelation(relation);
            observeRelations.remove(relation);
        }
    }

    public void removeObserverRelations() {
        synchronized (observeRelations) {
            for (final Iterator<ObserveRelation> iter = observeRelations.iterator(); iter.hasNext();) {
                final ObserveRelation relation = iter.next();
                iter.remove();
                super.removeObserveRelation(relation);
            }
        }
    }

    protected void notifyObserverRelationsForResource(final String URI) {
        synchronized (observeRelations) {
            for (final ObserveRelation relation : observeRelations) {
                if (relation.getExchange().getRequest().getOptions().getUriPathString().equals(URI)) {
                    relation.notifyObservers();
                }
            }
        }
    }

}
