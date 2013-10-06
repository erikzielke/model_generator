package com.github.erikzielke.model_generator.codegenerator;

import com.github.erikzielke.model_generator.codegenerator.namingstrategy.DefaultNamingStrategy;
import com.github.erikzielke.model_generator.codegenerator.namingstrategy.NamingStrategyInterface;
import com.github.erikzielke.model_generator.databaseinfo.TypeUtil;
import com.github.erikzielke.model_generator.databasemodel.Column;
import com.github.erikzielke.model_generator.databasemodel.Database;
import com.github.erikzielke.model_generator.databasemodel.Index;
import com.github.erikzielke.model_generator.databasemodel.Table;
import com.sun.codemodel.*;
import org.apache.commons.lang.StringUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 *
 */
public class JavaCodeGenerator implements CodeGenerator {
    private File destinationDir;
    private String packageName;
    private JCodeModel codeModel;

    private Map<Table, JDefinedClass> beansMap;
    private JDefinedClass parameterSetter;
    private JDefinedClass idSetter;
    private NamingStrategyInterface namingStrategy = new DefaultNamingStrategy();

    public NamingStrategyInterface getNamingStrategy() {
        return namingStrategy;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public void generateCode(Database database) {
        codeModel = new JCodeModel();
        beansMap = new HashMap<Table, JDefinedClass>();
        JPackage rootPackage = codeModel._package(packageName);

        generateBeans(rootPackage, database);
        generateDaos(rootPackage, database);

        try {
            codeModel.build(destinationDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateDaos(JPackage rootPackage, Database database) {
        try {
            JDefinedClass superDao = generateSuperDao(rootPackage);

            for (Table table : database.getTables()) {
                generateDao(superDao, rootPackage, table);
            }
        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private JDefinedClass generateSuperDao(JPackage rootPackage) throws JClassAlreadyExistsException {
        JDefinedClass superDao = rootPackage._class(JMod.ABSTRACT, "Dao");
        JTypeVar beanClass = superDao.generify("T");


        parameterSetter = superDao._interface("ParameterSetter");
        JMethod setParameters = parameterSetter.method(JMod.PUBLIC, codeModel.VOID, "setParameters");
        setParameters.param(PreparedStatement.class, "statement");
        setParameters._throws(codeModel.ref(SQLException.class));

        idSetter = superDao._interface("IdSetter");
        JMethod setId = idSetter.method(JMod.PUBLIC, codeModel.VOID, "setId");
        setId.param(codeModel.INT, "id");


        JMethod createMethod = superDao.method(JMod.PUBLIC | JMod.ABSTRACT, beanClass, "create");
        createMethod.param(ResultSet.class, "result");
        createMethod._throws(codeModel.ref(SQLException.class));

        JClass arrayListClass = codeModel.ref(ArrayList.class);
        JClass beanArrayListClass = arrayListClass.narrow(beanClass);

        JFieldVar dataSource = superDao.field(JMod.PRIVATE, DataSource.class, "dataSource");

        generateExecuteQuery(superDao, parameterSetter, beanArrayListClass, dataSource);
        generateExecuteInsert(superDao, parameterSetter, idSetter, dataSource);
        generateCount(superDao, dataSource);
        generateExecuteUpdateDelete(superDao, parameterSetter, dataSource);

        return superDao;
    }

    private void generateCount(JDefinedClass superDao, JFieldVar dataSource) {
        JMethod countRows = superDao.method(JMod.PUBLIC, codeModel.INT, "countRows");
        JVar sql = countRows.param(String.class, "sql");
        JVar connection = countRows.body().decl(codeModel.ref(Connection.class), "connection", JExpr._null());

        JTryBlock insertTryBlock = countRows.body()._try();
        JCatchBlock catchBlock = insertTryBlock._catch(codeModel.ref(SQLException.class));

        JBlock finallyBody = insertTryBlock._finally();
        JBlock tryBody = insertTryBlock.body();
        tryBody.assign(connection, dataSource.invoke("getConnection"));
        JClass statementClass = codeModel.ref(PreparedStatement.class);
        JInvocation prepareStatement = connection.invoke("prepareStatement");
        prepareStatement.arg(sql);
        JVar statement = tryBody.decl(statementClass, "statement", prepareStatement);
        JVar resultSet = tryBody.decl(codeModel.ref(ResultSet.class), "result", statement.invoke("executeQuery"));
        JInvocation getInt = resultSet.invoke("getInt");
        getInt.arg(JExpr.lit(1));
        tryBody._if(resultSet.invoke("next"))._then()._return(getInt);


        JVar exception = catchBlock.param("e");
        JInvocation newRuntimeException = JExpr._new(codeModel.ref(RuntimeException.class));
        newRuntimeException.arg(exception);
        catchBlock.body()._throw(newRuntimeException);

        JBlock finallyAndConnectionNotNull = finallyBody._if(connection.ne(JExpr._null()))._then();
        JTryBlock closingTryBlock = finallyAndConnectionNotNull._try();
        closingTryBlock.body().add(connection.invoke("close"));
        JCatchBlock closingCatch = closingTryBlock._catch(codeModel.ref(SQLException.class));
        JVar e = closingCatch.param("e");
        JInvocation newRuntimeExceptionClosing = JExpr._new(codeModel.ref(RuntimeException.class));
        newRuntimeExceptionClosing.arg(e);
        closingCatch.body()._throw(newRuntimeExceptionClosing);

        countRows.body()._return(JExpr.lit(0));
    }

    private void generateExecuteInsert(JDefinedClass superDao, JDefinedClass parameterSetter, JDefinedClass idSetter, JFieldVar dataSource) {
        String executeInsert = "executeInsert";
        generateExecute(superDao, parameterSetter, idSetter, dataSource, executeInsert);
    }

    private void generateExecuteUpdateDelete(JDefinedClass superDao, JDefinedClass parameterSetter, JFieldVar dataSource) {
        String executeInsert = "executeUpdateDelete";
        generateExecute(superDao, parameterSetter, null, dataSource, executeInsert);
    }

    private void generateExecute(JDefinedClass superDao, JDefinedClass parameterSetter, JDefinedClass idSetter, JFieldVar dataSource, String name) {
        JMethod insertQuery = superDao.method(JMod.PROTECTED, codeModel.VOID, name);
        JVar sql = insertQuery.param(String.class, "sql");
        JVar parameters = insertQuery.param(parameterSetter, "parameters");

        JVar idSetterParam = null;
        if (idSetter != null) {
            idSetterParam = insertQuery.param(idSetter, "idSetter");
        }

        JVar connection = insertQuery.body().decl(codeModel.ref(Connection.class), "connection", JExpr._null());

        JTryBlock insertTryBlock = insertQuery.body()._try();
        JCatchBlock catchBlock = insertTryBlock._catch(codeModel.ref(SQLException.class));
        JBlock finallyBody = insertTryBlock._finally();

        JBlock tryBody = insertTryBlock.body();
        tryBody.assign(connection, dataSource.invoke("getConnection"));
        JClass statementClass = codeModel.ref(PreparedStatement.class);
        JInvocation prepareStatement = connection.invoke("prepareStatement");
        prepareStatement.arg(sql);
        if (idSetter != null) {

            prepareStatement.arg(codeModel.ref(java.sql.Statement.class).staticRef("RETURN_GENERATED_KEYS"));
        }
        JVar statement = tryBody.decl(statementClass, "statement", prepareStatement);
        JInvocation setParameters = parameters.invoke("setParameters");
        setParameters.arg(statement);
        tryBody._if(parameters.ne(JExpr._null()))._then().add(setParameters);

        tryBody.invoke(statement, "executeUpdate");

        if (idSetter != null) {
            JConditional ifIdSetter = tryBody._if(idSetterParam.ne(JExpr._null()));
            JBlock block = ifIdSetter._then();
            JVar keys = block.decl(codeModel.ref(ResultSet.class), "key", statement.invoke("getGeneratedKeys"));
            JInvocation param = idSetterParam.invoke("setId");
            JInvocation getInt = keys.invoke("getInt");
            getInt.arg(JExpr.lit(1));
            param.arg(getInt);
            block._if(keys.invoke("next"))._then().add(param);
        }

        JVar exception = catchBlock.param("e");
        JInvocation newRuntimeException = JExpr._new(codeModel.ref(RuntimeException.class));
        newRuntimeException.arg(exception);
        catchBlock.body()._throw(newRuntimeException);

        JBlock finallyAndConnectionNotNull = finallyBody._if(connection.ne(JExpr._null()))._then();
        JTryBlock closingTryBlock = finallyAndConnectionNotNull._try();
        closingTryBlock.body().add(connection.invoke("close"));
        JCatchBlock closingCatch = closingTryBlock._catch(codeModel.ref(SQLException.class));
        JVar e = closingCatch.param("e");
        JInvocation newRuntimeExceptionClosing = JExpr._new(codeModel.ref(RuntimeException.class));
        newRuntimeExceptionClosing.arg(e);
        closingCatch.body()._throw(newRuntimeExceptionClosing);
    }

    private void generateExecuteQuery(JDefinedClass superDao, JDefinedClass parameterSetter, JClass beanArrayListClass,
                                      JFieldVar dataSource) {
        JMethod executeQuery = superDao.method(JMod.PROTECTED, beanArrayListClass, "executeQuery");
        JVar sql = executeQuery.param(String.class, "sql");
        JVar parameters = executeQuery.param(parameterSetter, "parameters");
        JVar result = executeQuery.body().decl(beanArrayListClass, "result", JExpr._new(beanArrayListClass));
        JVar connection = executeQuery.body().decl(codeModel.ref(Connection.class), "connection", JExpr._null());
        JTryBlock tryBlock = executeQuery.body()._try();

        JBlock tryBody = tryBlock.body();
        JCatchBlock catchBlock = tryBlock._catch(codeModel.ref(SQLException.class));
        JBlock catchBlack = catchBlock.body();
        JBlock finallyBlock = tryBlock._finally();

        tryBody.assign(connection, dataSource.invoke("getConnection"));
        JInvocation prepareStatement = connection.invoke("prepareStatement");
        prepareStatement.arg(sql);
        JVar statement = tryBody.decl(codeModel.ref(PreparedStatement.class), "statement", prepareStatement);
        JInvocation setParameterInvocation = parameters.invoke("setParameters");
        setParameterInvocation.arg(statement);
        tryBody._if(parameters.ne(JExpr._null()))._then().add(setParameterInvocation);
        JVar resultSet = tryBody.decl(codeModel.ref(ResultSet.class), "resultSet", statement.invoke("executeQuery"));
        JWhileLoop whileLoop = tryBody._while(resultSet.invoke("next"));
        JInvocation add = result.invoke("add");
        JInvocation create = JExpr.invoke("create");
        create.arg(resultSet);
        add.arg(create);
        whileLoop.body().add(add);

        JClass ref = codeModel.ref(RuntimeException.class);
        JInvocation exp = JExpr._new(ref);
        exp.arg(catchBlock.param("e"));
        catchBlack._throw(exp);

        JConditional ifConnection = finallyBlock._if(connection.ne(JExpr._null()));
        JTryBlock finallyTryBlock = ifConnection._then()._try();
        finallyTryBlock.body().add(connection.invoke("close"));
        JCatchBlock finallyCatchBlock = finallyTryBlock._catch(codeModel.ref(SQLException.class));
        JInvocation newRuntimeException = JExpr._new(codeModel.ref(RuntimeException.class));
        newRuntimeException.arg(finallyCatchBlock.param("e"));
        finallyCatchBlock.body()._throw(newRuntimeException);

        executeQuery.body()._return(result);
    }

    private void generateDao(JDefinedClass superDao, JPackage rootPackage, Table table) {
        try {
            JDefinedClass bean = beansMap.get(table);

            String daoFQName = rootPackage.name() + "." + namingStrategy.getPojoName(table) + "Dao";
            JDefinedClass dao = codeModel._class(daoFQName);
            dao._extends(superDao.narrow(bean));

            generateCreateMethod(table, dao, bean);
            generateFindAll(table, dao, bean);
            generateFindAllLimit(table, dao, bean);
            generateInsert(table, dao, bean);
            generateCountAll(table, dao, bean);

            if (table.getPrimaryKey() != null && !table.getPrimaryKey().getColumns().isEmpty()) {
                generateUpdate(table, dao, bean);
                generateDelete(table, dao, bean);
                generateFindByPrimaryKey(table, dao, bean);
            }

        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateCountAll(Table table, JDefinedClass dao, JDefinedClass bean) {
        JMethod countAll = dao.method(JMod.PUBLIC, codeModel.INT, "countAll");
        String countSql = "SELECT COUNT(*) FROM " + table.getName();
        JInvocation countRows = JExpr.invoke("countRows");
        countRows.arg(countSql);
        countAll.body()._return(countRows);
    }

    private void generateFindByPrimaryKey(Table table, JDefinedClass dao, JDefinedClass bean) {
        Index primaryKey = table.getPrimaryKey();
        StringBuilder keyFieldNames = new StringBuilder();
        for (Column column : primaryKey.getColumns()) {
            keyFieldNames.append(StringUtils.capitalize(column.getName()));
            keyFieldNames.append("And");
        }
        keyFieldNames.delete(keyFieldNames.length() - 3, keyFieldNames.length());

        JMethod findByPrimaryKey = dao.method(JMod.PUBLIC, bean, "findBy" + keyFieldNames.toString());

        for (Column column : primaryKey.getColumns()) {
            Class type = TypeUtil.sqlTypeToJavaClass(column.getType());
            findByPrimaryKey.param(JMod.FINAL, type, namingStrategy.getFieldName(column));
        }

        JInvocation queryInvocation = JExpr.invoke("executeQuery");
        StringBuilder sql = new StringBuilder("SELECT * FROM " + table.getName() + " WHERE ");
        addPrimaryKey(table, sql);
        queryInvocation.arg(JExpr.lit(sql.toString()));

        JDefinedClass parameterSetter = codeModel.anonymousClass(this.parameterSetter);
        JMethod setParameters = parameterSetter.method(JMod.PUBLIC, codeModel.VOID, "setParameters");
        JVar statement = setParameters.param(PreparedStatement.class, "statement");
        setParameters.annotate(Override.class);
        setParameters._throws(SQLException.class);

        int index = 1;
        for (Column column : table.getPrimaryKey().getColumns()) {
            JInvocation setObject = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
            setObject.arg(JExpr.lit(index++));

            switch (column.getType()) {
                case Types.TIME:
                case Types.DATE:
                case Types.TIMESTAMP:
                    setObject.arg(handleSetTime(column.getType(), JExpr.ref(namingStrategy.getFieldName(column)).invoke("getTime")));
                    break;
                default:
                    setObject.arg(JExpr.ref(namingStrategy.getFieldName(column)));
            }

            setParameters.body().add(setObject);
        }

        queryInvocation.arg(JExpr._new(parameterSetter));
        JClass beanList = codeModel.ref(ArrayList.class).narrow(bean);

        JVar result = findByPrimaryKey.body().decl(beanList, "result", queryInvocation);
        JConditional isEmpty = findByPrimaryKey.body()._if(result.invoke("isEmpty"));
        isEmpty._then()._return(JExpr._null());
        JInvocation get = result.invoke("get");
        get.arg(JExpr.lit(0));
        isEmpty._else()._return(get);

    }

    private void generateDelete(Table table, JDefinedClass dao, JDefinedClass bean) {
        JMethod delete = dao.method(JMod.PUBLIC, codeModel.VOID, "delete");
        JVar beanObject = delete.param(JMod.FINAL, bean, StringUtils.uncapitalize(bean.name()));
        StringBuilder deleteSQL = new StringBuilder("DELETE FROM `" + table.getName() + "` WHERE ");
        addPrimaryKey(table, deleteSQL);

        JInvocation executeUpdateDelete = delete.body().invoke("executeUpdateDelete");
        executeUpdateDelete.arg(JExpr.lit(deleteSQL.toString()));
        JDefinedClass parameterSetter = codeModel.anonymousClass(this.parameterSetter);
        JMethod setParameters = parameterSetter.method(JMod.PUBLIC, codeModel.VOID, "setParameters");
        setParameters._throws(SQLException.class);
        setParameters.annotate(Override.class);
        JVar statement = setParameters.param(PreparedStatement.class, "statement");

        JBlock body = setParameters.body();
        Index primaryKey = table.getPrimaryKey();
        int index = 1;
        for (Column column : primaryKey.getColumns()) {
            JInvocation setObject = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
            setObject.arg(JExpr.lit(index));

            switch (column.getType()) {
                case Types.TIME:
                case Types.DATE:
                case Types.TIMESTAMP:
                    setObject.arg(handleSetTime(column.getType(), beanObject.invoke(namingStrategy.getGetterName(column)).invoke("getTime")));
                    break;
                default:
                    setObject.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
            }

            body.add(setObject);
            index++;
        }

        executeUpdateDelete.arg(JExpr._new(parameterSetter));
    }

    private void addPrimaryKey(Table table, StringBuilder sql) {
        for (Column column : table.getPrimaryKey().getColumns()) {
            sql.append("`").append(column.getName()).append("`").append(" = ?");
            sql.append(" AND ");
        }
        sql.delete(sql.length() - 5, sql.length());
    }

    private void generateUpdate(Table table, JDefinedClass dao, JDefinedClass bean) {
        JMethod updateMethod = dao.method(JMod.PUBLIC, codeModel.VOID, "update");
        JVar beanObject = updateMethod.param(JMod.FINAL, bean, StringUtils.uncapitalize(bean.name()));

        StringBuilder updateSQL = new StringBuilder("UPDATE `" + table.getName() + "` SET ");
        for (Column column : table.getColumns()) {
            boolean columnPartOfPrimaryKey = table.isColumnPartOfPrimaryKey(column);
            if (!columnPartOfPrimaryKey) {
                updateSQL.append("`").append(column.getName()).append("`").append(" = ?, ");
            }
        }

        updateSQL.delete(updateSQL.length() - 2, updateSQL.length());
        updateSQL.append(" WHERE ");
        addPrimaryKey(table, updateSQL);

        JInvocation executeUpdateDelete = updateMethod.body().invoke("executeUpdateDelete");
        executeUpdateDelete.arg(JExpr.lit(updateSQL.toString()));
        JDefinedClass parameterSetter = codeModel.anonymousClass(this.parameterSetter);
        JMethod setParameters = parameterSetter.method(JMod.PUBLIC, codeModel.VOID, "setParameters");
        setParameters._throws(SQLException.class);
        setParameters.annotate(Override.class);
        JVar statement = setParameters.param(PreparedStatement.class, "statement");

        JBlock body = setParameters.body();
        Index primaryKey = table.getPrimaryKey();
        int parameterIndex = 1;
        for (Column column : table.getColumns()) {
            if (!table.isColumnPartOfPrimaryKey(column)) {
                if (!column.isNullable()) {
                    JInvocation invocation = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                    invocation.arg(JExpr.lit(parameterIndex));
                    switch (column.getType()) {
                        case Types.TIME:
                        case Types.DATE:
                        case Types.TIMESTAMP:
                            invocation.arg(handleSetTime(column.getType(), beanObject.invoke(namingStrategy.getGetterName(column)).invoke("getTime")));
                            break;
                        default:
                            invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                    }
                    body.add(invocation);
                } else {
                    JConditional nullCondition = body._if(beanObject.invoke(namingStrategy.getGetterName(column)).ne(JExpr._null()));

                    JInvocation invocation = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                    invocation.arg(JExpr.lit(parameterIndex));
                    switch (column.getType()) {
                        case Types.TIME:
                        case Types.DATE:
                        case Types.TIMESTAMP:
                            invocation.arg(handleSetTime(column.getType(), beanObject.invoke(namingStrategy.getGetterName(column)).invoke("getTime")));
                            break;
                        default:
                            invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                    }
                    nullCondition._then().add(invocation);

                    JInvocation setNull = statement.invoke("setNull");
                    setNull.arg(JExpr.lit(parameterIndex));
                    setNull.arg(JExpr.lit(column.getType()));
                    nullCondition._else().add(setNull);
                }
                parameterIndex++;

            }
        }

        for (Column column : primaryKey.getColumns()) {
            if (table.isColumnPartOfPrimaryKey(column)) {
                JInvocation setObject = statement.invoke("setObject");
                setObject.arg(JExpr.lit(parameterIndex));
                setObject.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                body.add(setObject);
                parameterIndex++;
            }
        }

        executeUpdateDelete.arg(JExpr._new(parameterSetter));
    }

    private void generateInsert(Table table, JDefinedClass dao, JDefinedClass bean) {
        JMethod insert = dao.method(JMod.PUBLIC, codeModel.VOID, "insert");
        JVar beanObject = insert.param(JMod.FINAL, bean, StringUtils.uncapitalize(bean.name()));
        StringBuilder insertSQL = new StringBuilder("INSERT INTO `" + table.getName() + "` (");

        int columnCount = 0;
        Column autoIncrementColumn = null;
        for (Column column : table.getColumns()) {
            if (!column.isAutoIncrement()) {
                insertSQL.append("`").append(column.getName()).append("`");
                insertSQL.append(", ");
                columnCount++;
            } else {
                autoIncrementColumn = column;
            }
        }
        insertSQL.delete(insertSQL.length() - 2, insertSQL.length());
        insertSQL.append(") VALUES (");
        for (int i = 0; i < columnCount; i++) {
            insertSQL.append("?, ");
        }
        insertSQL.delete(insertSQL.length() - 2, insertSQL.length());
        insertSQL.append(")");
        JInvocation executeInsert = insert.body().invoke("executeInsert");
        executeInsert.arg(JExpr.lit(insertSQL.toString()));


        JDefinedClass jDefinedClass = codeModel.anonymousClass(parameterSetter);
        JMethod setParameters = jDefinedClass.method(JMod.PUBLIC, codeModel.VOID, "setParameters");
        setParameters._throws(SQLException.class);
        setParameters.annotate(Override.class);
        JVar statement = setParameters.param(PreparedStatement.class, "statement");
        JBlock body = setParameters.body();

        int parameterIndex = 1;
        for (Column column : table.getColumns()) {
            if (!column.isAutoIncrement()) {
                if (!column.isNullable()) {
                    JInvocation invocation = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                    invocation.arg(JExpr.lit(parameterIndex));
                    switch (column.getType()) {
                        case Types.TIME:
                        case Types.DATE:
                        case Types.TIMESTAMP:
                            invocation.arg(handleSetTime(column.getType(), beanObject.invoke(namingStrategy.getGetterName(column)).invoke("getTime")));
                            break;
                        default:
                            invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                    }
                    body.add(invocation);
                } else {
                    JConditional nullCondition = body._if(beanObject.invoke(namingStrategy.getGetterName(column)).ne(JExpr._null()));

                    JInvocation invocation = statement.invoke("set" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                    invocation.arg(JExpr.lit(parameterIndex));
                    switch (column.getType()) {
                        case Types.TIME:
                        case Types.DATE:
                        case Types.TIMESTAMP:
                            invocation.arg(handleSetTime(column.getType(), beanObject.invoke(namingStrategy.getGetterName(column)).invoke("getTime")));
                            break;
                        default:
                            invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                    }
                    nullCondition._then().add(invocation);

                    JInvocation setNull = statement.invoke("setNull");
                    setNull.arg(JExpr.lit(parameterIndex));
                    setNull.arg(JExpr.lit(column.getType()));
                    nullCondition._else().add(setNull);
                }
                parameterIndex++;
            }
        }

        executeInsert.arg(JExpr._new(jDefinedClass));
        if (autoIncrementColumn != null) {
            JDefinedClass idSetterClass = codeModel.anonymousClass(idSetter);
            JMethod setId = idSetterClass.method(JMod.PUBLIC, codeModel.VOID, "setId");
            setId.annotate(Override.class);
            JVar generatedId = setId.param(codeModel.INT, "id");

            JInvocation invoke = beanObject.invoke(namingStrategy.getSetterName(autoIncrementColumn));
            invoke.arg(generatedId);
            setId.body().add(invoke);
            executeInsert.arg(JExpr._new(idSetterClass));
        } else {
            executeInsert.arg(JExpr._null());
        }
    }

    private void generateFindAll(Table table, JDefinedClass dao, JDefinedClass bean) {
        JClass arrayListClass = codeModel.ref(ArrayList.class);
        JClass beanArrayListClass = arrayListClass.narrow(bean);
        JMethod findAll = dao.method(JMod.PUBLIC, beanArrayListClass, "findAll");
        JInvocation queryInvocation = JExpr.invoke("executeQuery");
        queryInvocation.arg(JExpr.lit("SELECT * FROM " + table.getName()));
        queryInvocation.arg(JExpr._null());
        findAll.body()._return(queryInvocation);
    }

    private void generateFindAllLimit(Table table, JDefinedClass dao, JDefinedClass bean) {
        JClass arrayListClass = codeModel.ref(ArrayList.class);
        JClass beanArrayListClass = arrayListClass.narrow(bean);
        JMethod findAll = dao.method(JMod.PUBLIC, beanArrayListClass, "findAll");
        findAll.param(codeModel.INT, "start");
        findAll.param(codeModel.INT, "count");
        findAll.param(String.class, "order");
        JInvocation queryInvocation = JExpr.invoke("executeQuery");
        JExpression lit = JExpr.direct("\"SELECT * FROM " + table.getName() + " ORDER BY \" + order+ \" LIMIT \" + start + \",  \" + count+ \" \" ");

        queryInvocation.arg(lit);
        queryInvocation.arg(JExpr._null());
        findAll.body()._return(queryInvocation);
    }

    private void generateCreateMethod(Table table, JDefinedClass dao, JDefinedClass bean) {
        JMethod createMethod = dao.method(JMod.PUBLIC, bean, "create");
        createMethod._throws(SQLException.class);
        JVar resultSet = createMethod.param(ResultSet.class, "resultSet");
        JVar resultVariable = createMethod.body().decl(bean, StringUtils.uncapitalize(bean.name()), JExpr._new(bean));
        for (Column column : table.getColumns()) {
            if (!column.isNullable()) {
                JInvocation setValue = resultVariable.invoke(namingStrategy.getSetterName(column));
                JInvocation getValue = null;
                switch (column.getType()) {
                    case Types.TIME:
                    case Types.DATE:
                    case Types.TIMESTAMP:
                        JInvocation newInvoke = JExpr._new(codeModel.ref(java.util.Date.class));
                        JInvocation invoke = resultSet.invoke("get" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                        newInvoke.arg(invoke.invoke("getTime"));
                        invoke.arg(column.getName());
                        getValue = newInvoke;
                        break;
                    default:
                        JInvocation invoke1 = resultSet.invoke("get" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                        invoke1.arg(column.getName());
                        getValue = invoke1;
                        break;
                }
                setValue.arg(getValue);
                createMethod.body().add(setValue);
            } else {
                Class dataType = TypeUtil.sqlTypeToJavaClass(column.getType());
                JInvocation getValue = resultSet.invoke("get" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                getValue.arg(column.getName());
                JVar field = createMethod.body().decl(codeModel.ref(dataType), namingStrategy.getFieldName(column), getValue);
                JConditional wasNull = createMethod.body()._if(resultSet.invoke("wasNull"));
                wasNull._then().assign(field, JExpr._null());

                switch (column.getType()) {
                    case Types.TIME:
                    case Types.DATE:
                    case Types.TIMESTAMP:
                        JInvocation exp = JExpr._new(codeModel.ref(java.util.Date.class));
                        exp.arg(field.invoke("getTime"));
                        wasNull._else().assign(field, exp);
                        break;
                    default:
                }


                JInvocation setValue = resultVariable.invoke(namingStrategy.getSetterName(column));
                setValue.arg(field);
                createMethod.body().add(setValue);
            }
        }
        createMethod.body()._return(resultVariable);
    }

    private void generateBeans(JPackage rootPackage, Database database) {
        for (Table table : database.getTables()) {
            generateBean(rootPackage, table);
        }
    }

    private void generateBean(JPackage rootPackage, Table table) {
        try {
            JDefinedClass bean = codeModel._class(rootPackage.name() + "." + namingStrategy.getPojoName(table));
            for (Column column : table.getColumns()) {
                Class type = TypeUtil.sqlTypeToJavaClass(column.getType());

                JType usedType = codeModel.ref(type);

                if (!column.isAutoIncrement() && !column.isNullable()) {
                    JType unboxify = codeModel.ref(type).unboxify();
                    if (unboxify != null) {
                        usedType = unboxify;
                    }
                }

                JFieldVar field = bean.field(JMod.PRIVATE, usedType, namingStrategy.getFieldName(column));
                JMethod getMethod = bean.method(JMod.PUBLIC, usedType, namingStrategy.getGetterName(column));
                getMethod.body()._return(field);

                JMethod setMethod = bean.method(JMod.PUBLIC, codeModel.VOID, namingStrategy.getSetterName(column));
                JVar newValue = setMethod.param(usedType, namingStrategy.getFieldName(column));
                setMethod.body().assign(JExpr.refthis(field.name()), newValue);
            }

            beansMap.put(table, bean);
        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private JExpression handleSetTime(int sqlType, JInvocation getTime) {
        switch (sqlType) {
            case Types.TIME:
                return JExpr._new(codeModel.ref(Time.class)).arg(getTime);
            case Types.DATE:
                return JExpr._new(codeModel.ref(Date.class)).arg(getTime);
            case Types.TIMESTAMP:
                return JExpr._new(codeModel.ref(Timestamp.class)).arg(getTime);
        }
        return null;
    }
}
