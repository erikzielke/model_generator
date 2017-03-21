package com.github.erikzielke.model_generator.databaseinfo;


import com.github.erikzielke.model_generator.databasemodel.Column;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Types;
import java.util.Date;
import com.github.erikzielke.model_generator.databasemodel.Column;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JType;

/**
 * Created by Erik on 10/2/13.
 */
public class TypeUtil {
    public static String sqlTypeToSetterGetter(int sqlType) {
        switch (sqlType) {
            case Types.BIT:
                return "Boolean";
            case Types.TINYINT:
                return "Boolean";
            case Types.SMALLINT:
                return "Short";
            case Types.INTEGER:
                return "Int";
            case Types.BIGINT:
                return "Long";
            case Types.REAL:
                return "Float";
            case Types.DOUBLE:
                return "Double";
            case Types.FLOAT:
                return "Float";
            case Types.NUMERIC:
            case Types.DECIMAL:
                return "BigDecimal";
            case Types.DATE:
                return "Date";
            case Types.TIME:
                return "Time";
            case Types.TIMESTAMP:
                return "Timestamp";
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                return "String";
	    case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "BinaryStream";
            case Types.OTHER:
                return "String";
            default:
                return null;
        }
    }


    public static JType sqlTypeToJavaClass(JCodeModel codeModel, Column column, int sqlType) {
        switch (sqlType) {
            case Types.BIT:
                return codeModel.ref(Boolean.class);
            case Types.TINYINT:
                return codeModel.ref(Boolean.class);
            case Types.SMALLINT:
                return codeModel.ref(Short.class);
            case Types.INTEGER:
                return codeModel.ref(Integer.class);
            case Types.BIGINT:
                return codeModel.ref(Long.class);
            case Types.REAL:
                return codeModel.ref(Float.class);
            case Types.DOUBLE:
                return codeModel.ref(Double.class);
            case Types.FLOAT:
                return codeModel.ref(Float.class);
            case Types.NUMERIC:
            case Types.DECIMAL:
                return codeModel.ref(BigDecimal.class);
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return codeModel.ref(Date.class);
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                return codeModel.ref(String.class);
	    case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return codeModel.ref(InputStream.class);
            case Types.ARRAY:
                return codeModel.ref(Object.class);
            case Types.OTHER:
                if (column.getComment() == null || column.getComment().isEmpty()) {
                    return codeModel.ref(String.class);
                } else {
                    return codeModel.directClass(column.getComment());
                }
            default:
                return null;
        }
    }
}
