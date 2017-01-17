package com.github.erikzielke.model_generator.codegenerator;

import com.github.erikzielke.model_generator.codegenerator.namingstrategy.DefaultNamingStrategy;
import com.github.erikzielke.model_generator.codegenerator.namingstrategy.NamingStrategyInterface;
import com.github.erikzielke.model_generator.configuration.Configuration;
import com.github.erikzielke.model_generator.databaseinfo.TypeUtil;
import com.github.erikzielke.model_generator.databasemodel.Column;
import com.github.erikzielke.model_generator.databasemodel.Database;
import com.github.erikzielke.model_generator.databasemodel.Index;
import com.github.erikzielke.model_generator.databasemodel.Table;
import com.sun.codemodel.*;
import org.apache.commons.lang.StringUtils;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.*;
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
    private Configuration configuration;
    private JDefinedClass dataSourceHelper;

    public JavaCodeGenerator(Configuration configuration) {
        this.configuration = configuration;
    }

    public NamingStrategyInterface getNamingStrategy() {
        return namingStrategy;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void generateCode(Database database) {
        codeModel = new JCodeModel();
        beansMap = new HashMap<Table, JDefinedClass>();
        JPackage rootPackage = codeModel._package(packageName);

        generateBeans(rootPackage, database);
        generateDaos(rootPackage, database);

        try {
            writeQueryHelper(rootPackage);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            codeModel.build(destinationDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeQueryHelper(JPackage rootPackage) throws IOException {
        String path = rootPackage.name().replace(".", File.separator);

        File parent = new File(destinationDir, path);
        File file = new File(parent, "QueryHelper.java");
        if (!file.exists()) {
            try {
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                file.createNewFile();
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                bufferedWriter.write("package " + rootPackage.name() + ";\n" +
                        "\n" +
                        "import javax.sql.DataSource;\n" +
                        "import java.lang.reflect.Field;\n" +
                        "import java.sql.Connection;\n" +
                        "import java.sql.PreparedStatement;\n" +
                        "import java.sql.ResultSet;\n" +
                        "import java.sql.SQLException;\n" +
                        "import java.util.ArrayList;\n" +
                        "import java.util.HashMap;\n" +
                        "import java.util.List;\n" +
                        "\n" +
                        "public class QueryHelper {\n" +
                        "    public static <E> List<E> executeQuery(String sql, Dao.ParameterSetter parameters, Class<E> klazz) {\n" +
                        "        DataSource dataSource = DataSourceHelper.getInstance().getDataSource();\n" +
                        "        List<E> result = new ArrayList<E>();\n" +
                        "        Connection connection = null;\n" +
                        "        try {\n" +
                        "            connection = dataSource.getConnection();\n" +
                        "            PreparedStatement statement = connection.prepareStatement(sql);\n" +
                        "            if (parameters!= null) {\n" +
                        "                parameters.setParameters(statement);\n" +
                        "            }\n" +
                        "\n" +
                        "            Field[] declaredFields = klazz.getDeclaredFields();\n" +
                        "            HashMap<String, Field> fieldMap = new HashMap<String, Field>();\n" +
                        "            for (Field declaredField : declaredFields) {\n" +
                        "                declaredField.setAccessible(true);\n" +
                        "                fieldMap.put(declaredField.getName(), declaredField);\n" +
                        "            }\n" +
                        "\n" +
                        "            ResultSet resultSet = statement.executeQuery();\n" +
                        "\n" +
                        "            java.sql.ResultSetMetaData metaData = resultSet.getMetaData();\n" +
                        "\n" +
                        "            java.util.List<String> labels = new ArrayList<String>(metaData.getColumnCount());\n" +
                        "            for (int i = 1; i <= metaData.getColumnCount(); i++) {\n" +
                        "                String columnLabel = metaData.getColumnLabel(i);\n" +
                        "                labels.add(columnLabel);\n" +
                        "            }\n" +
                        "\n" +
                        "            while (resultSet.next()) {\n" +
                        "                try {\n" +
                        "                    E instance = klazz.newInstance();\n" +
                        "\n" +
                        "                    for (String label : labels) {\n" +
                        "                        Field field = fieldMap.get(label);\n" +
                        "                        if (field != null) {\n" +
                        "                            if (field.getType().equals(String.class)) {\n" +
                        "                                field.set(instance, resultSet.getString(label));\n" +
                        "                            } else if (field.getType().equals(java.util.Date.class)) {\n" +
                        "                                java.sql.Timestamp timestamp = resultSet.getTimestamp(label);\n" +
                        "                                field.set(instance, timestamp);\n" +
                        "                            } else if (field.getType().equals(Integer.class)) {\n" +
                        "                                int value = resultSet.getInt(label);\n" +
                        "                                if (resultSet.wasNull()) {\n" +
                        "                                    field.set(instance, null);\n" +
                        "                                } else {\n" +
                        "                                    field.set(instance, value);\n" +
                        "                                }\n" +
                        "                            } else if (field.getType().equals(int.class)) {\n" +
                        "                                field.set(instance, resultSet.getInt(label));\n" +
                        "                            } else if (field.getType().equals(Boolean.class)) {\n" +
                        "                                boolean value = resultSet.getBoolean(label);\n" +
                        "                                if (resultSet.wasNull()) {\n" +
                        "                                    field.set(instance, null);\n" +
                        "                                } else {\n" +
                        "                                    field.set(instance, value);\n" +
                        "                                }\n" +
                        "                            } else if (field.getType().equals(boolean.class)) {\n" +
                        "                                field.set(instance, resultSet.getBoolean(label));\n" +
                        "                            } else if (field.getType().equals(Double.class)) {\n" +
                        "                                double value = resultSet.getDouble(label);\n" +
                        "                                if (resultSet.wasNull()) {\n" +
                        "                                    field.set(instance, null);\n" +
                        "                                } else {\n" +
                        "                                    field.set(instance, value);\n" +
                        "                                }\n" +
                        "                            } else if (field.getType().equals(double.class)) {\n" +
                        "                                field.set(instance, resultSet.getDouble(label));\n" +
                        "                            }  else if (field.getType().equals(Float.class)) {\n" +
                        "                                double value = resultSet.getFloat(label);\n" +
                        "                                if (resultSet.wasNull()) {\n" +
                        "                                    field.set(instance, null);\n" +
                        "                                } else {\n" +
                        "                                    field.set(instance, value);\n" +
                        "                                }\n" +
                        "                            } else if (field.getType().equals(float.class)) {\n" +
                        "                                field.set(instance, resultSet.getFloat(label));\n" +
                        "                            }\n" +
                        "                        }\n" +
                        "                    }\n" +
                        "\n" +
                        "                    result.add(instance);\n" +
                        "                } catch (InstantiationException e) {\n" +
                        "                    throw new RuntimeException(e);\n" +
                        "                } catch (IllegalAccessException e) {\n" +
                        "                    throw new RuntimeException(e);\n" +
                        "                }\n" +
                        "            }\n" +
                        "        } catch (SQLException e) {\n" +
                        "            throw new RuntimeException(e);\n" +
                        "        } finally {\n" +
                        "            if (connection!= null) {\n" +
                        "                try {\n" +
                        "                    connection.close();\n" +
                        "                } catch (SQLException e) {\n" +
                        "                    throw new RuntimeException(e);\n" +
                        "                }\n" +
                        "            }\n" +
                        "        }\n" +
                        "        return result;\n" +
                        "    }\n" +
                        "}\n");
                bufferedWriter.flush();

            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void generateDaos(JPackage rootPackage, Database database) {
        try {
            dataSourceHelper = generateDaoHelper(rootPackage);
            JDefinedClass rowMapper = generateRowMapper(rootPackage);
            JDefinedClass superDao = generateSuperDao(rootPackage, rowMapper);

            for (Table table : database.getTables()) {
                generateDao(superDao, rootPackage, table);
            }
        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private JDefinedClass generateRowMapper(JPackage rootPackage) throws JClassAlreadyExistsException {
        JDefinedClass rowMapper = rootPackage._interface("RowMapper");
        JTypeVar t = rowMapper.generify("T");

        rowMapper.method(JMod.PUBLIC, t, "map").param(codeModel.directClass("java.sql.ResultSet"), "resultSet");
        return rowMapper;
    }

    private JDefinedClass generateDaoHelper(JPackage rootPackage) throws JClassAlreadyExistsException {
        JDefinedClass dataSourceHelper = rootPackage._class(JMod.PUBLIC, "DataSourceHelper");
        JMethod getDataSource = dataSourceHelper.method(JMod.PUBLIC, codeModel.ref(DataSource.class), "getDataSource");
        JFieldVar dataSource = dataSourceHelper.field(JMod.PRIVATE, codeModel.ref(DataSource.class), "dataSource");
        getDataSource.body()._return(dataSource);
        JFieldVar instance = dataSourceHelper.field(JMod.PRIVATE | JMod.STATIC, dataSourceHelper, "instance");
        JClass gsonClass = codeModel.directClass("com.google.gson.Gson");
        JFieldVar gson = dataSourceHelper.field(JMod.PRIVATE, gsonClass, "gson", JExpr._new(gsonClass));

        dataSourceHelper.method(JMod.PUBLIC, gsonClass, "getGson").body()._return(gson);

        JMethod constructor = dataSourceHelper.constructor(JMod.PRIVATE);
        JBlock body = constructor.body();
        JTryBlock jTryBlock = body._try();
        JBlock tryBody = jTryBlock.body();

        JClass initialContextClass = codeModel.ref(InitialContext.class);
        JVar initial = tryBody.decl(initialContextClass, "initial", JExpr._new(initialContextClass));
        JInvocation lookup = initial.invoke("lookup");
        lookup.arg(configuration.getNaming().getJndi());
        tryBody.assign(dataSource, JExpr.cast(codeModel.ref(DataSource.class), lookup));
        JCatchBlock jCatchBlock = jTryBlock._catch(codeModel.ref(NamingException.class));
        JVar exception = jCatchBlock.param("e");
        JInvocation exp = JExpr._new(codeModel.ref(RuntimeException.class));
        exp.arg(exception);
        jCatchBlock.body()._throw(exp);


        JMethod getInstance = dataSourceHelper.method(JMod.PUBLIC | JMod.STATIC, dataSourceHelper, "getInstance");
        JBlock initBody = getInstance.body()._if(instance.eq(JExpr._null()))._then();
        initBody.assign(instance, JExpr._new(dataSourceHelper));
        getInstance.body()._return(instance);
        return dataSourceHelper;
    }

    private JDefinedClass generateSuperDao(JPackage rootPackage, JDefinedClass rowMapper) throws JClassAlreadyExistsException {
        JDefinedClass superDao = rootPackage._class(JMod.PUBLIC | JMod.ABSTRACT, "Dao");

        JDefinedClass daoInterface = rootPackage._interface(JMod.PUBLIC, "DaoInterface");
        JTypeVar beanClassI = daoInterface.generify("T");
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

//        JClass linkedListClass = codeModel.ref(LinkedList.class);
//        JClass beanLinkedListClass = linkedListClass.narrow(beanClass);        
        
        JClass listClass = codeModel.ref(List.class);
        JClass beanListClass = listClass.narrow(beanClass);
        
        JFieldVar dataSource = superDao.field(JMod.PRIVATE, DataSource.class, "dataSource");
        
        JMethod constructor = superDao.constructor(JMod.PUBLIC);
        constructor.body().assign(dataSource, dataSourceHelper.staticInvoke("getInstance").invoke("getDataSource"));

        JFieldVar connectionT = superDao.field(JMod.PROTECTED, Connection.class, "connectionT");
        JMethod constructorT = superDao.constructor(JMod.PUBLIC);
        constructorT.body().assign(JExpr.refthis("connectionT"), constructorT.param(Connection.class, "connectionT"));
        
        generateExecuteQuery(superDao, parameterSetter, beanArrayListClass, dataSource);
        generateExecuteQueryWithRowMap(superDao, parameterSetter, beanArrayListClass, dataSource, rowMapper);
        JMethod executeQuery = daoInterface.method(JMod.NONE, beanArrayListClass, "executeQuery");
        executeQuery.param(String.class, "sql");
        executeQuery.param(parameterSetter, "parameters");

        generateExecuteInsert(superDao, parameterSetter, idSetter, dataSource);
        generateFindFirst(superDao, parameterSetter, dataSource, beanArrayListClass, beanClass);
        JMethod findFirstI = daoInterface.method(JMod.NONE, beanClass, "findFirst");
        JVar sql = findFirstI.param(String.class, "sql");
        JVar parameters = findFirstI.param(parameterSetter, "parameters");


        generateCount(superDao, dataSource);
        generateCountRowsWithParameters(superDao, dataSource);
        generateExecuteUpdateDelete(superDao, parameterSetter, dataSource);

        generateExecuteQueryT(superDao, parameterSetter, beanListClass, beanArrayListClass, connectionT);
        generateExecuteInsertT(superDao, parameterSetter, idSetter, connectionT);
        generateCountT(superDao, connectionT);
        generateExecuteUpdateDeleteT(superDao, parameterSetter, connectionT);


        return superDao;
    }

    private void generateExecuteQueryWithRowMap(JDefinedClass superDao, JDefinedClass parameterSetter, JClass beanArrayListClass, JFieldVar dataSource, JDefinedClass rowMapper) {

        JMethod executeQuery = superDao.method(JMod.PUBLIC, Object.class, "executeQuery");
        JTypeVar m = executeQuery.generify("M");

        JClass resultListType= codeModel.ref(ArrayList.class).narrow(m);
        executeQuery.type(resultListType);
        JVar sql = executeQuery.param(String.class, "sql");
        JVar parameters = executeQuery.param(parameterSetter, "parameters");
        JVar mapper = executeQuery.param(rowMapper.narrow(m), "rowMapper");

        JBlock body = executeQuery.body();
        JVar resultList = body.decl(resultListType, "result", JExpr._new(resultListType));

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
        JInvocation add = resultList.invoke("add");
        JInvocation create = mapper.invoke("map");
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

        executeQuery.body()._return(resultList);


    }


    private void generateFindFirst(JDefinedClass superDao, JDefinedClass parameterSetter, JFieldVar dataSource, JClass beanArrayListClass, JTypeVar beanClass) {
        JMethod findFirstMethod = superDao.method(JMod.PUBLIC, beanClass, "findFirst");
        JVar sql = findFirstMethod.param(String.class, "sql");
        JVar parameters = findFirstMethod.param(parameterSetter, "parameters");
        JBlock body = findFirstMethod.body();

        JVar result = body.decl(beanArrayListClass, "result", body.invoke("executeQuery").arg(sql).arg(parameters));
        JConditional isEmpty = body._if(result.invoke("isEmpty"));
        isEmpty._then()._return(JExpr._null());
        isEmpty._else()._return(result.invoke("get").arg(JExpr.lit(0)));

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

    private void generateCountRowsWithParameters(JDefinedClass superDao, JFieldVar dataSource) {
        JMethod countRows = superDao.method(JMod.PUBLIC, codeModel.INT, "countRows");
        JVar sql = countRows.param(String.class, "sql");
        JVar paramteres = countRows.param(parameterSetter, "paramteres");
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
        tryBody._if(paramteres.ne(JExpr._null()))._then().add(paramteres.invoke("setParameters").arg(statement));
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

    private void generateCountT(JDefinedClass superDao, JFieldVar connectionT) {
        JMethod countRows = superDao.method(JMod.PUBLIC, codeModel.INT, "countRowsT");
        countRows._throws(SQLException.class);
        JVar sql = countRows.param(String.class, "sql");

        JVar count = countRows.body().decl(codeModel.INT, "count", JExpr.lit(0));
        
        JClass statementClass = codeModel.ref(PreparedStatement.class);
        JInvocation prepareStatement = connectionT.invoke("prepareStatement");
        prepareStatement.arg(sql);
        JVar statement = countRows.body().decl(statementClass, "statement", prepareStatement);
        JVar resultSet = countRows.body().decl(codeModel.ref(ResultSet.class), "result", statement.invoke("executeQuery"));
        JInvocation getInt = resultSet.invoke("getInt");
        getInt.arg(JExpr.lit(1));
        countRows.body()._if(resultSet.invoke("next"))._then().assign(count, getInt);

        countRows.body().add(statement.invoke("close"));
        
        countRows.body()._return(count);
    }    
    
    private void generateExecuteInsert(JDefinedClass superDao, JDefinedClass parameterSetter, JDefinedClass idSetter, JFieldVar dataSource) {
        String executeInsert = "executeInsert";
        generateExecute(superDao, parameterSetter, idSetter, dataSource, executeInsert);
    }

    private void generateExecuteInsertT(JDefinedClass superDao, JDefinedClass parameterSetter, JDefinedClass idSetter, JFieldVar connectionT) {
        String executeInsert = "executeInsertT";
        generateExecuteT(superDao, parameterSetter, idSetter, connectionT, executeInsert);
    }    
    
    private void generateExecuteUpdateDelete(JDefinedClass superDao, JDefinedClass parameterSetter, JFieldVar dataSource) {
        String executeInsert = "executeUpdateDelete";
        generateExecute(superDao, parameterSetter, null, dataSource, executeInsert);
    }

    private void generateExecuteUpdateDeleteT(JDefinedClass superDao, JDefinedClass parameterSetter, JFieldVar connectionT) {
        String executeInsert = "executeUpdateDeleteT";
        generateExecuteT(superDao, parameterSetter, null, connectionT, executeInsert);
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

    private void generateExecuteT(JDefinedClass superDao, JDefinedClass parameterSetter, JDefinedClass idSetter, JFieldVar connectionT, String name) {
        JMethod insertQuery = superDao.method(JMod.PROTECTED, codeModel.VOID, name);
        insertQuery._throws(SQLException.class);
        JVar sql = insertQuery.param(String.class, "sql");
        JVar parameters = insertQuery.param(parameterSetter, "parameters");

        JVar idSetterParam = null;
        if (idSetter != null) {
            idSetterParam = insertQuery.param(idSetter, "idSetter");
        }

        JClass statementClass = codeModel.ref(PreparedStatement.class);
        JInvocation prepareStatement = connectionT.invoke("prepareStatement");
        prepareStatement.arg(sql);
        if (idSetter != null) {

            prepareStatement.arg(codeModel.ref(java.sql.Statement.class).staticRef("RETURN_GENERATED_KEYS"));
        }
        JVar statement = insertQuery.body().decl(statementClass, "statement", prepareStatement);
        JInvocation setParameters = parameters.invoke("setParameters");
        setParameters.arg(statement);
        insertQuery.body()._if(parameters.ne(JExpr._null()))._then().add(setParameters);

        insertQuery.body().invoke(statement, "executeUpdate");

        if (idSetter != null) {
            JConditional ifIdSetter = insertQuery.body()._if(idSetterParam.ne(JExpr._null()));
            JBlock block = ifIdSetter._then();
            JVar keys = block.decl(codeModel.ref(ResultSet.class), "key", statement.invoke("getGeneratedKeys"));
            JInvocation param = idSetterParam.invoke("setId");
            JInvocation getInt = keys.invoke("getInt");
            getInt.arg(JExpr.lit(1));
            param.arg(getInt);
            block._if(keys.invoke("next"))._then().add(param);
        }
        
        insertQuery.body().add(statement.invoke("close"));
    }    
    
    private void generateExecuteQuery(JDefinedClass superDao, JDefinedClass parameterSetter, JClass beanArrayListClass,
                                      JFieldVar dataSource) {
        JMethod executeQuery = superDao.method(JMod.PUBLIC, beanArrayListClass, "executeQuery");
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

    private void generateExecuteQueryT(JDefinedClass superDao, JDefinedClass parameterSetter,
    		JClass beanListClass, JClass beanArrayListClass, JFieldVar connectionT) {
    	
		JMethod executeQuery = superDao.method(JMod.PROTECTED, beanListClass, "executeQueryT");
		executeQuery._throws(SQLException.class);
		JVar sql = executeQuery.param(String.class, "sql");
		JVar parameters = executeQuery.param(parameterSetter, "parameters");
		JVar result = executeQuery.body().decl(beanListClass, "result", JExpr._new(beanArrayListClass));
		
		JInvocation prepareStatement = connectionT.invoke("prepareStatement");
		prepareStatement.arg(sql);
		JVar statement =  executeQuery.body().decl(codeModel.ref(PreparedStatement.class), "statement", prepareStatement);
		JInvocation setParameterInvocation = parameters.invoke("setParameters");
		setParameterInvocation.arg(statement);
		 executeQuery.body()._if(parameters.ne(JExpr._null()))._then().add(setParameterInvocation);
		JVar resultSet =  executeQuery.body().decl(codeModel.ref(ResultSet.class), "resultSet", statement.invoke("executeQuery"));
		JWhileLoop whileLoop =  executeQuery.body()._while(resultSet.invoke("next"));
		JInvocation add = result.invoke("add");
		JInvocation create = JExpr.invoke("create");
		create.arg(resultSet);
		add.arg(create);
		whileLoop.body().add(add);
		
		executeQuery.body().add(statement.invoke("close"));
		
		executeQuery.body()._return(result);
	}    
    
    private void generateDao(JDefinedClass superDao, JPackage rootPackage, Table table) {
        try {
            JDefinedClass bean = beansMap.get(table);

            String daoFQName = rootPackage.name() + "." + namingStrategy.getPojoName(table) + "Dao";
            String daoInterfaceName = namingStrategy.getPojoName(table) + "DaoInterface";
            String daoIFQName = rootPackage.name() + "." + daoInterfaceName;
            JDefinedClass dao = codeModel._class(daoFQName);
            dao._extends(superDao.narrow(bean));
            if (configuration.isJava8interfaces()) {
                JDefinedClass jDefinedClass = dao._implements(codeModel.directClass(daoIFQName));
            }


            String path = rootPackage.name().replace(".", File.separator);

            File parent = new File(destinationDir, path);
            File file = new File(parent, daoInterfaceName + ".java");
            if (configuration.isJava8interfaces() && !file.exists()) {
                try {
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    file.createNewFile();
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                    bufferedWriter.write("package "+rootPackage.name()+";\n" +
                            "public interface " + daoInterfaceName + " extends DaoInterface<"+bean.name()+"> {\n" +
                            "\n" +
                            "}\n");
                    bufferedWriter.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //
            JMethod constructor = dao.constructor(JMod.PUBLIC);
            constructor.body().invoke("super"); 

            JMethod constructorT = dao.constructor(JMod.PUBLIC);
            JVar connectionT = constructorT.param(Connection.class, "connectionT");
            JInvocation superCall = constructorT.body().invoke("super");
            superCall.arg(connectionT);

            generateCreateMethod(table, dao, bean);
            
            generateFindAll(table, dao, bean, false);
            generateFindAllLimit(table, dao, bean, false);
            generateInsert(table, dao, bean, false);
            generateCountAll(table, dao, bean, false);

            generateFindAll(table, dao, bean, true);
            generateFindAllLimit(table, dao, bean, true);
            generateInsert(table, dao, bean, true);
            generateCountAll(table, dao, bean, true);            
            
            if (table.getPrimaryKey() != null && !table.getPrimaryKey().getColumns().isEmpty()) {
                generateUpdate(table, dao, bean, false);
                generateDelete(table, dao, bean, false);
                generateFindByPrimaryKey(table, dao, bean, false);
                
                generateUpdate(table, dao, bean, true);
                generateDelete(table, dao, bean, true);
                generateFindByPrimaryKey(table, dao, bean, true);               
            }

        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateCountAll(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
    	JMethod countAll = null;
    	if (!methodT) {
    		countAll = dao.method(JMod.PUBLIC, codeModel.INT, "countAll");
    	}
    	else {
    		countAll = dao.method(JMod.PUBLIC, codeModel.INT, "countAllT");
    		countAll._throws(SQLException.class);
    	}
        
    	String countSql = "SELECT COUNT(*) FROM " + table.getName();
        JInvocation countRows = null;
        
        if (!methodT) {
        	countRows = JExpr.invoke("countRows");
        }
        else {
        	countRows = JExpr.invoke("countRowsT");
        }
        
        countRows.arg(countSql);
        countAll.body()._return(countRows);
    }

    private void generateFindByPrimaryKey(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
        Index primaryKey = table.getPrimaryKey();
        StringBuilder keyFieldNames = new StringBuilder();
        for (Column column : primaryKey.getColumns()) {
            keyFieldNames.append(StringUtils.capitalize(column.getName()));
            keyFieldNames.append("And");
        }
        keyFieldNames.delete(keyFieldNames.length() - 3, keyFieldNames.length());

        JMethod findByPrimaryKey = null;
        if (!methodT) {
        	findByPrimaryKey = dao.method(JMod.PUBLIC, bean, "findBy" + keyFieldNames.toString());
        }
        else {
        	findByPrimaryKey = dao.method(JMod.PUBLIC, bean, "findBy" + keyFieldNames.toString() + "T");
        	findByPrimaryKey._throws(SQLException.class);
        }

        for (Column column : primaryKey.getColumns()) {
            JType type = TypeUtil.sqlTypeToJavaClass(codeModel, column, column.getType());
            findByPrimaryKey.param(JMod.FINAL, type, namingStrategy.getFieldName(column));
        }

        JInvocation queryInvocation = null;
        if (!methodT) {
        	queryInvocation = JExpr.invoke("executeQuery");
        }
        else {
        	queryInvocation = JExpr.invoke("executeQueryT");
        }
        
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
        JClass beanList = null;
        if (!methodT) {
        	beanList = codeModel.ref(ArrayList.class).narrow(bean);
        }
        else {
        	beanList = codeModel.ref(List.class).narrow(bean);
        }

        JVar result = findByPrimaryKey.body().decl(beanList, "result", queryInvocation);
        JConditional isEmpty = findByPrimaryKey.body()._if(result.invoke("isEmpty"));
        isEmpty._then()._return(JExpr._null());
        JInvocation get = result.invoke("get");
        get.arg(JExpr.lit(0));
        isEmpty._else()._return(get);

    }

    private void generateDelete(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
    	JMethod delete = null;
    	if (!methodT) {
    		delete = dao.method(JMod.PUBLIC, codeModel.VOID, "delete");
    	}
    	else {
    		delete = dao.method(JMod.PUBLIC, codeModel.VOID, "deleteT");
    		delete._throws(SQLException.class);
    	}
    	
        JVar beanObject = delete.param(JMod.FINAL, bean, StringUtils.uncapitalize(bean.name()));
        StringBuilder deleteSQL = new StringBuilder("DELETE FROM `" + table.getName() + "` WHERE ");
        addPrimaryKey(table, deleteSQL);

        JInvocation executeUpdateDelete = null;
        if (!methodT) {
        	executeUpdateDelete = delete.body().invoke("executeUpdateDelete");
        }
        else {
        	executeUpdateDelete = delete.body().invoke("executeUpdateDeleteT");
        }
        
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

    private void generateUpdate(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
    	JMethod updateMethod = null;
    	if (!methodT) {
    		updateMethod = dao.method(JMod.PUBLIC, codeModel.VOID, "update");
    	}
    	else {
    		updateMethod = dao.method(JMod.PUBLIC, codeModel.VOID, "updateT");
    		updateMethod._throws(SQLException.class);
    	}
    	
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

        JInvocation executeUpdateDelete = null;
        if (!methodT) {
        	executeUpdateDelete = updateMethod.body().invoke("executeUpdateDelete");
        }
        else {
        	executeUpdateDelete = updateMethod.body().invoke("executeUpdateDeleteT");
        }
        
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
                            if (column.getType() == Types.OTHER && column.getComment() != null) {
                                invocation.arg(dataSourceHelper.staticInvoke("getInstance").invoke("getGson").invoke("toJson").arg(beanObject.invoke(namingStrategy.getGetterName(column))));
                            } else {
                                invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                            }
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
                            if (column.getType() == Types.OTHER && column.getComment() != null) {
                                invocation.arg(dataSourceHelper.staticInvoke("getInstance").invoke("getGson").invoke("toJson").arg(beanObject.invoke(namingStrategy.getGetterName(column))));
                            } else {
                                invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                            }
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

    private void generateInsert(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
    	JMethod insert = null;
    	if (!methodT) {
    		insert = dao.method(JMod.PUBLIC, codeModel.VOID, "insert");
    	}
    	else {
    		insert = dao.method(JMod.PUBLIC, codeModel.VOID, "insertT");
    		insert._throws(SQLException.class);
    	}
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
        
        JInvocation executeInsert = null;
        if (!methodT) {
        	executeInsert = insert.body().invoke("executeInsert");
        }
        else {
        	executeInsert = insert.body().invoke("executeInsertT");
        }
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
                            if (column.getType() == Types.OTHER && column.getComment() != null) {
                                invocation.arg(dataSourceHelper.staticInvoke("getInstance").invoke("getGson").invoke("toJson").arg(beanObject.invoke(namingStrategy.getGetterName(column))));
                            } else {
                                invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                            }
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
                            if (column.getType() == Types.OTHER && column.getComment() != null) {
                                invocation.arg(dataSourceHelper.staticInvoke("getInstance").invoke("getGson").invoke("toJson").arg(beanObject.invoke(namingStrategy.getGetterName(column))));
                            } else {
                                invocation.arg(beanObject.invoke(namingStrategy.getGetterName(column)));
                            }
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

    private void generateFindAll(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
        JClass arrayListClass = codeModel.ref(ArrayList.class);
        JClass beanArrayListClass = arrayListClass.narrow(bean);
        JClass listClass = codeModel.ref(List.class);
        JClass beanListClass = listClass.narrow(bean);
        
        JMethod findAll = null;
        JInvocation queryInvocation = null;
        if (!methodT) {
        	findAll = dao.method(JMod.PUBLIC, beanArrayListClass, "findAll");
        	queryInvocation = JExpr.invoke("executeQuery");
        }
        else {
        	findAll = dao.method(JMod.PUBLIC, beanListClass, "findAllT");
        	findAll._throws(SQLException.class);
        	queryInvocation = JExpr.invoke("executeQueryT");
        }
        queryInvocation.arg(JExpr.lit("SELECT * FROM " + table.getName()));
        queryInvocation.arg(JExpr._null());
        findAll.body()._return(queryInvocation);
    }

    private void generateFindAllLimit(Table table, JDefinedClass dao, JDefinedClass bean, boolean methodT) {
        JClass arrayListClass = codeModel.ref(ArrayList.class);
        JClass beanArrayListClass = arrayListClass.narrow(bean);
        JClass listClass = codeModel.ref(List.class);
        JClass beanListClass = listClass.narrow(bean);        
        
        JMethod findAll = null;
        if (!methodT) {
        	findAll = dao.method(JMod.PUBLIC, beanArrayListClass, "findAll");
        }
        else {
        	findAll = dao.method(JMod.PUBLIC, beanListClass, "findAllT");
        	findAll._throws(SQLException.class);
        }
        findAll.param(codeModel.INT, "start");
        findAll.param(codeModel.INT, "count");
        findAll.param(String.class, "order");
        
        JInvocation queryInvocation = null;
        if (!methodT) {
        	queryInvocation = JExpr.invoke("executeQuery");
        }
        else {
        	queryInvocation = JExpr.invoke("executeQueryT");
        }
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

                createValue(createMethod, column, getValue, setValue);
            } else {
                JType dataType = TypeUtil.sqlTypeToJavaClass(codeModel, column, column.getType());
                JInvocation getValue = resultSet.invoke("get" + TypeUtil.sqlTypeToSetterGetter(column.getType()));
                getValue.arg(column.getName());
                JVar field = createMethod.body().decl(dataType, namingStrategy.getFieldName(column), getValue);
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


                createValue(createMethod, column, getValue, setValue);
            }
        }
        createMethod.body()._return(resultVariable);
    }

    private void createValue(JMethod createMethod, Column column, JInvocation getValue, JInvocation setValue) {
        if (column.getType() == Types.OTHER && column.getComment() != null) {
            setValue.arg(dataSourceHelper.staticInvoke("getInstance").invoke("getGson").invoke("fromJson").arg(getValue).arg(JExpr.dotclass(codeModel.directClass(column.getComment()))));
            createMethod.body().add(setValue);
        } else {
            setValue.arg(getValue);
            createMethod.body().add(setValue);
        }
    }

    private void generateBeans(JPackage rootPackage, Database database) {
        for (Table table : database.getTables()) {
            generateBean(rootPackage, table);
        }
    }

    private void generateBean(JPackage rootPackage, Table table) {
        try {
            JDefinedClass bean = codeModel._class(rootPackage.name() + "." + namingStrategy.getPojoName(table));

            List<String> beanInterfaces = configuration.getBeanInterface();
            for (String beanInterface : beanInterfaces) {
                bean._implements(codeModel.directClass(beanInterface));
            }

            for (Column column : table.getColumns()) {
                JType type = TypeUtil.sqlTypeToJavaClass(codeModel, column, column.getType());

                JType usedType = type;

                if (!column.isAutoIncrement() && !column.isNullable()) {
                    JType unboxify = type.unboxify();
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
