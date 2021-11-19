package org.sag.woof;

import org.sag.acminer.IACMinerDataAccessor;
import org.sag.woof.database.androidapi.IAndroidAPIDatabase;
import org.sag.woof.database.filemethods.IFileMethodsDatabase;
import org.sag.woof.database.messagehandlers.IMessageHandlerDatabase;

public interface IWoofDataAccessor extends IACMinerDataAccessor {

	IAndroidAPIDatabase getAndroidAPIDB();

	void setAndroidAPIDB(IAndroidAPIDatabase db);

	IFileMethodsDatabase getFileMethodsDB();

	void setFileMethodsDB(IFileMethodsDatabase db);

	IMessageHandlerDatabase getMessageHandlerDB();

	void setMessageHandlerDB(IMessageHandlerDatabase db);

}