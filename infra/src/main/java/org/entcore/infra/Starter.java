/* Copyright © WebServices pour l'Éducation, 2014
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
 *
 */

package org.entcore.infra;

import fr.wseduc.cron.CronTrigger;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.CookieHelper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.http.BaseServer;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.utils.MapFactory;
import org.entcore.common.utils.StringUtils;
import org.entcore.infra.controllers.AntiVirusController;
import org.entcore.infra.controllers.EmbedController;
import org.entcore.infra.controllers.EventStoreController;
import org.entcore.infra.controllers.MonitoringController;
import org.entcore.infra.cron.HardBounceTask;
import org.entcore.infra.services.EventStoreService;
import org.entcore.infra.services.impl.ClamAvService;
import org.entcore.infra.services.impl.ExecCommandWorker;
import org.entcore.infra.services.impl.MongoDbEventStore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileProps;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.text.ParseException;
import java.util.List;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static fr.wseduc.webutils.Utils.isNotEmpty;

public class Starter extends BaseServer {

	private String node;
	private boolean cluster;
	private LocalMap<String, String> versionMap;
	private LocalMap<String, String> deploymentsIdMap;

	@Override
	public void start() {
		try {
			if (config() == null || config().size() == 0) {
				config = getConfig("", "mod.json");
			}
//			System.setProperty("vertx.cwd", "infra");
//			System.setProperty("vertx.cwd", "build/infra");
//			System.setProperty("user.dir", "build/infra");
//			System.setProperty("vertx.disableFileCaching", "true");
			super.start();
			final LocalMap<Object, Object> serverMap = vertx.sharedData().getLocalMap("server");
			deploymentsIdMap = vertx.sharedData().getLocalMap("deploymentsId");
			versionMap = vertx.sharedData().getLocalMap("versions");
			serverMap.put("signKey", config.getString("key", "zbxgKWuzfxaYzbXcHnK3WnWK" + Math.random()));
			CookieHelper.getInstance().init((String) vertx
					.sharedData().getLocalMap("server").get("signKey"), log);
			cluster = config.getBoolean("cluster", false);
			serverMap.put("cluster", cluster);
			node = config.getString("node", "");
			serverMap.put("node", node);
			JsonObject swift = config.getJsonObject("swift");
			if (swift != null) {
				serverMap.put("swift", swift.encode());
			}
			JsonObject emailConfig = config.getJsonObject("emailConfig");
			if (emailConfig != null) {
				serverMap.put("emailConfig", emailConfig.encode());
			}
			JsonObject filesystem = config.getJsonObject("file-system");
			if (filesystem != null) {
				serverMap.put("file-system", filesystem.encode());
			}
			JsonObject neo4jConfig = config.getJsonObject("neo4jConfig");
			if (neo4jConfig != null) {
				serverMap.put("neo4jConfig", neo4jConfig.encode());
			}
			final String csp = config.getString("content-security-policy");
			if (isNotEmpty(csp)) {
				serverMap.put("contentSecurityPolicy", csp);
			}
			serverMap.put("gridfsAddress", config.getString("gridfs-address", "wse.gridfs.persistor"));
			initModulesHelpers(node);

			/* sharedConf sub-object */
			JsonObject sharedConf = config.getJsonObject("sharedConf", new JsonObject());
			for(String field : sharedConf.fieldNames()){
				serverMap.put(field, sharedConf.getValue(field));
			}

			vertx.fileSystem();
			vertx.sharedData().getLocalMap("skins").putAll(config.getJsonObject("skins", new JsonObject()).getMap());

			List<String> list = vertx.fileSystem().readDirBlocking(".", "^SecuredAction-.*json$");
			log.info("Working Directory = " +
					System.getProperty("user.dir"));

//			List<String> extraclasspath = new ArrayList<>();
//			extraclasspath.add(config.getString("baseCP") + "registry");
//			vertx.deployVerticle("org.entcore.registry.AppRegistry", new DeploymentOptions()
//					.setConfig(config.getJsonObject("app-registry").getJsonObject("config"))
//					.setIsolationGroup("_EntCore_registry")
//					.setExtraClasspath(extraclasspath)
//					, event -> {
//						if (event.failed()) {
//							log.error("Fail deploy app-registry", event.cause());
//						}
//					});
//			deployPreRequiredModules(config.getJsonArray("pre-required-modules"), new Handler<Void>() {
//				@Override
//				public void handle(Void v) {
//					JsonSchemaValidator validator = JsonSchemaValidator.getInstance();
//					validator.setEventBus(getEventBus(vertx));
//					validator.setAddress(node + "json.schema.validator");
//					validator.loadJsonSchema(getPathPrefix(config), vertx);
//
//					deployModule(config.getJsonObject("app-registry"), false, false, new Handler<AsyncResult<String>>() {
//						@Override
//						public void handle(AsyncResult<String> event) {
//							if (event.succeeded()) {
//								deployModules(config.getJsonArray("external-modules", new JsonArray()), false);
//								deployModules(config.getJsonArray("one-modules", new JsonArray()), true);
//								registerGlobalWidgets(config.getString("widgets-path", "../../assets/widgets"));
//								loadInvalidEmails();
//							}
//						}
//					});
//				}
//			});
		} catch (Exception ex) {
			log.error(ex.getMessage());
		}
		JsonObject eventConfig = config.getJsonObject("eventConfig", new JsonObject());
		EventStoreService eventStoreService = new MongoDbEventStore();
		EventStoreController eventStoreController = new EventStoreController(eventConfig);
		eventStoreController.setEventStoreService(eventStoreService);
		addController(eventStoreController);
		addController(new MonitoringController());
		addController(new EmbedController());
		if (config.getBoolean("antivirus", false)) {
			ClamAvService antivirusService = new ClamAvService();
			antivirusService.setVertx(vertx);
			antivirusService.setTimeline(new TimelineHelper(vertx, getEventBus(vertx), config));
			antivirusService.setRender(new Renders(vertx, config));
			antivirusService.init();
			AntiVirusController antiVirusController = new AntiVirusController();
			antiVirusController.setAntivirusService(antivirusService);
			addController(antiVirusController);
			vertx.deployVerticle(ExecCommandWorker.class.getName(), new DeploymentOptions().setWorker(true));
		}
	}

	private void loadInvalidEmails() {
		MapFactory.getClusterMap("invalidEmails", vertx, new Handler<AsyncMap<Object, Object>>() {
			@Override
			public void handle(final AsyncMap<Object, Object> invalidEmails) {
				if (invalidEmails != null) {
					invalidEmails.size(new Handler<AsyncResult<Integer>>() {
						@Override
						public void handle(AsyncResult<Integer> event) {
							if (event.succeeded() && event.result() < 1) {
								MongoDb.getInstance().findOne(HardBounceTask.PLATEFORM_COLLECTION, new JsonObject()
										.put("type", HardBounceTask.PLATFORM_ITEM_TYPE), new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> event) {
										JsonObject res = event.body().getJsonObject("result");
										if ("ok".equals(event.body().getString("status")) && res != null && res.getJsonArray("invalid-emails") != null) {
											for (Object o : res.getJsonArray("invalid-emails")) {
												invalidEmails.put(o, "", new Handler<AsyncResult<Void>>() {
													@Override
													public void handle(AsyncResult<Void> event) {
														if (event.failed()) {
															log.error("Error adding invalid email.", event.cause());
														}
													}
												});
											}
										} else {
											log.error(event.body().getString("message"));
										}
									}
								});
							}
						}
					});
				}
				EmailFactory emailFactory = new EmailFactory(vertx, config);
				try {
					new CronTrigger(vertx, config.getString("hard-bounces-cron", "0 0 7 * * ? *"))
							.schedule(new HardBounceTask(emailFactory.getSender(), config.getInteger("hard-bounces-day", -1),
									new TimelineHelper(vertx, getEventBus(vertx), config), invalidEmails));
				} catch (ParseException e) {
					log.error(e.getMessage(), e);
					vertx.close();
				}
			}
		});
	}

	private void deployPreRequiredModules(final JsonArray array, final Handler<Void> handler) {
		if (array == null || array.size() == 0) {
			handler.handle(null);
			return;
		}
		final Handler [] handlers = new Handler[array.size() + 1];
		handlers[handlers.length - 1] = new Handler<AsyncResult<String>>() {

			@Override
			public void handle(AsyncResult<String> event) {
				if (event.succeeded()) {
					handler.handle(null);
				} else {
					log.error("Error deploying pre-required module.", event.cause());
					vertx.close();
				}
			}
		};
		for (int i = array.size() - 1; i >= 0; i--) {
			final int j = i;
			handlers[i] = new Handler<AsyncResult<String>>() {

				@Override
				public void handle(AsyncResult<String> event) {
					if (event.succeeded()) {
						deployModule(array.getJsonObject(j), false, cluster, handlers[j + 1]);
					} else {
						log.error("Error deploying pre-required module.", event.cause());
						vertx.close();
					}
				}
			};

		}
		handlers[0].handle(new AsyncResult<String>() {
			@Override
			public String result() {
				return null;
			}

			@Override
			public Throwable cause() {
				return null;
			}

			@Override
			public boolean succeeded() {
				return true;
			}

			@Override
			public boolean failed() {
				return false;
			}
		});
	}

	private void deployModule(JsonObject module, boolean internal, boolean overideBusAddress,
			Handler<AsyncResult<String>> handler) {
		if (module.getString("name") == null) {
			return;
		}
		JsonObject conf = new JsonObject();
		if (internal) {
			try {
				conf = getConfig("../" + module.getString("name") + "/", "mod.json");
			} catch (Exception e) {
				log.error("Invalid configuration for module " + module.getString("name"), e);
				return;
			}
		}
		conf = conf.mergeIn(module.getJsonObject("config", new JsonObject()));
		String address = conf.getString("address");
		if (overideBusAddress && !node.isEmpty() && address != null) {
			conf.put("address", node + address);
		}
//		container.deployModule(module.getString("name"),
//				conf, module.getInteger("instances", 1), handler);
	}

	private void addAppVersion(final JsonObject module, final String deploymentId) {
		final List<String> lNameVersion = StringUtils.split(module.getString("name", ""), "~");
		if (lNameVersion != null && lNameVersion.size() == 3) {
			versionMap.put(lNameVersion.get(0) + "." + lNameVersion.get(1), lNameVersion.get(2));
			deploymentsIdMap.put(lNameVersion.get(0) + "." + lNameVersion.get(1), deploymentId);
		}
	}

	private void deployModules(JsonArray modules, boolean internal) {
		for (Object o : modules) {
			final JsonObject module = (JsonObject) o;
			if (module.getString("name") == null) {
				continue;
			}
			JsonObject conf = new JsonObject();
			if (internal) {
				try {
					conf = getConfig("../" + module.getString("name") + "/", "mod.json");
				} catch (Exception e) {
					log.error("Invalid configuration for module " + module.getString("name"), e);
					continue;
				}
			}
			conf = conf.mergeIn(module.getJsonObject("config", new JsonObject()));
//			vertx.deployModule(module.getString("name"),
//					conf, module.getInteger("instances", 1), new Handler<AsyncResult<String>>() {
//						@Override
//						public void handle(AsyncResult<String> event) {
//							if (event.succeeded()) {
//								addAppVersion(module, event.result());
//							}
//						}
//					});
		}
	}

	private void registerWidget(final String widgetPath){
		final String widgetName = new File(widgetPath).getName();
		JsonObject widget = new JsonObject()
				.put("name", widgetName)
				.put("js", "/assets/widgets/"+widgetName+"/"+widgetName+".js")
				.put("path", "/assets/widgets/"+widgetName+"/"+widgetName+".html");

		if(vertx.fileSystem().existsBlocking(widgetPath + "/i18n")){
			widget.put("i18n", "/assets/widgets/"+widgetName+"/i18n");
		}

		JsonObject message = new JsonObject()
				.put("widget", widget);
		vertx.eventBus().send("wse.app.registry.widgets", message, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				if("error".equals(event.body().getString("status"))){
					log.error("Error while registering widget "+widgetName+". "+event.body().getJsonArray("errors"));
					return;
				}
				log.info("Successfully registered widget "+widgetName);
			}
		}));
	}

	private void registerGlobalWidgets(String widgetsPath) {
		vertx.fileSystem().readDir(widgetsPath, new Handler<AsyncResult<List<String>>>() {
			public void handle(AsyncResult<List<String>> asyn) {
				if(asyn.failed()){
					log.error("Error while registering global widgets.", asyn.cause());
					return;
				}
				final List<String> paths = asyn.result();
				for(final String path: paths){
					vertx.fileSystem().props(path, new Handler<AsyncResult<FileProps>>() {
						public void handle(AsyncResult<FileProps> asyn) {
							if(asyn.failed()){
								log.error("Error while registering global widget " + path, asyn.cause());
								return;
							}
							if(asyn.result().isDirectory()){
								registerWidget(path);
							}
						}
					});
				}
			}
		});
	}

	protected JsonObject getConfig(String path, String fileName) throws Exception {
		Buffer b = vertx.fileSystem().readFileBlocking(path + fileName);
		if (b == null) {
			log.error("Configuration file "+ fileName +"not found");
			throw new Exception("Configuration file "+ fileName +" not found");
		}
		else {
			return new JsonObject(b.toString());
		}
	}

}
