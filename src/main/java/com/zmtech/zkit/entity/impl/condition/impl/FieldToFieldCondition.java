package com.zmtech.zkit.entity.impl.condition.impl;

import com.zmtech.zkit.entity.EntityCondition;
import com.zmtech.zkit.entity.impl.EntityConditionFactoryImpl;
import com.zmtech.zkit.entity.impl.EntityDefinition;
import com.zmtech.zkit.entity.impl.EntityQueryBuilder;
import com.zmtech.zkit.entity.impl.FieldInfo;
import com.zmtech.zkit.entity.impl.condition.EntityConditionImplBase;
import com.zmtech.zkit.util.MNode;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Set;

public class FieldToFieldCondition implements EntityConditionImplBase {

    protected static final Class thisClass = FieldValueCondition.class;
    protected ConditionField field;
    protected EntityCondition.ComparisonOperator operator;
    protected ConditionField toField;
    protected boolean ignoreCase = false;
    protected int curHashCode;

    public FieldToFieldCondition(ConditionField field, EntityCondition.ComparisonOperator operator, ConditionField toField) {
        this.field = field;
        this.operator = operator == null ? EQUALS : operator;
        this.toField = toField;
        curHashCode = createHashCode();
    }

    @Override
    public void makeSqlWhere(EntityQueryBuilder eqb, EntityDefinition subMemberEd) {
        StringBuilder sql = eqb.sqlTopLevel;
        EntityDefinition mainEd = eqb.getMainEd();
        FieldInfo fi = field.getFieldInfo(mainEd);
        FieldInfo toFi = toField.getFieldInfo(mainEd);

        int typeValue = -1;
        if (ignoreCase) {
            typeValue = fi != null ? fi.typeValue : 1;
            if (typeValue == 1) sql.append("UPPER(");
        }
        makeSubSql(subMemberEd, sql, mainEd, fi, typeValue, field);

        sql.append(' ').append(EntityConditionFactoryImpl.getComparisonOperatorString(operator)).append(' ');

        int toTypeValue = -1;
        if (ignoreCase) {
            toTypeValue = toField.getFieldInfo(mainEd) != null ? toField.getFieldInfo(mainEd).typeValue : 1;
            if (toTypeValue == 1) sql.append("UPPER(");
        }
        makeSubSql(subMemberEd, sql, mainEd, toFi, toTypeValue, toField);
    }

    private StringBuilder makeSubSql(EntityDefinition subMemberEd, StringBuilder sql, EntityDefinition mainEd, FieldInfo fi, int typeValue, ConditionField field) {
        if (subMemberEd != null) {
            MNode aliasNode = fi.fieldNode;
            String aliasField = aliasNode.attribute("field");
            if (aliasField == null || aliasField.isEmpty()) aliasField = fi.name;
            sql.append(subMemberEd.getColumnName(aliasField));
        } else {
            sql.append(field.getColumnName(mainEd));
        }
        if (ignoreCase && typeValue == 1) sql.append(")");
        return sql;
    }

    @Override
    public boolean mapMatches(Map<String, Object> map) {
        return EntityConditionFactoryImpl.compareByOperator(map.get(field.getFieldName()), operator, map.get(toField.getFieldName()));
    }

    @Override
    public boolean mapMatchesAny(Map<String, Object> map) {
        return mapMatches(map);
    }

    @Override
    public boolean mapKeysNotContained(Map<String, Object> map) {
        return !map.containsKey(field.fieldName) && !map.containsKey(toField.fieldName);
    }

    @Override
    public boolean populateMap(Map<String, Object> map) {
        return false;
    }

    @Override
    public void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) {
        // 只可以在 view entity中使用，因此既可以使用 entity Alias 或 fieldName Alias
        if (field instanceof ConditionAlias) {
            entityAliasSet.add(((ConditionAlias) field).getEntityAlias());
        } else {
            fieldAliasSet.add(field.fieldName);
        }
        if (toField instanceof ConditionAlias) {
            entityAliasSet.add(((ConditionAlias) toField).getEntityAlias());
        } else {
            fieldAliasSet.add(toField.fieldName);
        }
    }

    @Override
    public EntityConditionImplBase filter(String entityAlias, EntityDefinition mainEd) {
        // 只可以在 view entity中使用
        MNode fieldMe = field.getFieldInfo(mainEd).directMemberEntityNode;
        MNode toFieldMe = toField.getFieldInfo(mainEd).directMemberEntityNode;
        if (entityAlias == null) {
            if ((fieldMe != null && "true".equalsIgnoreCase(fieldMe.attribute("sub-select"))) &&
                    (toFieldMe != null && "true".equalsIgnoreCase(toFieldMe.attribute("sub-select"))) &&
                    fieldMe.attribute("entity-alias").equals(toFieldMe.attribute("entity-alias"))) return null;
            return this;
        } else {
            if ((fieldMe != null && entityAlias.equals(fieldMe.attribute("entity-alias"))) &&
                    (toFieldMe != null && entityAlias.equals(toFieldMe.attribute("entity-alias")))) return this;
            return null;
        }
    }

    @Override
    public EntityCondition ignoreCase() {
        ignoreCase = true;
        curHashCode++;
        return this;
    }

    @Override
    public String toString() {
        return field.toString() + " " + EntityConditionFactoryImpl.getComparisonOperatorString(operator) + " " + toField.toString();
    }

    @Override
    public int hashCode() {
        return curHashCode;
    }

    private int createHashCode() {
        return (field != null ? field.hashCode() : 0) + operator.hashCode() + (toField != null ? toField.hashCode() : 0) + (ignoreCase ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || o.getClass() != thisClass) return false;
        FieldToFieldCondition that = (FieldToFieldCondition) o;
        if (!field.equals(that.field)) return false;
        // NOTE: for Java Enums the != is WAY faster than the .equals
        if (operator != that.operator) return false;
        if (!toField.equals(that.toField)) return false;
        return ignoreCase == that.ignoreCase;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        field.writeExternal(out);
        out.writeUTF(operator.name());
        toField.writeExternal(out);
        out.writeBoolean(ignoreCase);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        field = new ConditionField();
        field.readExternal(objectInput);
        operator = EntityCondition.ComparisonOperator.valueOf(objectInput.readUTF());
        toField = new ConditionField();
        toField.readExternal(objectInput);
    }
}
