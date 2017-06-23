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

package org.entcore.feeder.csv;

import com.opencsv.CSVWriter;
import fr.wseduc.webutils.DefaultAsyncResult;
import org.entcore.feeder.exceptions.ValidationException;
import org.entcore.feeder.utils.CSVUtil;
import org.entcore.feeder.utils.Report;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static fr.wseduc.webutils.Utils.getOrElse;
import static fr.wseduc.webutils.Utils.isEmpty;
import static fr.wseduc.webutils.Utils.isNotEmpty;

public class CsvReport extends Report {

	private static final String MAPPINGS = "mappings";
	private static final String CLASSES_MAPPING = "classesMapping";
	private static final String HEADERS = "headers";
	public static final String KEYS_CLEANED = "keysCleaned";
	private final Vertx vertx;
	protected final ProfileColumnsMapper columnsMapper;

	public CsvReport(Vertx vertx, JsonObject importInfos) {
		super(importInfos.getString("language", "fr"));
		final String importId = importInfos.getString("id");
		if (isNotEmpty(importId)) {
			importInfos.putString("_id", importId);
			importInfos.removeField("id");
		}
		result.mergeIn(importInfos);
		if (result.getBoolean(KEYS_CLEANED, false)) {
			uncleanKeys();
		}
		this.vertx = vertx;
		this.columnsMapper = new ProfileColumnsMapper(getMappings());
	}

	public void addHeader(String profile, JsonArray header) {
		JsonObject headers = result.getObject(HEADERS);
		if (headers == null) {
			headers = new JsonObject();
			result.putObject(HEADERS, headers);
		}
		headers.putArray(profile, header);
	}

	public void addMapping(String profile, JsonObject mapping) {
		JsonObject mappings = result.getObject(MAPPINGS);
		if (mappings == null) {
			mappings = new JsonObject();
			result.putObject(MAPPINGS, mappings);
		}
		mappings.putObject(profile, mapping);
	}

	public JsonObject getMappings() {
		return result.getObject(MAPPINGS);
	}

	public void setMappings(JsonObject mappings) {
		if (mappings != null && mappings.size() > 0) {
			result.putObject(MAPPINGS, mappings);
		}
	}

	public void setClassesMapping(JsonObject mapping) {
		if (mapping != null && mapping.size() > 0) {
			result.putObject(CLASSES_MAPPING, mapping);
		}
	}

	public JsonObject getClassesMapping(String profile) {
		final JsonObject cm = result.getObject(CLASSES_MAPPING);
		if (cm != null) {
			return cm.getObject(profile);
		}
		return null;
	}

	public JsonObject getClassesMappings() {
		return result.getObject(CLASSES_MAPPING);
	}

	public void exportFiles(final Handler<AsyncResult<String>> handler) {
		final String path = result.getString("path");
		final String structureName = result.getString("structureName");
		final JsonObject headers = result.getObject(HEADERS);
		final JsonObject files = result.getObject(FILES);
		if (files == null || isEmpty(path) || isEmpty(structureName) || headers == null) {
			handler.handle(new DefaultAsyncResult<String>(new ValidationException("missing.arguments")));
			return;
		}
		FileSystem fs = vertx.fileSystem();
		final String structureExternalId = result.getString("structureExternalId");
		final String UAI = result.getString("UAI");
		final String p = (path + File.separator + structureName +
				(isNotEmpty(structureExternalId) ? "@" + structureExternalId: "") +
				(isNotEmpty(UAI) ? "_" + UAI : ""));
//				.replaceFirst("tmp", "tmp/test");
		fs.mkdir(p, true, new Handler<AsyncResult<Void>>() {
			@Override
			public void handle(AsyncResult<Void> event) {
				try {
					if (event.succeeded()) {
						for (String file : files.getFieldNames()) {
							final JsonArray header = headers.getArray(file);
							final JsonArray lines = files.getArray(file);
							if (lines == null || lines.size() == 0 || header == null || header.size() == 0) {
								handler.handle(new DefaultAsyncResult<String>(new ValidationException("missing.file." + file)));
								return;
							}
							final CSVWriter writer = CSVUtil.getCsvWriter(p + File.separator + file, "UTF-8");
							final String[] strings = new ArrayList<String>(header.toList()).toArray(new String[header.size()]);
							final List<String> columns = new ArrayList<>();
							writer.writeNext(strings);
							columnsMapper.getColumsNames(file, strings, columns);
							for (Object o : lines) {
								if (!(o instanceof JsonObject)) continue;
								final JsonObject line = (JsonObject) o;
								final Map<String, Integer> columnCount = new HashMap<>();
								final String [] l = new String[strings.length];
								int i = 0;
								for (String column : columns) {
									Object v = line.getValue(column);
									Integer count = getOrElse(columnCount.get(column), 0);
									if (v instanceof String) {
										if (count == 0) {
											//if (column.startsWith("child")) {
												l[i] = cleanStructure((String) v);
//											} else {
//												l[i] = (String) v;
//											}
										}
									} else if (v instanceof JsonArray) {
										if (((JsonArray) v).size() > count) {
											//if (column.startsWith("child")) {
												l[i] = cleanStructure(((JsonArray) v).<String>get(count));
//											} else {
//												l[i] = ((JsonArray) v).<String>get(count);
//											}
										}
									} else {
										l[i] = "";
									}
									columnCount.put(column, ++count);
									i++;
								}
								writer.writeNext(l);
							}
							writer.close();
						}
						handler.handle(new DefaultAsyncResult<>(path));
					} else {
						handler.handle(new DefaultAsyncResult<String>(event.cause()));
					}
				} catch (IOException e) {
					handler.handle(new DefaultAsyncResult<String>(e));
					// TODO delete directory
				}
			}
		});
	}

	private String cleanStructure(String v) {
		if (v != null && v.contains("$")) {
			return v.substring(v.indexOf("$") + 1);
		}
		return v;
	}

	@Override
	public String getSource() {
		return "CSV";
	}

	public String getStructureExternalId() {
		return result.getString("structureExternalId");
	}

	@Override
	protected void cleanKeys() {
		int count = 0;
		count += cleanAttributeKeys(getClassesMappings());
		count += cleanAttributeKeys(getMappings());
		count += cleanAttributeKeys(result.getObject("errors"));
		if (count > 0) {
			result.putBoolean(KEYS_CLEANED, true);
		}
	}

	protected void uncleanKeys() {
		uncleanAttributeKeys(getClassesMappings());
		uncleanAttributeKeys(getMappings());
		uncleanAttributeKeys(result.getObject("errors"));
		result.removeField(KEYS_CLEANED);
	}

//	protected void setStructureExternalIdIfAbsent(String structureExternalId) {
//		if (isEmpty(result.getString("structureExternalId"))) {
//			result.putString("structureExternalId", structureExternalId);
//		}
//	}

}
