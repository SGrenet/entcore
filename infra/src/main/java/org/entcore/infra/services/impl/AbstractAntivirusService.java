/*
 * Copyright © WebServices pour l'Éducation, 2017
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.infra.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.infra.services.AntivirusService;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractAntivirusService implements AntivirusService, Handler<Message<JsonObject>> {

	protected static final Logger log = LoggerFactory.getLogger(AntivirusService.class);
	protected Vertx vertx;
	private Map<String, InfectedFile> queue;
	private Renders render;
	private Storage storage;
	private TimelineHelper timeline;

	public void init() {
		this.queue = new HashMap<>();
		this.storage = new StorageFactory(vertx).getStorage();
		vertx.eventBus().registerLocalHandler("antivirus", this);
	}

	protected abstract void parseScanReport(String path, AsyncResultHandler<List<InfectedFile>> handler);


	@Override
	public void replaceInfectedFiles(String path, final Handler<Either<String, JsonObject>> handler) {
		parseScanReport(path, new AsyncResultHandler<List<InfectedFile>>() {
			@Override
			public void handle(AsyncResult<List<InfectedFile>> event) {
				if (event.succeeded()) {
					final JsonObject j = launchReplace(event.result());
					handler.handle(new Either.Right<String, JsonObject>(j));
				} else {
					log.error("Error parsing scan report.", event.cause());
					handler.handle(new Either.Left<String, JsonObject>(event.cause().getMessage()));
				}
			}
		});
	}

	protected JsonObject launchReplace(List<InfectedFile> infectedFiles) {
		final JsonObject j = new JsonObject();
		for (final InfectedFile i : infectedFiles) {
			final JsonObject message = new JsonObject()
					.putString("action", "getInfos")
					.putString("id", i.getId())
					.putString("replyTo", "antivirus")
					.putString("replyAction", "rmInfected");
			final long timerId = vertx.setTimer(30000l, new Handler<Long>() {
				@Override
				public void handle(Long event) {
					removeInfectedFile(i, null);
				}
			});
			i.setTimerId(timerId);
			queue.put(i.getId(), i);
			vertx.eventBus().publish("storage", message);
			j.putString(i.getPath(), i.getVirus());
		}
		return j;
	}

	@Override
	public void handle(Message<JsonObject> event) {
		switch (event.body().getString("action", "")) {
			case "rmInfected" :
				InfectedFile i = queue.remove(event.body().getString("id", ""));
				if (i != null) {
					vertx.cancelTimer(i.getTimerId());
					i.setApplication(event.body().getString("application"));
					i.setName(event.body().getString("name"));
					i.setOwner(event.body().getString("owner"));
					removeInfectedFile(i, event);
				}
				break;
		}
	}

	private void removeInfectedFile(final InfectedFile i, final Message<JsonObject> message) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			final JsonObject params = new JsonObject(mapper.writeValueAsString(i));
			log.info("Remove infected file : " + params.encode());
			final HttpServerRequest request = new JsonHttpServerRequest(new JsonObject());
			render.processTemplate(request, "text/infectedFile.txt", params, new Handler<String>() {
				@Override
				public void handle(String content) {
					storage.writeBuffer(i.getPath(), i.getId(), new Buffer(content), "text/plain", i.getName() + ".txt", new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject event) {
							if (timeline != null && i.getOwner() != null) {
								final List<String> recipients = new ArrayList<>();
								recipients.add(i.getOwner());
								timeline.notifyTimeline(request,
										"workspace.delete-virus", null, recipients, null, params);
							}
							if (message != null) {
								JsonObject m = new JsonObject()
										.putString("id", i.getId())
										.putString("name", i.getName() + ".txt")
										.putString("contentType", "text/plain")
										.putString("action", "updateInfos");
								message.reply(m, new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> r) {
										if ("ok".equals(r.body().getString("status")) && r.body().getInteger("count", -1) > 0) {
											log.info("File info " + i.getId() + " updated.");
										} else {
											log.error("Error updating file info " + i.getId());
										}
									}
								});
							}
						}
					});
				}
			});
		} catch (IOException | DecodeException e) {
			log.error("Error serializing infected file : " + i.getId(), e);
		}
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

	public void setTimeline(TimelineHelper timeline) {
		this.timeline = timeline;
	}

	public void setRender(Renders render) {
		this.render = render;
	}

}
