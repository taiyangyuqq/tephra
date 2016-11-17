package org.lpw.tephra.dao.model;

import org.lpw.tephra.bean.BeanFactory;
import org.lpw.tephra.util.Converter;
import org.lpw.tephra.util.Logger;
import org.lpw.tephra.util.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author lpw
 */
@Repository("tephra.model.table")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ModelTableImpl implements ModelTable {
    @Autowired
    protected Validator validator;
    @Autowired
    protected Converter converter;
    @Autowired
    protected Logger logger;
    protected Class<? extends Model> modelClass;
    protected String tableName;
    protected String memoryName;
    protected String idColumnName;
    protected boolean uuid;
    protected Map<String, Method> getMethods = new HashMap<>();
    protected Map<String, Method> jsonableMethods = new HashMap<>();
    protected Map<String, Jsonable> jsonables = new HashMap<>();
    protected Map<String, ManyToOne> manyToOnes = new HashMap<>();
    protected Map<String, Method> setMethods = new HashMap<>();
    protected Map<String, Class<?>> types = new HashMap<>();
    protected Map<String, String> lowerCases = new HashMap<>();
    protected Map<String, String> columns = new HashMap<>();

    @Override
    public Class<? extends Model> getModelClass() {
        return modelClass;
    }

    @Override
    public void setModelClass(Class<? extends Model> modelClass) {
        this.modelClass = modelClass;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public String getMemoryName() {
        return memoryName;
    }

    @Override
    public void setMemoryName(String memoryName) {
        this.memoryName = memoryName;
    }

    @Override
    public String getIdColumnName() {
        return idColumnName;
    }

    @Override
    public void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName.toLowerCase();
    }

    @Override
    public boolean isUuid() {
        return uuid;
    }

    @Override
    public void setUuid(boolean uuid) {
        this.uuid = uuid;
    }

    @Override
    public void addGetMethod(String name, Method method) {
        getMethods.put(name, method);
        Jsonable jsonable = method.getAnnotation(Jsonable.class);
        if (jsonable != null) {
            jsonableMethods.put(name, method);
            jsonables.put(name, jsonable);
        }
        ManyToOne manyToOne = method.getAnnotation(ManyToOne.class);
        if (manyToOne != null)
            manyToOnes.put(name, manyToOne);

        Class<?> type = method.getReturnType();
        Temporal temporal = method.getAnnotation(Temporal.class);
        if (temporal != null) {
            if (TemporalType.DATE.equals(temporal.value()))
                type = Date.class;
            else if (TemporalType.TIMESTAMP.equals(temporal.value()))
                type = Timestamp.class;
        }
        types.put(name, type);
        addLowerCase(name);
    }

    @Override
    public void addSetMethod(String name, Method method) {
        setMethods.put(name, method);
        addLowerCase(name);
    }

    @Override
    public void addColumn(String columnName, String propertyName) {
        columns.put(columnName.toLowerCase(), propertyName);
        addLowerCase(propertyName);
    }

    protected void addLowerCase(String name) {
        lowerCases.put(name.substring(0, 1).toLowerCase() + name.substring(1), name);
    }

    @Override
    public Object get(Model model, String name) {
        if (model == null || validator.isEmpty(name))
            return null;

        Method method = null;
        Class<?> type = null;
        for (int i = 0; i < 3; i++) {
            String key = getKey(i, name);
            if (validator.isEmpty(key))
                continue;

            method = getMethods.get(key);
            type = types.get(key);

            if (method != null)
                break;
        }
        if (method == null || type == null)
            return null;

        try {
            return convert(type, method.invoke(model));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            logger.warn(e, "获取Model[{}]属性[{}]值时发生异常！", model, name);

            return null;
        }
    }

    @Override
    public Object get(String name, Object value) {
        if (validator.isEmpty(name) || value == null)
            return null;

        if ("id".equalsIgnoreCase(name) || idColumnName.equalsIgnoreCase(name))
            return converter.toString(value);

        Class<?> type = null;
        for (int i = 0; i < 3; i++) {
            String key = getKey(i, name);
            if (validator.isEmpty(key))
                continue;

            type = types.get(key);
            if (type != null)
                break;
        }
        if (type == null)
            return null;

        return convert(type, value);
    }

    @Override
    public void set(Model model, String name, Object value) {
        if (model == null || validator.isEmpty(name))
            return;

        Method method = null;
        Class<?> type = null;
        Jsonable jsonable = null;
        ManyToOne manyToOne = null;
        for (int i = 0; i < 3; i++) {
            String key = getKey(i, name);
            if (validator.isEmpty(key))
                continue;

            method = setMethods.get(key);
            type = types.get(key);
            jsonable = jsonables.get(key);
            manyToOne = manyToOnes.get(key);

            if (method != null)
                break;
        }
        if (method == null || type == null)
            return;

        try {
            if (jsonable != null)
                value = format(jsonable.format(), value);
            if (manyToOne == null) {
                Object object = convert(type, value);
                if (object != null)
                    method.invoke(model, object);
            } else {
                Model m = (Model) BeanFactory.getBean(type);
                m.setId((String) value);
                method.invoke(model, m);
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            logger.warn(e, "设置Model[{}]属性[{}]值[{}]时发生异常！", model, name, value);
        }
    }

    protected String getKey(int i, String name) {
        if (i == 1)
            return lowerCases.get(name);

        if (i == 2)
            return columns.get(name);

        return name;
    }

    protected Object format(String format, Object value) {
        if (validator.isEmpty(format))
            return value;

        if (format.startsWith("number.")) {
            int[] ns = converter.toInts(format.substring(7));
            String v = (String) value;
            StringBuilder sb = new StringBuilder().append(v);
            int indexOf = v.indexOf('.');
            int point = indexOf == -1 ? 0 : (v.length() - indexOf - 1);
            for (int i = point; i < ns[0]; i++)
                sb.append('0');
            if (indexOf > -1) {
                if (point > ns[0])
                    sb.delete(indexOf + ns[0] + 1, sb.length());
                sb.deleteCharAt(indexOf);
            }

            return sb.toString();
        }

        return value;
    }

    public Object convert(Class<?> type, Object value) {
        if (value == null || type.isInstance(value))
            return value;

        if (String.class.equals(type))
            return converter.toString(value);

        if (int.class.equals(type) || Integer.class.equals(type))
            return converter.toInt(value);

        if (long.class.equals(type) || Long.class.equals(type))
            return converter.toLong(value);

        if (float.class.equals(type) || Float.class.equals(type))
            return converter.toFloat(value);

        if (double.class.equals(type) || Double.class.equals(type))
            return converter.toDouble(value);

        if (java.util.Date.class.equals(type) || Date.class.equals(type) || Timestamp.class.equals(type)) {
            java.util.Date date = converter.toDate(value);
            if (date == null)
                return null;

            if (java.util.Date.class.equals(type))
                return date;

            if (Date.class.equals(type))
                return new Date(date.getTime());

            return new Timestamp(date.getTime());
        }

        return null;
    }

    @Override
    public Set<String> getColumnNames() {
        return columns.keySet();
    }

    @Override
    public Set<String> getPropertyNames() {
        return getMethods.keySet();
    }

    @Override
    public Jsonable getJsonable(String name) {
        for (int i = 0; i < 3; i++) {
            String key = getKey(i, name);
            if (validator.isEmpty(key))
                continue;

            Jsonable jsonable = jsonables.get(key);
            if (jsonable != null)
                return jsonable;
        }

        return null;
    }

    @Override
    public Class<?> getType(String name) {
        for (int i = 0; i < 3; i++) {
            String key = getKey(i, name);
            if (validator.isEmpty(key))
                continue;

            Class<?> type = types.get(key);
            if (type != null)
                return type;
        }

        return null;
    }

    @Override
    public <T extends Model> void copy(T source, T target, boolean containId) {
        if (containId)
            target.setId(source.getId());
        setMethods.forEach((name, set) -> {
            Method get = getMethods.get(name);
            if (get == null) {
                logger.warn(null, "无法获得Get[{}]方法！", name);

                return;
            }

            try {
                set.invoke(target, get.invoke(source));
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.warn(e, "复制Model属性时发生异常！");
            }
        });
    }

    @Override
    public <T extends Model> String toString(T model) {
        StringBuilder string = new StringBuilder().append("table=").append(tableName).append(";id=").append(model.getId());
        columns.forEach((column, property) -> string.append(';').append(column).append('&').append(property).append('=').append(get(model, column)));

        return string.toString();
    }
}
