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

import org.entcore.feeder.utils.CSVUtil;
import org.entcore.feeder.utils.ResultMessage;
import org.entcore.feeder.utils.Validator;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static fr.wseduc.webutils.Utils.isEmpty;

public class ProfileColumnsMapper {

	private final Map<String, Map<String, Object>> profilesNamesMapping = new HashMap<>();

	public ProfileColumnsMapper() {
		defaultInit();
	}

	private void defaultInit() {
		JsonObject baseMappings = new JsonObject()
				.putString("id", "externalId")
				.putString("externalid", "externalId")
				.putString("nom", "lastName")
				.putString("nomusage", "username")
				.putString("nomdusage", "username")
				.putString("nomusage", "surname")
				.putString("prenom", "firstName")
				.putString("classe", "classes")
				.putString("libelleclasse", "classes")
				.putString("classeouregroupement", "classes")
				.putString("idenfant", "childExternalId")
				.putString("datedenaissance", "birthDate")
				.putString("datenaissance", "birthDate")
				.putString("neele", "birthDate")
				.putString("ne(e)le", "birthDate")
				.putString("childid", "childExternalId")
				.putString("childexternalid", "childExternalId")
				.putString("nomenfant", "childLastName")
				.putString("prenomenfant", "childFirstName")
				.putString("classeenfant", "childClasses")
				.putString("nomdusageenfant", "childUsername")
				.putString("nomdefamilleenfant", "childLastName")
				.putString("classesenfants", "childClasses")
				.putString("presencedevanteleves", "teaches")
				.putString("fonction", "functions")
				.putString("niveau", "level")
				.putString("regime", "accommodation")
				.putString("filiere", "sector")
				.putString("cycle", "sector")
				.putString("mef", "module")
				.putString("libellemef", "moduleName")
				.putString("boursier", "scholarshipHolder")
				.putString("transport", "transport")
				.putString("statut", "status")
				.putString("codematiere", "fieldOfStudy")
				.putString("matiere", "fieldOfStudyLabels")
				.putString("persreleleve", "relative")
				.putString("datedenaissance", "birthDate")
				.putString("datenaissance", "birthDate")
				.putString("civilite", "title")
				.putString("telephone", "homePhone")
				.putString("telephonedomicile", "homePhone")
				.putString("telephonetravail", "workPhone")
				.putString("telephoneportable", "mobile")
				.putString("adresse", "address")
				.putString("adresse1", "address")
				.putString("adresse2", "address2")
				.putString("cp", "zipCode")
				.putString("cp1", "zipCode")
				.putString("cp2", "zipCode2")
				.putString("ville", "city")
				.putString("commune", "city")
				.putString("commune1", "city")
				.putString("commune2", "city2")
				.putString("pays", "country")
				.putString("pays1", "country")
				.putString("pays2", "country2")
				.putString("email", "email")
				.putString("courriel", "email")
				.putString("sexe", "gender")
				.putString("ine", "ine")
				.putString("identifiantclasse", "ignore")
				.putString("dateinscription", "ignore")
				.putString("deuxiemeprenom", "ignore")
				.putString("troisiemeprenom", "ignore")
				.putString("communenaissance", "ignore")
				.putString("deptnaissance", "ignore")
				.putString("paysnaissance", "ignore")
				.putString("etat", "ignore")
				.putString("intervenant", "ignore")
				.putString("ignore", "ignore");
		JsonObject studentMappings = baseMappings.copy()
				.putString("nomeleve", "lastName")
				.putString("nomdusageeleve", "surname")
				.putString("prenomeleve", "firstName")
				.putString("niveau", "level")
				.putString("regime", "accommodation")
				.putString("filiere", "sector")
				.putString("cycle", "sector")
				.putString("attestationfournie", "ignore")
				.putString("autorisationsassociations", "ignore")
				.putString("autorisationassociations", "ignore")
				.putString("autorisationsphotos", "ignore")
				.putString("autorisationphoto", "ignore")
				.putString("decisiondepassage", "ignore")
				.putString("boursier", "scholarshipHolder")
				.putString("transport", "transport")
				.putString("statut", "status")
				.putString("persreleleve", "relative");
		JsonObject relativeMapping = baseMappings.copy()
				.putString("nomresponsable", "lastName")
				.putString("nomusageresponsable", "surname")
				.putString("prenomresponsable", "firstName")
				.putString("idenfant", "childExternalId")
				.putString("childid", "childExternalId")
				.putString("childexternalid", "childExternalId")
				.putString("nomenfant", "childLastName")
				.putString("prenomenfant", "childFirstName")
				.putString("classeenfant", "childClasses")
				.putString("nomdusageenfant", "childUsername")
				.putString("nomdefamilleenfant", "childLastName")
				.putString("classesenfants", "childClasses")
				.putString("civiliteresponsable", "title")
				.putString("adresseresponsable", "address")
				.putString("cpresponsable", "zipCode")
				.putString("communeresponsable", "city");
		JsonObject teacherMapping = baseMappings.copy()
				.putString("presencedevanteleves", "teaches")
				.putString("fonction", "functions")
				.putString("mef", "module")
				.putString("libellemef", "moduleName")
				.putString("codematiere", "fieldOfStudy")
				.putString("matiere", "fieldOfStudyLabels")
				.putString("professeurprincipal", "headTeacher")
				.putString("discipline", "classCategories")
				.putString("matiereenseignee", "subjectTaught")
				.putString("directeur", "ignore");
		profilesNamesMapping.put("Teacher", teacherMapping.toMap());
		profilesNamesMapping.put("Personnel", teacherMapping.toMap());
		profilesNamesMapping.put("Student", studentMappings.toMap());
		profilesNamesMapping.put("Relative", relativeMapping.toMap());
		profilesNamesMapping.put("Guest", baseMappings.toMap());
	}

	public ProfileColumnsMapper(JsonObject mapping) {
		if (mapping == null || mapping.size() == 0) {
			defaultInit();
		} else {
			for (String profile: mapping.getFieldNames()) {
				final JsonObject m = mapping.getObject(profile);
				if (m != null) {
					JsonObject j = new JsonObject().putString("externalid", "externalId");
					for (String attr : m.getFieldNames()) {
						j.putString(cleanKey(attr), m.getString(attr));
					}
					profilesNamesMapping.put(profile, j.toMap());
				}
			}
		}
	}

	void getColumsNames(String profile, String[] strings, List<String> columns, Handler<Message<JsonObject>> handler) {
		for (int j = 0; j < strings.length; j++) {
			String cm = columnsNameMapping(profile, strings[j]);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				try {
					columns.add(j, cm);
				} catch (ArrayIndexOutOfBoundsException e) {
					columns.clear();
					handler.handle(new ResultMessage().error("invalid.column " + cm));
					return;
				}
			} else {
				columns.clear();
				handler.handle(new ResultMessage().error("invalid.column " + cm));
				return;
			}
		}
	}

	JsonArray getColumsNames(String profile, String[] strings, List<String> columns) {
		JsonArray errors = new JsonArray();
		for (int j = 0; j < strings.length; j++) {
			String cm = columnsNameMapping(profile, strings[j]);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				columns.add(j, cm);
			} else {
				errors.add(cm);
				return errors;
			}
		}
		return errors;
	}

	String columnsNameMapping(String profile, String columnName) {
		final String key = cleanKey(columnName);
		final Object attr = profilesNamesMapping.get(profile).get(key);
		return attr != null ? attr.toString() : key;
	}

	private static String cleanKey(String columnName) {
		return Validator.removeAccents(columnName.trim().toLowerCase())
				.replaceAll("\\s+", "").replaceAll("\\*", "").replaceAll("'", "").replaceFirst(CSVUtil.UTF8_BOM, "");
	}

	public JsonObject getColumsMapping(String profile, String[] strings) {
		JsonObject mapping = new JsonObject();
		for (String key : strings) {
			if (isEmpty(key)) return null;
			String cm = columnsNameMapping(profile, key);
			if (profilesNamesMapping.get(profile).containsValue(cm)) {
				mapping.putString(key, cm);
			} else {
				mapping.putString(key, "");
			}
		}
		return mapping;
	}

	public JsonObject availableFields() {
		JsonObject j = new JsonObject();
		for (String profile : profilesNamesMapping.keySet()) {
			j.putArray(profile, new JsonArray(new HashSet<>(profilesNamesMapping.get(profile).values()).toArray()));
		}
		return j;
	}

}
