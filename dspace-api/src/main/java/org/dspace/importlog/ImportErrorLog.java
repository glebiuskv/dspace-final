package org.dspace.importlog;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

public class ImportErrorLog extends DSpaceObject {

	private static final Logger log = Logger.getLogger(ImportErrorLog.class);
	private final TableRow myRow;
	private static final String tableName = "import_error_log";

	public ImportErrorLog(Context context, TableRow row) throws SQLException {
		super(context);

		// Ensure that my TableRow is typed.
		if (null == row.getTable())
			row.setTable(tableName);

		myRow = row;

		clearDetails();
	}

	/**
	 * Creates new folder
	 * @param context Context
	 * @return Folder instance
	 * @throws SQLException
	 * @throws AuthorizeException
	 */
	public static ImportErrorLog create(Context context, String importId) throws SQLException {
		// Create a table row
		TableRow row = DatabaseManager.create(context, tableName);
		ImportErrorLog f = new ImportErrorLog(context, row);
		f.setDate(new Date());
		f.setImportId(importId);
		
		log.info(LogManager.getHeader(context, "create_import_error_log_record", "record_id=" + f.getID()));
		
		return f;
	}
	
	@Override
	public int getType() {
		return 0;
	}

	@Override
	public int getID() {
		return myRow.getIntColumn("id");
	}

	@Override
	public String getHandle() {
		return null;
	}

	@Override
	public void update() throws SQLException {
		DatabaseManager.update(ourContext, myRow);
		log.info(LogManager.getHeader(ourContext, "update_import_error_log", "record_id=" + getID()));

	}

	@Override
	public void updateLastModified() {
	}
	
	public Date getDate() {
		return myRow.getDateColumn("date");
	}
	
	public void setDate(Date date) {
		myRow.setColumn("date", date);
	}
	
	public String getFile() {
		return myRow.getStringColumn("file");
	}
	
	public void setFile(String file) {
		myRow.setColumn("file", file);
	}

	@Override
	public String getName() {
		return null;
	}
	
	public String getImportId() {
		return myRow.getStringColumn("import_id");
	}
	
	public void setImportId(String importId) {
		myRow.setColumn("import_id", importId);
	}
	
	public static ImportErrorLog[] getReport(Context context, String importId, int offset, int limit) throws SQLException {
		TableRowIterator rows = DatabaseManager.query(context, "SELECT * FROM " + tableName + " WHERE import_id=? ORDER BY date ASC OFFSET ? LIMIT ?", importId, new Integer(offset), new Integer(limit));
		try {
            List<TableRow> gRows = rows.toList();
            ImportErrorLog[] folders = new ImportErrorLog[gRows.size()];

            for (int i = 0; i < gRows.size(); i++) {
                TableRow row = gRows.get(i);
                folders[i] = new ImportErrorLog(context, row);
            }

            return folders;
        } finally {
            if (rows != null) {
                rows.close();
            }
        }
	}
	
	public static int getReportLength(Context context, String importId) throws SQLException {
		TableRowIterator rows = DatabaseManager.query(context, "SELECT COUNT(*) as count FROM " + tableName + " WHERE import_id=?", importId);
		return rows.next().getIntColumn("count");
	}
	
	public static String[] getReportsId(Context context, Date date) throws SQLException {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		Timestamp from = new Timestamp(cal.getTimeInMillis());
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.SECOND, 59);
		Timestamp till = new Timestamp(cal.getTimeInMillis());
		TableRowIterator rows = DatabaseManager.query(context, "SELECT import_id FROM (SELECT DISTINCT ON (import_id) import_id, date FROM " + tableName + " WHERE date >= ? AND date <= ?) l ORDER BY date DESC", from, till);
		try {
            List<TableRow> gRows = rows.toList();
            String[] result = new String[gRows.size()];

            for (int i = 0; i < gRows.size(); i++) {
                TableRow row = gRows.get(i);
                result[i] = row.getStringColumn("import_id");
            }

            return result;
        } finally {
            if (rows != null) {
                rows.close();
            }
        }
	}

}
