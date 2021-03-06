package org.dspace.folder;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.dspace.core.Context;
import org.dspace.importlog.ImportErrorLog;
import org.dspace.util.MfuaXmlParser;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.ibm.icu.util.Calendar;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

public class FolderServiceImpl implements FolderService {
	
	private static final Logger logger = (Logger) LoggerFactory.getLogger(FolderServiceImpl.class);
	private Context context;
	private Scheduler scheduler = new Scheduler();
	private Map<Integer, String> schedules = new HashMap<Integer,String>();

	@Override
	public void init() throws SQLException {
		logger.info("Initializing folder service");
		context = new Context();
		scheduler.setDaemon(true);
		scheduler.start();
		
		reloadSchedules();
	}
	
	@Override
	public void reloadSchedules() throws SQLException {
		logger.info("Reloading schedules");
		
		//Cleaning schedules
		for (String id: schedules.values()) {
			scheduler.deschedule(id);
		}
		schedules.clear();
		
		ImportFolder[] folders = ImportFolder.findAll(context);
		for (ImportFolder folder: folders) {
			//Creating schedule pattern
			StringBuilder pattern = new StringBuilder();
			if (folder.getMinute() != null) {
				pattern.append(folder.getMinute());
			} else {
				pattern.append("*");
			}
			pattern.append(" ");
			if (folder.getHour() != null) {
				pattern.append(folder.getHour());
			} else {
				pattern.append("*");
			}
			pattern.append(" ");
			if (folder.getDate() != null) {
				pattern.append(folder.getDate());
			} else {
				pattern.append("*");
			}
			pattern.append(" ");
			if (folder.getMonth() != null) {
				pattern.append(folder.getMonth());
			} else {
				pattern.append("*");
			}
			pattern.append(" ");
			if (folder.getWeekday() != null) {
				pattern.append(folder.getWeekday());
			} else {
				pattern.append("*");
			}
			pattern.append(" ");
			
			ImportTask task = new ImportTask(folder.getPath(), folder.getYear());
			String scheduleId = scheduler.schedule(pattern.toString(), task);
			schedules.put(folder.getID(), scheduleId);
			Log.info("New import task " + scheduleId);
		}
	}
	
	@Override
	public TaskExecutor execute(int id) {
		ImportTask task = (ImportTask) scheduler.getTask(schedules.get(id));
		ImportTask manualTask = new ImportTask(task.getPath(), null);
		return scheduler.launch(manualTask);
	}
	
	class ImportTask extends Task {
		
		private String path;
		private Integer year;
		private String importId;
		
		public ImportTask(String path, Integer year) {
			this.path = path;
			this.year = year;
		}
		
		public String getPath() {
			return path;
		}

		@Override
		public void execute(TaskExecutionContext context) throws RuntimeException {
			logger.info("Executing import task for path " + path);
			//Checking year
			if (year != null) {
				Calendar cal = Calendar.getInstance();
				if (cal.get(Calendar.YEAR) != year)
					return;
			}
			
			importId = UUID.randomUUID().toString();
			
			parseDir(new File(path));
			
			logger.info("Finished import path for path " + path);
		}
		
		private void parseDir(File dir) {
			File[] list = dir.listFiles();
			for (File item: list) {
				if (item.isDirectory()) {
					parseDir(item);
				} else if (FilenameUtils.getExtension(item.getName()).equalsIgnoreCase("xml")) {
					try {
						DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
						f.setValidating(false);
						DocumentBuilder builder = f.newDocumentBuilder();
						Document doc = builder.parse(item);
						Document parsedDocument = MfuaXmlParser.createItems(doc, context, null, importId, item);
						if (parsedDocument != null) {
							File outFile = convertPath(item, "out");
							logger.debug("Moving xml source directory into out direcoty: " + outFile.getParentFile().getAbsolutePath());
							outFile.getParentFile().mkdirs();
							item.getParentFile().renameTo(outFile.getParentFile());
							TransformerFactory transformerFactory = TransformerFactory.newInstance();
							Transformer transformer = transformerFactory.newTransformer();
							DOMSource source = new DOMSource(parsedDocument);
							StreamResult streamResult =  new StreamResult(outFile);
							transformer.transform(source, streamResult);
						} else {
							throw new Exception("Unable to import XML");
						}
					} catch (Exception e) {
						logger.warn("Import error", e);
						
						//Moving file to error directory
						File errorFile = convertPath(item, "error");
						logger.debug("Moving xml source directory into error direcoty: " + errorFile.getParentFile().getAbsolutePath());
						errorFile.getParentFile().mkdirs();
						item.getParentFile().renameTo(errorFile.getParentFile());
						
						try {
							ImportErrorLog errorLog = ImportErrorLog.create(context, importId);
							errorLog.setFile(errorFile.getAbsolutePath());
							errorLog.update();
						} catch (Exception e2) {
							logger.error("Unable to log import error", e2);
						}
					}
					
					//Removing original file parent directory with all files inside
					File itemDir = item.getParentFile();
					logger.debug("Removing parent directory: " + itemDir.getAbsolutePath());
					itemDir.delete();
				}
			}
			
			try {
				context.commit();
			} catch (SQLException e) {
				logger.warn("Unknown error", e);
			}
		}
		
		private File convertPath(File file, String dir) {
			String filePath = file.getAbsolutePath();
			String[] pathParts = path.split("/");
			StringBuilder newPath = new StringBuilder();
			for (int i = 0; i < (pathParts.length - 1); i++) {
				if (i > 0) {
					newPath.append("/");
				}
				newPath.append(pathParts[i]);
			}
			newPath.append("/" + dir);
			return new File(filePath.replaceFirst("^" + path, newPath.toString()));
		}
		
	}
	
}
