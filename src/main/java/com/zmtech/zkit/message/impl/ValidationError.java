package com.zmtech.zkit.message.impl;


import com.zmtech.zkit.exception.BaseException;
import com.zmtech.zkit.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * ValidationError - used to track information about validation errors.
 *
 * This extends the BaseException and has additional information about the field that had the error, etc.
 *
 * This is not generally thrown all the way up to the user and is instead added to a list of validation errors as
 * things are running, and then all of them can be shown in context of the fields with the errors.
 */
@SuppressWarnings("unused")
public class ValidationError extends BaseException {
    protected final String form;
    protected final String field;
    protected final String serviceName;

    public ValidationError(String field, String message, Throwable nested) {
        super(message, nested);
        this.form = null;
        this.field = field;
        this.serviceName = null;
    }

    public ValidationError(String form, String field, String serviceName, String message, Throwable nested) {
        super(message, nested);
        this.form = form;
        this.field = field;
        this.serviceName = serviceName;
    }

    public String getForm() { return form; }
    public String getFormPretty() { return StringUtil.camelCaseToPretty(form); }
    public String getField() { return field; }
    public String getFieldPretty() { return StringUtil.camelCaseToPretty(field); }
    public String getServiceName() { return serviceName; }
    public String getServiceNamePretty() { return StringUtil.camelCaseToPretty(serviceName); }

    public String toStringPretty() {
        StringBuilder errorBuilder = new StringBuilder();
        String message = getMessage();
        if (message != null) errorBuilder.append(message);
        errorBuilder.append('(');
        String fieldPretty = getFieldPretty();
        if (fieldPretty != null && !fieldPretty.isEmpty()) errorBuilder.append("for field ").append(fieldPretty);
        String formPretty = getFormPretty();
        if (formPretty != null && !formPretty.isEmpty()) errorBuilder.append(" on form ").append(formPretty);
        String serviceNamePretty = getServiceNamePretty();
        if (serviceNamePretty != null && !serviceNamePretty.isEmpty()) errorBuilder.append(" of service ").append(serviceNamePretty);
        return  errorBuilder.toString();
    }

    public Map<String, String> getMap() {
        Map<String, String> veMap = new HashMap<>();
        veMap.put("form", form); veMap.put("field", field); veMap.put("serviceName", serviceName);
        veMap.put("formPretty", getFormPretty()); veMap.put("fieldPretty", getFieldPretty());
        veMap.put("serviceNamePretty", getServiceNamePretty());
        veMap.put("message", getMessage());
        if (getCause() != null) veMap.put("cause", getCause().toString());
        return veMap;
    }
}
