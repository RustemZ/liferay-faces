/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.bridge.renderkit.html_basic;

import java.io.IOException;
import java.util.Set;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ComponentSystemEvent;
import javax.faces.event.ComponentSystemEventListener;
import javax.faces.event.ListenerFor;
import javax.faces.event.PostAddToViewEvent;
import javax.faces.render.Renderer;
import javax.faces.render.RendererWrapper;

import com.liferay.faces.bridge.application.ResourceInfo;
import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;


/**
 * @author  Neil Griffin
 */
@ListenerFor(systemEventClass = PostAddToViewEvent.class)
public class ResourceRendererBridgeImpl extends RendererWrapper implements ComponentSystemEventListener {

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(ResourceRendererBridgeImpl.class);

	// Private Data Members
	private Set<String> headResourceIdsFromManagedBean;
	private Renderer wrappedRenderer;

	public ResourceRendererBridgeImpl() {
		// Zero-arg constructor required by Mojarra StateHolderSaver class to test if this class is an instance of
		// StateHolder (which it isn't).
	}

	public ResourceRendererBridgeImpl(Renderer renderer) {
		this.wrappedRenderer = renderer;
	}

	@Override
	public void encodeEnd(FacesContext facesContext, UIComponent uiComponent) throws IOException {

		ResourceInfo resourceInfo = new ResourceInfo(uiComponent);
		String resourceId = resourceInfo.getId();

		// Determine whether or not the specified resource is already present in the <head> section of the portal page.
		if (headResourceIdsFromManagedBean == null) {
			HeadManagedBean headManagedBean = HeadManagedBean.getInstance(facesContext);
			headResourceIdsFromManagedBean = headManagedBean.getHeadResourceIds();
		}

		boolean alreadyPresentInPortalPageHead = headResourceIdsFromManagedBean.contains(resourceId);

		// If the speicifed resource is NOT already in the <head> section of the portal page, then
		if (!alreadyPresentInPortalPageHead) {

			boolean ajaxRequest = facesContext.getPartialViewContext().isAjaxRequest();

			// If this is taking place during an Ajax request, then:
			if (ajaxRequest) {

				// Set a custom response writer that knows how to remove double-encoded ampersands from URLs.
				ResponseWriter responseWriter = facesContext.getResponseWriter();
				facesContext.setResponseWriter(new ResponseWriterResourceImpl(responseWriter));

				// Ask the wrapped renderer to encode the script to a custom ResponseWriter
				super.encodeEnd(facesContext, uiComponent);

				// Restore the original response writer.
				facesContext.setResponseWriter(responseWriter);
			}

			// Otherwise:
			else {

				// Ask the wrapped renderer to encode the script to a custom ResponseWriter
				super.encodeEnd(facesContext, uiComponent);

				// If the h:head part of the component tree is being rendered, then
				if (facesContext.getResponseWriter() instanceof HeadResponseWriter) {

					// Mark the resource as having been added to the head.
					headResourceIdsFromManagedBean.add(resourceId);

					logger.debug("Marking resource resourceId=[{0}] as being present in the head", resourceId);
				}
			}
		}
	}

	/**
	 * Since the Mojarra {@link com.sun.faces.renderkit.html_basic.ScriptStyleBaseRenderer} class implements {@link
	 * ComponentSystemEventListener}, this class must implement that interface too, since this is a wrapper type of
	 * class. Mojarra uses this method to intercept {@link PostAddToViewEvent} in order to add script and link resources
	 * to the head (if the target attribute has a value of "head").
	 */
	public void processEvent(ComponentSystemEvent event) throws AbortProcessingException {

		if (wrappedRenderer instanceof ComponentSystemEventListener) {
			ComponentSystemEventListener wrappedListener = (ComponentSystemEventListener) wrappedRenderer;
			wrappedListener.processEvent(event);
		}
		else {
			logger.warn("Wrapped renderer=[{0]} does not implement ComponentSystemEventListener", wrappedRenderer);
		}
	}

	@Override
	public Renderer getWrapped() {
		return wrappedRenderer;
	}
}
