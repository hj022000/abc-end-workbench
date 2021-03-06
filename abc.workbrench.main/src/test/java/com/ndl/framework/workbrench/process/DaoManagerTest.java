package com.ndl.framework.workbrench.process;

import static org.junit.Assert.assertNotNull;

import java.util.Set;

import org.junit.Test;
import org.springframework.util.Assert;

import com.abc.framework.workbrench.define.AnnotationBean;
import com.abc.framework.workbrench.define.AtomOrRepositoryPackage;
import com.abc.framework.workbrench.define.ColumnBean;
import com.abc.framework.workbrench.define.DaoBean;
import com.abc.framework.workbrench.define.FieldRativeEnum;
import com.abc.framework.workbrench.define.FieldTypeEnum;
import com.abc.framework.workbrench.define.MethCategoryEnum;
import com.abc.framework.workbrench.define.ServiceBean;
import com.abc.framework.workbrench.freemarker.RunConfigure;
import com.abc.framework.workbrench.process.DaoManager;

public class DaoManagerTest {
	
	@Test
	public void parseRepositoryManagerFromXML(){
		DaoManager.generateDaoTemplateToXML("testDaoPackage");
	}
	@Test
	public void generateDaoMethodManagerToXML() {
		try {
			// 基本信息
			// Controller签名
			String daoType = "Login";
			String descripter = "index login addLogin createLogin deleteLogin updateLogin";
			String methodSignature = "queryLoginByUserNameAndPassword";
			String methodSQLKey = "queryLogin";
			String methodSignatureDescripter = "toLogin登录认证";
			ColumnBean startColumnBean = new ColumnBean();
			startColumnBean.setColumnName("Name");
			startColumnBean.setColumnType("String");
			startColumnBean.setNextFieldRative(FieldRativeEnum.And);

			ColumnBean nextColumnBean = new ColumnBean();
			nextColumnBean.setColumnName("Password");
			nextColumnBean.setColumnType("String");
			nextColumnBean.setNextFieldRative(FieldRativeEnum.Finished);

			startColumnBean.setNextColumnBean(nextColumnBean);

			//FieldTypeEnum returnFieldEnum = FieldTypeEnum.ENTITY;

			AnnotationBean daoTypeAnnotation = null;
			MethCategoryEnum methodCategory = RunConfigure.JPACategory;
			DaoManager manager = DaoManager.addDaoMethod(daoType, descripter, daoTypeAnnotation, methodSQLKey,
					methodSignature, methodSignatureDescripter, startColumnBean, methodCategory, FieldTypeEnum.ENTITY);
			assertNotNull(manager);
			manager.generateDaoBeanToXML("testDaoPackage");
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	@Test
	public void testAddDaoMethod() {
		try {
			// 基本信息
			// Controller签名
			String daoType = "Login";
			String descripter = "index login addLogin createLogin deleteLogin updateLogin";
			String methodSignature = "queryLoginByUserNameAndPassword";
			String methodSQLKey = "queryLogin";
			String methodSignatureDescripter = "toLogin登录认证";
			ColumnBean startColumnBean = new ColumnBean();
			startColumnBean.setColumnName("Name");
			startColumnBean.setColumnType("String");
			startColumnBean.setNextFieldRative(FieldRativeEnum.And);

			ColumnBean nextColumnBean = new ColumnBean();
			nextColumnBean.setColumnName("Password");
			nextColumnBean.setColumnType("String");
			nextColumnBean.setNextFieldRative(FieldRativeEnum.Finished);

			startColumnBean.setNextColumnBean(nextColumnBean);

			FieldTypeEnum returnFieldEnum = FieldTypeEnum.ENTITY;

			AnnotationBean daoTypeAnnotation = null;
			MethCategoryEnum methodCategory = RunConfigure.JPACategory;
			DaoManager manager = DaoManager.addDaoMethod(daoType, descripter, daoTypeAnnotation, methodSQLKey,
					methodSignature, methodSignatureDescripter, startColumnBean, methodCategory, returnFieldEnum);
			assertNotNull(manager);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
