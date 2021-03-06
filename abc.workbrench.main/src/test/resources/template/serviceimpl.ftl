package ${packageBase}.service;

import ${packageBase}.model.${tableBean.tableNameCapitalized}${pojoSuffix};
import ${packageBase}.dao.${tableBean.tableNameCapitalized}${classPrefix}${daoIntefaceSuffix};

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import com.ndlan.canyin.main.service.BaseService;

@Transactional
@Component("${tableBean.tableNameNoDash}${classPrefix}${serviceIntefaceSuffix}")
public class ${tableBean.tableNameCapitalized}${classPrefix}${classSuffix}  extends BaseService<${tableBean.tableNameCapitalized}${classPrefix}${daoIntefaceSuffix}, 
	${tableBean.tableNameCapitalized}${pojoSuffix}>      implements ${tableBean.tableNameCapitalized}${serviceIntefaceSuffix} {

    @Resource(name="${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}")
    protected ${tableBean.tableNameCapitalized}${classPrefix}${daoIntefaceSuffix} ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix};

    @Override
    public int save${tableBean.tableNameCapitalized}${pojoSuffix}(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
        trimStringValue(${tableBean.tableNameNoDash});
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.insertSelective(${tableBean.tableNameNoDash});
    }

    @Override
    public int saveAndGetId(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
        trimStringValue(${tableBean.tableNameNoDash});
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.insertSelectiveAndGetId(${tableBean.tableNameNoDash});
    }

    @Override
    public int update(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
        trimStringValue(${tableBean.tableNameNoDash});
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.updateByPrimaryKeySelective(${tableBean.tableNameNoDash});
    }

    @Override
    public int saveOrUpdate${tableBean.tableNameCapitalized}${pojoSuffix}(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
        if (null == ${tableBean.tableNameNoDash}.get${tableBean.pkColumnNameCapitalized}() ||
		"" == ${tableBean.tableNameNoDash}.get${tableBean.pkColumnNameCapitalized}()) {
            return save${tableBean.tableNameCapitalized}${pojoSuffix}(${tableBean.tableNameNoDash});
        } else {
            return update(${tableBean.tableNameNoDash});
        }
    }

    @Override
    public ${tableBean.tableNameCapitalized}${pojoSuffix} get${tableBean.tableNameCapitalized}${pojoSuffix}(${tableBean.pkType} ${tableBean.pkName}) {
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.selectByPrimaryKey(${tableBean.pkName});
    }

    @Override
    public List<${tableBean.tableNameCapitalized}${pojoSuffix}> getAll() {
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.selectAll();
    }

    @Override
    public void delete(${tableBean.pkType} ${tableBean.pkName}) {
         ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.deleteByPrimaryKey(${tableBean.pkName});
    }

    public List<${tableBean.tableNameCapitalized}${pojoSuffix}> query${tableBean.tableNameCapitalized}${pojoSuffix}
	(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}, Long startPos, Long num){
	SQLParam sqlParam=getWhereSQL(${tableBean.tableNameNoDash});
	String whereSql=sqlParam.where ;
	Object [] params=sqlParam.params;
	return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.selectByWhereSql( whereSql,  params,  startPos,  num);
    }

    public int deleteByWhereSql(String whereSql, Object[] params){
	return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.deleteByWhereSql(whereSql, params);
    }
     
    @Override
    public int update(String sql, Object... args) {
        return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.update(sql, args);
    }

    public List<${tableBean.tableNameCapitalized}${pojoSuffix}> query${tableBean.tableNameCapitalized}${pojoSuffix}
	(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}){
	SQLParam sqlParam=getWhereSQL(${tableBean.tableNameNoDash});
	String whereSql=sqlParam.where ;
	Object [] params=sqlParam.params;
	
	return ${tableBean.tableNameNoDash}${classPrefix}${daoIntefaceSuffix}.selectByWhereSql(whereSql, params);
    }

     public SQLParam getWhereSQL(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
	StringBuffer sqlBuffer=new StringBuffer();
	List<Object> param=new ArrayList<Object>();
	SQLParam sqlParam=new SQLParam();
	<#list tableBean.columnBeanList as columnBean>
         <#if columnBean.columnType == "String"> 
        String ${columnBean.columnNameNoDash} = ${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}();
        if (StringUtils.isNotBlank(${columnBean.columnNameNoDash}) ) {
           sqlBuffer.append("${columnBean.columnNameNoDash}=?");
	    <#if columnBean_has_next>sqlBuffer.append(" and ");</#if>
	     param.add(${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}());
        }
        <#elseif columnBean.columnType == "Date"&&columnBean.columnType == "BigDecimal"> 
        ${columnBean.columnType} ${columnBean.columnNameNoDash} = ${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}();
        if (${columnBean.columnNameNoDash}!=null ) {
           sqlBuffer.append("${columnBean.columnNameNoDash}=?");
	    <#if columnBean_has_next>sqlBuffer.append(" and ");</#if>
	     param.add(${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}());
        }
	<#else>
	${columnBean.columnType} ${columnBean.columnNameNoDash} = ${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}();
	if (${columnBean.columnNameNoDash}!=null  ) {
           sqlBuffer.append("${columnBean.columnNameNoDash}=?");
	    <#if columnBean_has_next>sqlBuffer.append(" and ");</#if>
	     param.add(${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}());
        } 
	</#if> 
      
            
        </#list>
	
	String sql=sqlBuffer.toString();
	sql=StringUtils.trim(sql);//sql.trim()
	if(sql.endsWith("and")){
		sql=sql.substring(0, sql.lastIndexOf("and"));
	}
	sqlParam.where=sql;
	sqlParam.params=param.toArray(new Object[0]);
	return sqlParam;
     }

    private class  SQLParam {
	String where;
	Object[] params;
    }
    @Override
    public ${tableBean.tableNameCapitalized}${pojoSuffix} trimStringValue(${tableBean.tableNameCapitalized}${pojoSuffix} ${tableBean.tableNameNoDash}) {
        <#list tableBean.columnBeanList as columnBean>
            <#if columnBean.columnType == "String">
        String ${columnBean.columnNameNoDash} = ${tableBean.tableNameNoDash}.get${columnBean.columnNameCapitalized}();
        if (StringUtils.isNotBlank(${columnBean.columnNameNoDash}) && ${columnBean.columnNameNoDash}.length() > ${columnBean.length?c}) {
            ${tableBean.tableNameNoDash}.set${columnBean.columnNameCapitalized}(${columnBean.columnNameNoDash}.substring(0, ${columnBean.length?c}));
        }

            </#if>
        </#list>
        return ${tableBean.tableNameNoDash};
    }
}
