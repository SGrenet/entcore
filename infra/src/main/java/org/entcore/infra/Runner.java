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

package org.entcore.infra;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Runner {

	public static void main(String[] args) {
		VertxOptions options = new VertxOptions();
		// Smart cwd detection

		// Based on the current directory (.) and the desired directory (exampleDir), we try to compute the vertx.cwd
		// directory:
		try {
			// We need to use the canonical file. Without the file name is .
			File current = new File(".").getCanonicalFile();
//			if (exampleDir.startsWith(current.getName()) && !exampleDir.equals(current.getName())) {
//				exampleDir = exampleDir.substring(current.getName().length() + 1);
//			}
		} catch (IOException e) {
			// Ignore it.
		}

//		System.setProperty("vertx.cwd", exampleDir);
//		Consumer<Vertx> runner = vertx -> {
//			try {
//				if (deploymentOptions != null) {
//					vertx.deployVerticle(verticleID, deploymentOptions);
//				} else {
//					vertx.deployVerticle(verticleID);
//				}
//			} catch (Throwable t) {
//				t.printStackTrace();
//			}
//		};
//		if (options.isClustered()) {
//			Vertx.clusteredVertx(options, res -> {
//				if (res.succeeded()) {
//					Vertx vertx = res.result();
//					runner.accept(vertx);
//				} else {
//					res.cause().printStackTrace();
//				}
//			});
//		} else {launcher

		final String baseClasspath = args[1];
			Vertx vertx = Vertx.vertx(options.setFileResolverCachingEnabled(false));
			Buffer conf = vertx.fileSystem().readFileBlocking(args[0]);
		List<String> extraclasspath = new ArrayList<>();
		extraclasspath.add(baseClasspath + "infra-3.0-SNAPSHOT-fat.jar");
		JsonObject config = new JsonObject(conf);
		config.put("baseCP", baseClasspath);
		vertx.deployVerticle("org.entcore.infra.Starter", new DeploymentOptions()
					.setConfig(config)
					.setIsolationGroup("_EntCore_infra")
					.setExtraClasspath(extraclasspath),
					ar -> {
						if (ar.succeeded()) {
//							List<String> ecp = new ArrayList<>();
//							ecp.add(baseClasspath + "registry");
//							vertx.deployVerticle("org.entcore.registry.AppRegistry", new DeploymentOptions()
//									.setConfig(config.getJsonObject("app-registry").getJsonObject("config"))
//									.setIsolationGroup("_EntCore_registry")
//									.setExtraClasspath(ecp)
//									, event -> {
//								if (event.failed()) {
//									System.out.println("Fail deploy app-registry : " + event.cause());
//								}
//							});
						}
					}
			);
//		}
	}

}
