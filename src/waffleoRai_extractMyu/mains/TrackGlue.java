package waffleoRai_extractMyu.mains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

import waffleoRai_Containers.CDTable.CDInvalidRecordException;
import waffleoRai_Containers.ISOXAImage;
import waffleoRai_Containers.XATable;
import waffleoRai_Containers.XATable.XAEntry;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class TrackGlue {
	
	//ISOBuster puts track 2 at sector 250996
	//Luckily the MYUVOICE.DA file also starts at 250996
	//The last file on track 1 is MOVIE.BIN
	
	private static int[] absSec2Time(int sector) {
		int[] time = new int[3];
		time[2] = sector % 75;
		sector /= 75;
		time[1] = sector % 60;
		sector /= 60;
		time[0] = sector;
		
		return time;
	}
	
	private static void glueTracks(String path_t1, String path_t2, String outstem) throws IOException, CDInvalidRecordException, UnsupportedFileTypeException{
		
		if(path_t1 == null) {
			MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
					"Track 1 path was not provided. Exiting...");
			return;
		}
		if(path_t2 == null) {
			MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
					"Track 2 path was not provided. Exiting...");
			return;
		}
		if(outstem == null) {
			MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
					"Output path was not provided. Exiting...");
			return;
		}
		
		MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
				"Attempting to read track 1...");
		ISOXAImage t1 = MyuArcCommon.readISOFile(path_t1);		
		XATable tbl = t1.getXATable();
		MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
				"ISO Image read. Reading table...");
		
		int da_sec = -1;
		Collection<XAEntry> entries = tbl.getXAEntries();
		for(XAEntry e : entries) {
			MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
					"Entry -- " + e.getName() + " @ sector " + e.getStartBlock());
			if(e.getName().endsWith(".DA")) {
				da_sec = e.getStartBlock();
			}
		}
		
		if(da_sec < 0) {
			MyuPackagerLogger.logMessage("TrackGlue.glueTracks", 
					"MYUVOICE.DA table entry was not found! Quitting...");
			return;
		}
		
		int t1_secs = (int)(FileBuffer.fileSize(path_t1) / 0x930);
		
		//Output the bin file.
		String bin_out = outstem + ".iso";
		Files.copy(Paths.get(path_t1), Paths.get(bin_out));
		FileBuffer t2dat = FileBuffer.createBuffer(path_t2);
		t2dat.appendToFile(bin_out);
		
		//Output the cue file
		String cue_out = outstem + ".cue";
		String bin_name = bin_out.substring(bin_out.lastIndexOf(File.separatorChar) + 1);
		BufferedWriter bw = new BufferedWriter(new FileWriter(cue_out));
		bw.write("FILE \"" + bin_name + "\" BINARY\n");
		bw.write("\tTRACK 01 MODE2/2352\n");
		bw.write("\t\tINDEX 01 00:00:00\n");
		bw.write("\tTRACK 02 AUDIO\n");
		if(da_sec > t1_secs) {
			//Gap
			int secdiff = da_sec - t1_secs;
			int[] timediff = absSec2Time(secdiff);
			bw.write(String.format("\t\tPREGAP %02d:%02d:%02d\n", timediff[0], timediff[1], timediff[2]));
		}
		int[] t2time = absSec2Time(t1_secs);
		bw.write(String.format("\t\tINDEX 01 %02d:%02d:%02d\n", t2time[0], t2time[1], t2time[2]));
		
		bw.close();
	}
	
	public static void main_glueTracks(Map<String, String> args) throws IOException, CDInvalidRecordException, UnsupportedFileTypeException{
		String path_t1 = args.get("track1");
		String path_t2 = args.get("track2");
		String outstem = args.get("outstem");
		//boolean skip_gap = args.containsKey("skipgap");
		
		glueTracks(path_t1, path_t2, outstem);
	}

}
