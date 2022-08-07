package org.sag.fred;

import org.sag.acminer.IACMinerDataAccessor;
import org.sag.fred.database.androidapi.IAndroidAPIDatabase;
import org.sag.fred.database.filemethods.IFileMethodsDatabase;
import org.sag.fred.database.messagehandlers.IMessageHandlerDatabase;

public interface IFredDataAccessor extends IACMinerDataAccessor {

	IAndroidAPIDatabase getAndroidAPIDB();

	void setAndroidAPIDB(IAndroidAPIDatabase db);

	IFileMethodsDatabase getFileMethodsDB();

	void setFileMethodsDB(IFileMethodsDatabase db);

	IMessageHandlerDatabase getMessageHandlerDB();

	void setMessageHandlerDB(IMessageHandlerDatabase db);

}