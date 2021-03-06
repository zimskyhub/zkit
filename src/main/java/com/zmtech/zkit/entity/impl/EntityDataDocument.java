package com.zmtech.zkit.entity.impl;

import com.google.common.collect.Lists;
import com.zmtech.zkit.context.impl.ExecutionContextImpl;
import com.zmtech.zkit.entity.*;
import com.zmtech.zkit.entity.impl.condition.impl.ConditionAlias;
import com.zmtech.zkit.entity.impl.condition.impl.ConditionField;
import com.zmtech.zkit.entity.impl.condition.impl.FieldValueCondition;
import com.zmtech.zkit.exception.EntityException;
import com.zmtech.zkit.l10n.impl.L10nFacadeImpl;
import com.zmtech.zkit.util.CollectionUtil;
import com.zmtech.zkit.util.EntityJavaUtil;
import com.zmtech.zkit.util.MNode;
import org.apache.groovy.util.Maps;
import groovy.json.JsonOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class EntityDataDocument {
    protected final static Logger logger = LoggerFactory.getLogger(EntityDataDocument.class);

    protected final EntityFacadeImpl efi;

    public EntityDataDocument(EntityFacadeImpl efi) {
        this.efi = efi;
    }

    public int writeDocumentsToFile(String filename, List<String> dataDocumentIds, EntityCondition condition,
                             Timestamp fromUpdateStamp, Timestamp thruUpdatedStamp, boolean prettyPrint) {
        File outFile = new File(filename);

        if(outFile.exists()){
            efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件写入错误: 文件 [${filename}] 已经存在!","",Collections.singletonMap("filename",filename)));
            return 0;
        }
        try {
            outFile.createNewFile();
            PrintWriter pw = new PrintWriter(outFile);
            pw.write("[\n");
            int valuesWritten = writeDocumentsToWriter(pw, dataDocumentIds, condition, fromUpdateStamp, thruUpdatedStamp, prettyPrint);
            pw.write("{}\n]\n");
            pw.close();
            efi.ecfi.getEci().getMessage().addMessage(efi.ecfi.getResource().expand("文件写入成功: 已写入 ${valuesWritten} 文档到文件 ${filename} 中!","", Maps.of("valuesWritten",valuesWritten,"filename",filename)));
            return valuesWritten;
        } catch (IOException e) {
            efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件写入错误: 文件无法 ${filename} 无法创建.错误 ${error}","", Maps.of("filename",filename,"error",e.toString())));
            return 0;
        }
    }

    public int writeDocumentsToDirectory(String dirname, List<String> dataDocumentIds, EntityCondition condition,
                                  Timestamp fromUpdateStamp, Timestamp thruUpdatedStamp, boolean prettyPrint) {
        File outDir = new File(dirname);
        if (!outDir.exists()) outDir.mkdir();
        if (!outDir.isDirectory()) {
            efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件夹写入错误: 路径 ${dirname} 并不是文件夹.","",Maps.of()new HashMap<String,Object>(){{
                put("dirname",dirname);
            }}));
            return 0;
        }

        int valuesWritten = 0;

        for (String dataDocumentId : dataDocumentIds) {
            String filename = dirname+"/"+dataDocumentId+".json";
            try {
                File outFile = new File(filename);
                if (outFile.exists()) {
                    efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件写入错误: 文件 ${filename} 已经存储, 文档 ${dataDocumentId} 跳过存储.","",new HashMap<String,Object>(){{
                        put("filename",filename);
                        put("dataDocumentId",dataDocumentId);
                    }}));
                    continue;
                }
                outFile.createNewFile();


                PrintWriter pw = new PrintWriter(outFile);
                pw.write("[\n");
                valuesWritten += writeDocumentsToWriter(pw, Collections.singletonList(dataDocumentId), condition, fromUpdateStamp, thruUpdatedStamp, prettyPrint);
                pw.write("{}\n]\n");
                pw.close();

                int finalValuesWritten = valuesWritten;
                efi.ecfi.getEci().getMessage().addMessage(efi.ecfi.getResource().expand("已写入 ${valuesWritten} 条记录到文件 ${filename} 中","",new HashMap<String,Object>(){{
                    put("valuesWritten", finalValuesWritten);
                    put("filename",filename);
                }}));
            } catch (IOException e) {
                efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件写入错误: 文件无法 ${filename} 无法创建.错误 ${error}","",new HashMap<String,Object>(){{
                    put("filename",filename);
                    put("error",e.toString());
                }}));
            }
        }
        return valuesWritten;
    }

    public int writeDocumentsToWriter(Writer pw, List<String> dataDocumentIds, EntityCondition condition,
                               Timestamp fromUpdateStamp, Timestamp thruUpdatedStamp, boolean prettyPrint) {
        if (dataDocumentIds == null || dataDocumentIds.size() == 0) return 0;
        int valuesWritten = 0;

        try {
            for (String dataDocumentId : dataDocumentIds) {
                ArrayList<Map<String,Object>> documentList = getDataDocuments(dataDocumentId, condition, fromUpdateStamp, thruUpdatedStamp);
                int docListSize = documentList.size();
                for (int i = 0; i < docListSize; i++) {
                    if (valuesWritten > 0) pw.write(",\n");
                    Map document = documentList.get(i);
                    String json = JsonOutput.toJson(document);
                    if (prettyPrint) {
                        pw.write(JsonOutput.prettyPrint(json));
                    } else {
                        pw.write(json);
                    }
                    valuesWritten++;
                }
            }
            if (valuesWritten > 0) pw.write("\n");
        } catch (IOException e) {
            efi.ecfi.getEci().getMessage().addError(efi.ecfi.getResource().expand("文件写入错误: 文件无法 ${filename} 无法创建.错误 ${error}","",new HashMap<String,Object>(){{
                put("filename",filename);
                put("error",e.toString());
            }}));
        }

        return valuesWritten;
    }

    public static class DataDocumentInfo {
        public String dataDocumentId;
        public EntityValue dataDocument;
        public EntityList dataDocumentFieldList;
        public String primaryEntityName;
        public EntityDefinition primaryEd;
        public ArrayList<String> primaryPkFieldNames;
        public Map<String, Object> fieldTree = new ConcurrentHashMap<>();
        public Map<String, String> fieldAliasPathMap = new ConcurrentHashMap<>();
        public boolean hasExpressionField = false;
        public EntityDefinition entityDef;

        public DataDocumentInfo(String dataDocumentId, EntityFacadeImpl efi) {
            this.dataDocumentId = dataDocumentId;

            dataDocument = efi.fastFindOne("moqui.entity.document.DataDocument", true, false, dataDocumentId);
            if (dataDocument == null) throw new EntityException("文档查询错误: 没有找到ID为 [${dataDocumentId}] 的文档");
            dataDocumentFieldList = dataDocument.findRelated("moqui.entity.document.DataDocumentField", null, Lists.newArrayList("sequenceNum", "fieldPath"), true, false)

            primaryEntityName = (String) dataDocument.getNoCheckSimple("primaryEntityName");
            primaryEd = efi.getEntityDefinition(primaryEntityName);
            primaryPkFieldNames = primaryEd.getPkFieldNames();

            AtomicBoolean hasExprMut = new AtomicBoolean(false);
            populateFieldTreeAndAliasPathMap(dataDocumentFieldList, primaryPkFieldNames, fieldTree, fieldAliasPathMap, hasExprMut, false);
            hasExpressionField = hasExprMut.get();

            EntityDynamicViewImpl dynamicView = new EntityDynamicViewImpl(efi);
            dynamicView.entityNode.getAttributes().put("package", "DataDocument");
            dynamicView.entityNode.getAttributes().put("entity-name", dataDocumentId);

            // add member entities and field aliases to dynamic view
            dynamicView.addMemberEntity("PRIM", primaryEntityName, null, null, null);
            AtomicInteger incrementer = new AtomicInteger();
            fieldTree.put("_ALIAS", "PRIM");
            addDataDocRelatedEntity(dynamicView, "PRIM", fieldTree, incrementer, makeDdfByAlias(dataDocumentFieldList));
            // logger.warn("=========== ${dataDocumentId} fieldTree=${fieldTree}")
            // logger.warn("=========== ${dataDocumentId} fieldAliasPathMap=${fieldAliasPathMap}")

            entityDef = dynamicView.makeEntityDefinition();
        }
    }

    public EntityDefinition makeEntityDefinition(String dataDocumentId) {
        DataDocumentInfo ddi = new DataDocumentInfo(dataDocumentId, efi);
        return ddi.entityDef;
    }

    public EntityFind makeDataDocumentFind(String dataDocumentId) {
        DataDocumentInfo ddi = new DataDocumentInfo(dataDocumentId, efi)
        EntityList dataDocumentConditionList = ddi.dataDocument.findRelated("moqui.entity.document.DataDocumentCondition", null, null, true, false);
        return makeDataDocumentFind(ddi, dataDocumentConditionList, null, null);
    }

    public EntityFind makeDataDocumentFind(DataDocumentInfo ddi, EntityList dataDocumentConditionList,
                                    Timestamp fromUpdateStamp, Timestamp thruUpdatedStamp) {
        // build the query condition for the primary entity and all related entities
        EntityDefinition ed = ddi.entityDef;
        EntityFind mainFind = ed.makeEntityFind();

        // add conditions
        if (dataDocumentConditionList != null && dataDocumentConditionList.size() > 0) {
            ExecutionContextImpl eci = efi.ecfi.getEci();
            for (EntityValue dataDocumentCondition : dataDocumentConditionList) {
                String fieldAlias = (String) dataDocumentCondition.getNoCheckSimple("fieldNameAlias");
                FieldInfo fi = ed.getFieldInfo(fieldAlias);
                if (fi == null) throw new EntityException("Found DataDocument Condition with alias [${fieldAlias}] that is not aliased in DataDocument ${ddi.dataDocumentId}");
                if (dataDocumentCondition.getNoCheckSimple("postQuery").equals("Y") ) {
                    String operator = dataDocumentCondition.getNoCheckSimple("operator") != null ? (String) dataDocumentCondition.getNoCheckSimple("operator"): "equals";
                    String toFieldAlias = (String) dataDocumentCondition.getNoCheckSimple("toFieldNameAlias");
                    if (toFieldAlias != null && !toFieldAlias.isEmpty()) {
                        mainFind.conditionToField(fieldAlias, EntityConditionFactoryImpl.stringComparisonOperatorMap.get(operator), toFieldAlias);
                    } else {
                        String stringVal = (String) dataDocumentCondition.getNoCheckSimple("fieldValue");
                        Object objVal = fi.convertFromString(stringVal, (L10nFacadeImpl) eci.getL10n());
                        mainFind.condition(fieldAlias, operator, objVal);
                    }
                }
            }
        }

        // create a condition with an OR list of date range comparisons to check that at least one member-entity has lastUpdatedStamp in range
        if (fromUpdateStamp != null || thruUpdatedStamp != null) {
            List<EntityCondition> dateRangeOrCondList = new ArrayList<>();
            for (MNode memberEntityNode : ed.getEntityNode().children("member-entity")) {
                ConditionField ludCf = new ConditionAlias(memberEntityNode.attribute("entity-alias"),
                        "lastUpdatedStamp", efi.getEntityDefinition(memberEntityNode.attribute("entity-name")));
                List<EntityCondition> dateRangeFieldCondList = new ArrayList<>();
                if ((Object) fromUpdateStamp != null) {
                    dateRangeFieldCondList.add(efi.getConditionFactory().makeCondition(
                            new FieldValueCondition(ludCf, EntityCondition.EQUALS, null),
                            EntityCondition.OR,
                            new FieldValueCondition(ludCf, EntityCondition.GREATER_THAN_EQUAL_TO, fromUpdateStamp)));
                }
                if ((Object) thruUpdatedStamp != null) {
                    dateRangeFieldCondList.add(efi.getConditionFactory().makeCondition(
                            new FieldValueCondition(ludCf, EntityCondition.EQUALS, null),
                            EntityCondition.OR,
                            new FieldValueCondition(ludCf, EntityCondition.LESS_THAN, thruUpdatedStamp)));
                }
                dateRangeOrCondList.add(efi.getConditionFactory().makeCondition(dateRangeFieldCondList, EntityCondition.AND));
            }
            mainFind.condition(efi.getConditionFactory().makeCondition(dateRangeOrCondList, EntityCondition.OR));
        }

        // logger.warn("=========== DataDocument query condition for ${dataDocumentId} mainFind.condition=${((EntityFindImpl) mainFind).getWhereEntityCondition()}\n${mainFind.toString()}")
        return mainFind
    }

    public ArrayList<Map<String, Object>> getDataDocuments(String dataDocumentId, EntityCondition condition, Timestamp fromUpdateStamp, Timestamp thruUpdatedStamp) {
        ExecutionContextImpl eci = efi.ecfi.getEci();

        DataDocumentInfo ddi = new DataDocumentInfo(dataDocumentId, efi);
        EntityList dataDocumentRelAliasList = ddi.dataDocument.findRelated("moqui.entity.document.DataDocumentRelAlias", null, null, true, false);
        EntityList dataDocumentConditionList = ddi.dataDocument.findRelated("moqui.entity.document.DataDocumentCondition", null, null, true, false);

        // make the relationship alias Map
        Map<String,Object> relationshipAliasMap = new HashMap<>();
        for (EntityValue dataDocumentRelAlias : dataDocumentRelAliasList)
        relationshipAliasMap.put((String)dataDocumentRelAlias.getNoCheckSimple("relationshipName"), dataDocumentRelAlias.getNoCheckSimple("documentAlias"));

        EntityFind mainFind = makeDataDocumentFind(ddi, dataDocumentConditionList, fromUpdateStamp, thruUpdatedStamp);
        if (condition != null) mainFind.condition(condition);

        boolean hasAllPrimaryPks = true;
        for (String pkFieldName : ddi.primaryPkFieldNames) if (!ddi.fieldAliasPathMap.containsKey(pkFieldName)) hasAllPrimaryPks = false;

        // do the one big query
        EntityListIterator mainEli = mainFind.iterator();
        Map<String, Map<String, Object>> documentMapMap = hasAllPrimaryPks ? new LinkedHashMap<>() : null;
        ArrayList<Map<String, Object>> documentMapList = hasAllPrimaryPks ? null : new ArrayList<>();
        try {
            EntityValue ev;
            while ((ev = (EntityValue) mainEli.next()) != null) {
                // logger.warn("=========== DataDocument query result for ${dataDocumentId}: ${ev}")

                StringBuffer pkCombinedSb = new StringBuffer();
                for (String pkFieldName : ddi.primaryPkFieldNames) {
                    if (!ddi.fieldAliasPathMap.containsKey(pkFieldName)) continue;
                    if (pkCombinedSb.length() > 0) pkCombinedSb.append("::");
                    Object pkFieldValue = ev.getNoCheckSimple(pkFieldName);
                    if (pkFieldValue instanceof Timestamp) pkFieldValue = ((Timestamp) pkFieldValue).getTime();
                    pkCombinedSb.append(pkFieldValue.toString());
                }
                String docId = pkCombinedSb.toString();

                /*
                  - _index = DataDocument.indexName
                  - _type = dataDocumentId
                  - _id = pk field values from primary entity, double colon separated
                  - _timestamp = document created time
                  - Map for primary entity with primaryEntityName as key
                  - nested List of Maps for each related entity with aliased fields with relationship name as key
                 */
                Map<String, Object> docMap = hasAllPrimaryPks ? documentMapMap.get(docId) : null;
                if (docMap == null) {
                    // add special entries
                    docMap = new LinkedHashMap<>();
                    docMap.put("_type", dataDocumentId);
                    docMap.put("_id", docId);
                    docMap.put("_timestamp", eci.getL10n().format(
                            thruUpdatedStamp != null ? thruUpdatedStamp: new Timestamp(System.currentTimeMillis()), "yyyy-MM-dd'T'HH:mm:ssZ"));
                    String _index = ddi.dataDocument.getString("indexName");
                    if (_index != null) docMap.put("_index", _index.toLowerCase());
                    docMap.put("_entity", ddi.primaryEd.getShortOrFullEntityName());

                    // add Map for primary entity
                    Map<String,Object> primaryEntityMap = new HashMap<>();
                    for (Map.Entry<String, Object> fieldTreeEntry : ddi.fieldTree.entrySet()) {
                        Object entryValue = fieldTreeEntry.getValue();
                        // if ("_ALIAS".equals(fieldTreeEntry.getKey())) continue
                        if (entryValue instanceof ArrayList) {
                            String fieldEntryKey = fieldTreeEntry.getKey();
                            if (fieldEntryKey.startsWith("(")) continue;
                            ArrayList<String> fieldAliasList = ( ArrayList<String>)entryValue;
                            for (int i = 0; i < fieldAliasList.size(); i++) {
                                String fieldAlias = (String) fieldAliasList.get(i);
                                Object curVal = ev.get(fieldAlias);
                                if (curVal != null) primaryEntityMap.put(fieldAlias, curVal);
                            }
                        }
                    }
                    // docMap.put((String) relationshipAliasMap.get(primaryEntityName) ?: primaryEntityName, primaryEntityMap)
                    docMap.putAll(primaryEntityMap);

                    if (hasAllPrimaryPks) documentMapMap.put(docId, docMap);
                    else documentMapList.add(docMap);
                }

                // recursively add Map or List of Maps for each related entity
                populateDataDocRelatedMap(ev, docMap, ddi.primaryEd, ddi.fieldTree, relationshipAliasMap, false);
            }
        } finally {
            mainEli.close();
        }

        // make the actual list and return it
        if (hasAllPrimaryPks) {
            documentMapList = new ArrayList<>(documentMapMap.size());
            documentMapList.addAll(documentMapMap.values());
        }
        String manualDataServiceName = (String) ddi.dataDocument.getNoCheckSimple("manualDataServiceName");
        for (int i = 0; i < documentMapList.size(); ) {
            Map<String, Object> docMap = documentMapList.get(i);
            // call the manualDataServiceName service for each document
            if (manualDataServiceName != null && !manualDataServiceName.isEmpty()) {
                // logger.warn("Calling ${manualDataServiceName} with doc: ${docMap}")
//                Map result = efi.ecfi.serviceFacade.sync().name(manualDataServiceName)
//                        .parameter("dataDocumentId", dataDocumentId).parameter("document", docMap).call()
//                Map outDoc = (Map<String, Object>) result.get("document")
//                if (outDoc != null && outDoc.size() > 0) {
//                    docMap = outDoc;
//                    documentMapList.set(i, docMap);
//                }
            }

            // evaluate expression fields
            if (ddi.hasExpressionField) {
                runDocExpressions(docMap, null, ddi.primaryEd, ddi.fieldTree, relationshipAliasMap);
            }

            // check postQuery conditions
            boolean allPassed = true;
            for (EntityValue dataDocumentCondition : dataDocumentConditionList) if ("Y".equals(dataDocumentCondition.getString("postQuery"))) {
                Set<Object> valueSet = new HashSet<>();
                CollectionUtil.findAllFieldsNestedMap((String) dataDocumentCondition.getNoCheckSimple("fieldNameAlias"), docMap, valueSet);
                if (valueSet.size() == 0) {
                    if (dataDocumentCondition.getNoCheckSimple("fieldValue") == null) { continue; }
                    else { allPassed = false; break; }
                }
                if (dataDocumentCondition.getNoCheckSimple("fieldValue") == null) { allPassed = false; break; }
                Object fieldValueObj = dataDocumentCondition.getNoCheckSimple("fieldValue").asType(valueSet.first().class)
                if (!(valueSet.contains(fieldValueObj))) { allPassed = false; break; }
            }

            if (allPassed) { i++; } else { documentMapList.remove(i); }
        }

        return documentMapList;
    }

    public static ArrayList<String> fieldPathToList(String fieldPath) {
        int openParenIdx = fieldPath.indexOf("(");
        ArrayList<String> fieldPathElementList = new ArrayList<>();
        if (openParenIdx == -1) {
            Collections.addAll(fieldPathElementList, fieldPath.split(":"));
        } else {
            if (openParenIdx > 0) {
                // should end with a colon so subtract 1
                String preParen = fieldPath.substring(0, openParenIdx - 1);
                Collections.addAll(fieldPathElementList, preParen.split(":"));
                fieldPathElementList.add(fieldPath.substring(openParenIdx));
            } else {
                fieldPathElementList.add(fieldPath);
            }
        }
        return fieldPathElementList;
    }
    public static void populateFieldTreeAndAliasPathMap(EntityList dataDocumentFieldList, List<String> primaryPkFieldNames,
                                                 Map<String, Object> fieldTree, Map<String, String> fieldAliasPathMap, AtomicBoolean hasExprMut, boolean allPks) {
        for (EntityValue dataDocumentField : dataDocumentFieldList) {
            String fieldPath = (String) dataDocumentField.getNoCheckSimple("fieldPath");
            ArrayList<String> fieldPathElementList = fieldPathToList(fieldPath);
            Map currentTree = fieldTree;
            int fieldPathElementListSize = fieldPathElementList.size();
            for (int i = 0; i < fieldPathElementListSize; i++) {
                String fieldPathElement =fieldPathElementList.get(i);
                if (i < (fieldPathElementListSize - 1)) {
                    Map subTree = (Map) currentTree.get(fieldPathElement);
                    if (subTree == null) { subTree = new HashMap<>(); currentTree.put(fieldPathElement, subTree); }
                    currentTree = subTree;
                } else {
                    String fieldAlias = dataDocumentField.getNoCheckSimple("fieldNameAlias") != null ?(String) dataDocumentField.getNoCheckSimple("fieldNameAlias"): fieldPathElement;
                    CollectionUtil.addToListInMap(fieldPathElement, fieldAlias, currentTree);
                    fieldAliasPathMap.put(fieldAlias, fieldPath);
                    if (fieldPathElement.startsWith("(")) hasExprMut.set(true);
                }
            }
        }
        // make sure all PK fields of the primary entity are aliased
        if (allPks) {
            for (String pkFieldName : primaryPkFieldNames) if (!fieldAliasPathMap.containsKey(pkFieldName)) {
                fieldTree.put(pkFieldName, pkFieldName);
                fieldAliasPathMap.put(pkFieldName, pkFieldName);
            }
        }
    }

    protected void runDocExpressions(Map<String, Object> curDocMap, Map<String, Object> parentsMap, EntityDefinition parentEd,
                                     Map<String, Object> fieldTreeCurrent, Map relationshipAliasMap) {
        for (Map.Entry<String, Object> fieldTreeEntry : fieldTreeCurrent.entrySet()) {
            String fieldEntryKey = fieldTreeEntry.getKey();
            Object fieldEntryValue = fieldTreeEntry.getValue();
            if (fieldEntryValue instanceof Map) {
                String relationshipName = fieldEntryKey;
                Map<String, Object> fieldTreeChild = (Map) fieldEntryValue;

                EntityJavaUtil.RelationshipInfo relationshipInfo = parentEd.getRelationshipInfo(relationshipName);
                String relDocumentAlias = relationshipAliasMap.get(relationshipName) != null ? (String)relationshipAliasMap.get(relationshipName): relationshipInfo.shortAlias != null? relationshipInfo.shortAlias: relationshipName;
                EntityDefinition relatedEd = relationshipInfo.relatedEd;
                boolean isOneRelationship = relationshipInfo.isTypeOne;

                if (isOneRelationship) {
                    runDocExpressions(curDocMap, parentsMap, relatedEd, fieldTreeChild, relationshipAliasMap);
                    List<Map> relatedEntityDocList = (List<Map>) curDocMap.get(relDocumentAlias);
                    if (relatedEntityDocList != null) for (Map childMap : relatedEntityDocList) {
                        Map<String, Object> newParentsMap = new HashMap<>();
                        if (parentsMap != null) parentsMap.putAll(parentsMap);
                        newParentsMap.putAll(curDocMap);
                        runDocExpressions(childMap, newParentsMap, relatedEd, fieldTreeChild, relationshipAliasMap);
                    }
                }
            } else if (fieldEntryValue instanceof ArrayList) {
                if (fieldEntryKey.startsWith("(")) {
                    // run expression to get value, set for all aliases (though will always be one)
                    Map<String, Object> evalMap = new HashMap<>();
                    if (parentsMap != null) evalMap.putAll(parentsMap);
                    evalMap.putAll(curDocMap);
                    try {
                        Object curVal = efi.ecfi.getResource().expression(fieldEntryKey, null, evalMap);
                        if (curVal != null) {
                            ArrayList<String> fieldAliasList = (ArrayList<String>) fieldEntryValue;
                            for (int i = 0; i < fieldAliasList.size(); i++) {
                                String fieldAlias = (String) fieldAliasList.get(i);
                                if (curVal != null) curDocMap.put(fieldAlias, curVal);
                            }
                        }
                    } catch (Throwable t) {
                        logger.error("Error evaluating DataDocumentField expression: ${fieldEntryKey}", t);
                    }
                }
            }
        }
    }

    protected void populateDataDocRelatedMap(EntityValue ev, Map<String, Object> parentDocMap, EntityDefinition parentEd,
                                             Map<String, Object> fieldTreeCurrent, Map relationshipAliasMap, boolean setFields) {
        for (Map.Entry<String, Object> fieldTreeEntry : fieldTreeCurrent.entrySet()) {
            String fieldEntryKey = fieldTreeEntry.getKey();
            Object fieldEntryValue = fieldTreeEntry.getValue();
            // if ("_ALIAS".equals(fieldEntryKey)) continue
            if (fieldEntryValue instanceof Map) {
                String relationshipName = fieldEntryKey;
                Map<String, Object> fieldTreeChild = (Map<String, Object>) fieldEntryValue;

                EntityJavaUtil.RelationshipInfo relationshipInfo = parentEd.getRelationshipInfo(relationshipName);
                String relDocumentAlias = relationshipAliasMap.get(relationshipName) != null ? (String)relationshipAliasMap.get(relationshipName) : relationshipInfo.shortAlias !=null ? relationshipInfo.shortAlias: relationshipName;
                EntityDefinition relatedEd = relationshipInfo.relatedEd;
                boolean isOneRelationship = relationshipInfo.isTypeOne;

                if (isOneRelationship) {
                    // we only need a single Map
                    populateDataDocRelatedMap(ev, parentDocMap, relatedEd, fieldTreeChild, relationshipAliasMap, true);
                } else {
                    // we need a List of Maps
                    Map relatedEntityDocMap = null;

                    // see if there is a Map in the List in the matching entry
                    List<Map> relatedEntityDocList = (List<Map>) parentDocMap.get(relDocumentAlias);
                    if (relatedEntityDocList != null) {
                        for (Map candidateMap : relatedEntityDocList) {
                            boolean allMatch = true;
                            for (Map.Entry<String, Object> fieldTreeChildEntry : fieldTreeChild.entrySet()) {
                                Object entryValue = fieldTreeChildEntry.getValue();
                                if (entryValue instanceof ArrayList && !fieldTreeChildEntry.getKey().startsWith("(")) {
                                    ArrayList<String> fieldAliasList = (ArrayList<String>) entryValue;
                                    for (int i = 0; i < fieldAliasList.size(); i++) {
                                        String fieldAlias = fieldAliasList.get(i);
                                        if (candidateMap.get(fieldAlias) != ev.get(fieldAlias)) {
                                            allMatch = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (allMatch) {
                                relatedEntityDocMap = candidateMap;
                                break
                            }
                        }
                    }

                    if (relatedEntityDocMap == null) {
                        // no matching Map? create a new one... and it will get populated in the recursive call
                        relatedEntityDocMap = new HashMap();
                        // now time to recurse
                        populateDataDocRelatedMap(ev, relatedEntityDocMap, relatedEd, fieldTreeChild, relationshipAliasMap, true);
                        if (!relatedEntityDocMap.isEmpty()) {
                            if (relatedEntityDocList == null) {
                                relatedEntityDocList = new ArrayList<>();
                                parentDocMap.put(relDocumentAlias, relatedEntityDocList);
                            }
                            relatedEntityDocList.add(relatedEntityDocMap);
                        }
                    } else {
                        // now time to recurse
                        populateDataDocRelatedMap(ev, relatedEntityDocMap, relatedEd, fieldTreeChild, relationshipAliasMap, false);
                    }
                }
            } else if (fieldEntryValue instanceof ArrayList) {
                if (setFields && !fieldEntryKey.startsWith("(")) {
                    // set the field(s)
                    ArrayList<String> fieldAliasList = (ArrayList<String>) fieldEntryValue;
                    for (int i = 0; i < fieldAliasList.size(); i++) {
                        String fieldAlias = fieldAliasList.get(i);
                        Object curVal = ev.get(fieldAlias);
                        if (curVal != null) parentDocMap.put(fieldAlias, curVal);
                    }
                }
            }
        }
    }

    private static Map<String, EntityValue> makeDdfByAlias(EntityList dataDocumentFieldList) {
        Map<String, EntityValue> ddfByAlias = new HashMap<>();
        int ddfSize = dataDocumentFieldList.size();
        for (int i = 0; i < ddfSize; i++) {
            EntityValue ddf = dataDocumentFieldList.get(i);
            String alias = (String) ddf.getNoCheckSimple("fieldNameAlias");
            if (alias == null || alias.isEmpty()) {
                String fieldPath = (String) ddf.getNoCheckSimple("fieldPath");
                ArrayList<String> fieldPathElementList = fieldPathToList(fieldPath);
                alias = (String) fieldPathElementList.get(fieldPathElementList.size() - 1);
            }
            ddfByAlias.put(alias, ddf);
        }
        return ddfByAlias;
    }
    private static void addDataDocRelatedEntity(EntityDynamicViewImpl dynamicView, String parentEntityAlias,
                                                Map<String, Object> fieldTreeCurrent, AtomicInteger incrementer, Map<String, EntityValue> ddfByAlias) {
        for (Map.Entry fieldTreeEntry : fieldTreeCurrent.entrySet()) {
            String fieldEntryKey = (String) fieldTreeEntry.getKey();
            if ("_ALIAS".equals(fieldEntryKey)) continue;

            Object entryValue = fieldTreeEntry.getValue();
            if (entryValue instanceof Map) {
                Map fieldTreeChild = (Map) entryValue;
                // add member entity, and entity alias in "_ALIAS" entry
                String entityAlias = "MBR" + incrementer.getAndIncrement();
                dynamicView.addRelationshipMember(entityAlias, parentEntityAlias, fieldEntryKey, true);
                fieldTreeChild.put("_ALIAS", entityAlias);
                // now time to recurse
                addDataDocRelatedEntity(dynamicView, entityAlias, fieldTreeChild, incrementer, ddfByAlias);
            } else if (entryValue instanceof ArrayList) {
                // add alias for field
                String entityAlias = fieldTreeCurrent.get("_ALIAS")
                ArrayList<String> fieldAliasList = (ArrayList<String>) entryValue;
                for (int i = 0; i < fieldAliasList.size(); i++) {
                    String fieldAlias = fieldAliasList.get(i);
                    EntityValue ddf = ddfByAlias.get(fieldAlias);
                    if (ddf == null) throw new EntityException("Could not find DataDocumentField for field alias ${fieldEntryKey}");
                    String defaultDisplay = (String)ddf.getNoCheckSimple("defaultDisplay");

                    if (fieldEntryKey.startsWith("(")) {
                        // handle expressions differently, expressions have to be meant for this but nice for various cases
                        // TODO: somehow specify type, yet another new field on DataDocumentField entity? for now defaulting to 'text-long'
                        dynamicView.addPqExprAlias(fieldAlias, fieldEntryKey, "text-long",
                                "N".equals(defaultDisplay) ? "false" : ("Y".equals(defaultDisplay) ? "true" : null));
                    } else {
                        dynamicView.addAlias(entityAlias, fieldAlias, fieldEntryKey, (String) ddf.getNoCheckSimple("functionName"),
                                "N".equals(defaultDisplay) ? "false" : ("Y".equals(defaultDisplay) ? "true" : null));
                    }
                }
            }
        }
    }
}
