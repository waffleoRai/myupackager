package waffleoRai_extractMyu.mains;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import waffleoRai_Containers.ISO;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class CDCompare {
	
	public static void main_cdCompare(Map<String, String> argmap) throws IOException {
		String refpath = argmap.get("ref");
		String querypath = argmap.get("query");
		
		BufferedInputStream refStr = new BufferedInputStream(new FileInputStream(refpath));
		BufferedInputStream qryStr = new BufferedInputStream(new FileInputStream(querypath));
		long cpos = 0;
		int sector = 0;
		int secPos = 0;
		
		int b0 = refStr.read();
		int b1 = qryStr.read();
		while(b0 == b1) {
			if(b0 == -1) break;
			if(b1 == -1) break;
			
			cpos++;
			if(++secPos >= ISO.SECSIZE) {
				sector++;
				secPos = 0;
			}
			
			b0 = refStr.read();
			b1 = qryStr.read();
		}
		
		refStr.close();
		qryStr.close();
		
		long fsizeRef = FileBuffer.fileSize(refpath);
		long fsizeQry = FileBuffer.fileSize(querypath);
		if(cpos == fsizeRef && cpos == fsizeQry) {
			MyuPackagerLogger.logMessage("CDCompare.main_cdCompare", 
					"[O] CD images match!");
			return;
		}
		
		MyuPackagerLogger.logMessage("CDCompare.main_cdCompare", 
				"[X] CD images do not match!");
		MyuPackagerLogger.logMessage("CDCompare.main_cdCompare", 
				String.format("First mismatch: 0x%x (Sector %d @ 0x%03x)", cpos ,sector, secPos));
		
	}

}
