package waffleoRai_extractMyu;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MyuPackagerLogger {
	
	private static boolean log_is_stderr = false;
	private static BufferedWriter log;

	public static void logMessage(String func, String message){
		try{
			log.write("[" + func + "] ");
			log.write(message);
			log.write("\n");
			log.flush();
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
	}
	
	public static void openLog(String path) throws IOException{
		log = new BufferedWriter(new FileWriter(path));
		log_is_stderr = false;
	}
	
	public static void openLogStdErr(){
		//System.err.println("log to stderr");
		log = new BufferedWriter(new OutputStreamWriter(System.err));
		log_is_stderr = true;
	}
	
	public static void closeLog() throws IOException{
		if(log_is_stderr) log.flush();
		else log.close();
	}
	
}
