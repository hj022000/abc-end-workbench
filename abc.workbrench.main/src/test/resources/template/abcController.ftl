package ${packageBase}.${packageRest};

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ndlan.framework.core.api.BusinessService;
import com.ndlan.framework.core.web.restful.BaseBusinessControllerImpl;

import ${defaultBeanPackage}.${packageModel}.${tableBean.tableNameCapitalized}${classSuffix};

import ${defaultBeanPackage}.${packageModelQuery}.${tableBean.tableNameCapitalized}${classQuerySuffix};


@Controller
@RequestMapping("/business/${parseControllerPath}")
public class ${tableBean.tableNameCapitalized}${classSuffix}${restSuffix} 
extends BaseBusinessControllerImpl<${tableBean.tableNameCapitalized}${classSuffix}, ${tableBean.tableNameCapitalized}${classQuerySuffix}> {

	@Autowired(required=true)
	@Qualifier(value="${tableBean.tableNameNoDash}${classSuffix}${businessServiceSuffix}")
	private BusinessService ${tableBean.tableNameNoDash}${classSuffix}${businessServiceSuffix};

	protected BusinessService<${tableBean.tableNameCapitalized}${classSuffix}> getBaseService(){
		return ${tableBean.tableNameNoDash}${classSuffix}${businessServiceSuffix};
	}

}

