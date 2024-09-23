package waffleoRai_extractMyu.mains;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Containers.CDDateTime;
import waffleoRai_Containers.ISO;
import waffleoRai_Containers.ISOUtils;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.StringUtils;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.CDBuildContext;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuCD;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.MyupkgConstants;

public class IsoBuild {
	
	private static final String CD_IDSTR = "CD001";
	private static final String CDXA_IDSTR = "CD-XA001";
	private static final String SYS_IDSTR = "PLAYSTATION";
	
	public static class WTrack{
		public int startSec;
		public boolean isAudio = false;
		public List<LiteNode> files;
		
		public WTrack() {
			files = new ArrayList<LiteNode>(32); 
		}
	}
	
	public static class WFile{
		public String fileName;
		public String filePath;
		public long fileSize;
		public int fsizeSecs;
		public char embedType = 'S';
		public CDDateTime timestamp;
		public int perm = 0x555; //One triplet in each nibble
		public int groupId = 0;
		public int userId = 0;
		public int fileNo = 0;
		public boolean interleaved = false;
		public int startSec;
		public String hash;
		public int ver = 1; //Tacked to end of file name
		public boolean isForm2;
		public int leadInSize = 0;
		public int leadOutSize = 0;
	}
	
	private static boolean checkArgs(CDBuildContext ctx, Map<String, String> args) {
		ctx.input_xml = args.get("spec");
		ctx.output_iso = args.get("out");
		ctx.checksum_path = args.get("checksums");
		ctx.build_dir = args.get("builddir");
		ctx.incl_dir = args.get("incldir");
		ctx.src_dir = args.get("cdir");
		ctx.matchFlag = args.containsKey("match");
		ctx.buildAllFlag = args.containsKey("buildall");
		ctx.arcOnlyFlag = args.containsKey("arconly");
		
		if(ctx.input_xml == null){
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"XML specification file is required as input!");
			return false;
		}
		
		ctx.wd = MyuArcCommon.getContainingDir(ctx.input_xml);
		if(ctx.build_dir == null) {
			ctx.build_dir = ctx.wd + File.separator + "cd";
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Build staging directory not provided. Set to \"" + ctx.build_dir + "\"");
		}
		if(ctx.output_iso == null) {
			ctx.output_iso = ctx.wd + File.separator + "build.iso";
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Output path not provided. Set to \"" + ctx.output_iso + "\"");
		}
		if(ctx.checksum_path == null) {
			ctx.checksum_path = ctx.wd + File.separator + "checksums.csv";
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Checksum table path not provided. Set to \"" + ctx.checksum_path + "\"");
		}
		if(ctx.incl_dir == null) {
			ctx.incl_dir = ctx.wd + File.separator + "include";
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Include dir path not provided. Set to \"" + ctx.incl_dir + "\"");
		}
		if(ctx.src_dir == null) {
			ctx.src_dir = ctx.wd + File.separator + "src";
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"C dir path not provided. Set to \"" + ctx.src_dir + "\"");
		}
		
		if(!FileBuffer.directoryExists(ctx.build_dir)) {
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Build staging directory \"" + ctx.build_dir + "\" not found. Creating...");
			try {Files.createDirectories(Paths.get(ctx.build_dir));}
			catch(IOException ex) {ex.printStackTrace(); return false;}
		}
		if(!FileBuffer.directoryExists(ctx.incl_dir)) {
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"Include directory \"" + ctx.incl_dir + "\" not found. Creating...");
			try {Files.createDirectories(Paths.get(ctx.incl_dir));}
			catch(IOException ex) {ex.printStackTrace(); return false;}
		}
		if(!FileBuffer.directoryExists(ctx.src_dir)) {
			MyuPackagerLogger.logMessage("IsoBuild.checkArgs", 
					"C directory \"" + ctx.src_dir + "\" not found. Creating...");
			try {Files.createDirectories(Paths.get(ctx.src_dir));}
			catch(IOException ex) {ex.printStackTrace(); return false;}
		}
		
		return true;
	}
	
	private static FileBuffer serializeVolDescSector(CDBuildContext ctx) {
		if(ctx == null) return null;
		FileBuffer buff = FileBuffer.wrap(MyuCD.genDummySecBaseI_M2F1()); //Zero filled with headers and stuff.
		MyuCD.updateSectorNumber(buff, ctx.currentSecAbs);
		
		//Weird subheader flags.
		buff.replaceByte((byte)0x09, 0x12L);
		buff.replaceByte((byte)0x09, 0x16L);
		
		long cpos = 0x18;
		buff.replaceByte((byte)0x01, cpos++); //Primary volume descriptor
		for(int i = 0; i < CD_IDSTR.length(); i++) buff.replaceByte((byte)(CD_IDSTR.charAt(i)), cpos++); //Standard identifier
		buff.replaceByte((byte)0x01, cpos++); //Version (usually 1)
		buff.replaceByte((byte)0x00, cpos++);
		for(int i = 0; i < SYS_IDSTR.length(); i++) buff.replaceByte((byte)(SYS_IDSTR.charAt(i)), cpos++);
		int pad = 0x20 - SYS_IDSTR.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		
		String aval = ctx.isoInfo.attr.get(MyupkgConstants.XML_ATTR_VOLUMEID);
		String volid = "SLUS_00000";
		if(aval != null) volid = aval;
		for(int i = 0; i < volid.length(); i++) buff.replaceByte((byte)(volid.charAt(i)), cpos++);
		pad = 0x20 - volid.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		
		cpos += 8; //Reserved
		buff.setEndian(false); buff.replaceInt(ctx.totalSectors, cpos); cpos += 4; //Total size in sectors
		buff.setEndian(true); buff.replaceInt(ctx.totalSectors, cpos); cpos += 4;
		cpos += 0x20; //Reserved
		
		buff.setEndian(false); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.setEndian(true); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.setEndian(false); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.setEndian(true); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.setEndian(false); buff.replaceShort((short)ISO.F1SIZE, cpos); cpos += 2;
		buff.setEndian(true); buff.replaceShort((short)ISO.F1SIZE, cpos); cpos += 2;
		
		buff.setEndian(false); buff.replaceInt(ctx.ptSize, cpos); cpos += 4;
		buff.setEndian(true); buff.replaceInt(ctx.ptSize, cpos); cpos += 4;
		buff.setEndian(false); buff.replaceInt(ctx.ptSecs[0], cpos); cpos += 4;
		buff.replaceInt(ctx.ptSecs[1], cpos); cpos += 4;
		buff.setEndian(true); buff.replaceInt(ctx.ptSecs[2], cpos); cpos += 4;
		buff.replaceInt(ctx.ptSecs[3], cpos); cpos += 4;
		
		//Root record should always be same size
		buff.replaceByte((byte)0x22, cpos++); cpos++;
		buff.setEndian(false); buff.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
		buff.setEndian(true); buff.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
		buff.setEndian(false); buff.replaceInt(ISO.F1SIZE, cpos); cpos += 4; //This is size. Update eventually in case someone goes and adds a bunch of files for some reason.
		buff.setEndian(true); buff.replaceInt(ISO.F1SIZE, cpos); cpos += 4;
		ctx.volumeTimestamp = null;
		if(ctx.matchFlag) {
			//Forge timestamp	
			aval = ctx.isoInfo.attr.get(MyupkgConstants.XML_ATTR_FAKETIME);
			if(aval != null) ctx.volumeTimestamp = MyuCD.readXMLTimestamp(aval);
			else ctx.volumeTimestamp = CDDateTime.now();
		}
		else ctx.volumeTimestamp = CDDateTime.now();
		byte[] tsBin = MyuCD.serializeDateTimeBin(ctx.volumeTimestamp);
		tsBin[0] -= 100; //No idea why, but relative to 2000 for volume and root dir?
		for(int i = 0; i < tsBin.length; i++) buff.replaceByte(tsBin[i], cpos++);
		buff.replaceByte((byte)0x02, cpos++); //Flag as directory
		cpos += 2;
		buff.setEndian(false); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.setEndian(true); buff.replaceShort((short)1, cpos); cpos += 2;
		buff.replaceByte((byte)0x01, cpos++); cpos++; //Name is just empty
		//No XA stuff for this one.
		
		for(int i = 0; i < 128; i++) buff.replaceByte((byte)0x20, cpos++); //Volume Set ID
		//Publisher
		String pubstr = "(Unknown Publisher)";
		aval = ctx.isoInfo.attr.get(MyupkgConstants.XML_ATTR_PUBID);
		if(aval != null) pubstr = aval;
		for(int i = 0; i < pubstr.length(); i++) buff.replaceByte((byte)(pubstr.charAt(i)), cpos++);
		pad = 128 - pubstr.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		
		//Data prep ID (this happens to be the stamp date)
		String wstr = String.format("%04d_%02d_%02d", ctx.volumeTimestamp.getYear(), ctx.volumeTimestamp.getMonth(), ctx.volumeTimestamp.getDay());
		for(int i = 0; i < wstr.length(); i++) buff.replaceByte((byte)(wstr.charAt(i)), cpos++);
		pad = 128 - wstr.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		
		//"PLAYSTATION" again for application ID
		for(int i = 0; i < SYS_IDSTR.length(); i++) buff.replaceByte((byte)(SYS_IDSTR.charAt(i)), cpos++);
		pad = 128 - SYS_IDSTR.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		
		//Copyright (just repeat publisher). It should be a file name according to nocash buuuuuut it isn't on this disk.
		for(int i = 0; i < pubstr.length(); i++) buff.replaceByte((byte)(pubstr.charAt(i)), cpos++);
		pad = 37 - pubstr.length();
		for(int i = 0; i < pad; i++) buff.replaceByte((byte)0x20, cpos++);
		for(int i = 0; i < 37; i++) buff.replaceByte((byte)0x20, cpos++); //Abstract file name
		for(int i = 0; i < 37; i++) buff.replaceByte((byte)0x20, cpos++); //Bibliography file name
		
		FileBuffer tstr = ctx.volumeTimestamp.serializeForCD();
		tstr.replaceByte((byte)'0', 15);
		for(int i = 0; i < 17; i++) buff.replaceByte((byte)tstr.getByte(i), cpos++);
		for(int i = 0; i < 17; i++) buff.replaceByte((byte)tstr.getByte(i), cpos++); 
		for(int j = 0; j < 2; j++) {
			for(int i = 0; i < 16; i++) buff.replaceByte((byte)0x30, cpos++); 
			buff.replaceByte((byte)0x00, cpos++); 
		}
		buff.replaceByte((byte)0x01, cpos++); //File structure version
		cpos += 142; //Reserved/application use
		for(int i = 0; i < CDXA_IDSTR.length(); i++) buff.replaceByte((byte)(CDXA_IDSTR.charAt(i)), cpos++);
		
		//Leave everything else zero
		
		//Checksums
		if(!ISOUtils.updateSectorChecksumsM2F1(buff)) {
			MyuPackagerLogger.logMessage("IsoBuild.serializeVolDescSector", "Sector checksum update failed!");
		}
		
		return buff;
	}
	
	private static Map<String, String> checksumTable2Map(String[][] checksumTable){
		if(checksumTable == null) return null;
		Map<String, String> map = new HashMap<String, String>();
		
		for(int i = 0; i < checksumTable.length; i++) {
			if(checksumTable[i][0].startsWith("#")) continue;
			map.put(checksumTable[i][1], checksumTable[i][2]);
		}
		
		return map;
	}
	
	private static List<WFile> prepFileInfo(CDBuildContext ctx, Map<String, String> checksumMap) throws IOException, NoSuchAlgorithmException{
		List<WFile> outlist = new ArrayList<WFile>(32);
		
		int trackNo = 1;
		int currentSec = ctx.baseDirSec + 1;
		for(WTrack track : ctx.tracks) {
			MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
					"Now preprocessing track " + trackNo + "...");
			if(trackNo == 1) track.startSec = 0;
			else track.startSec = currentSec;
			
			boolean first = true;
			for(LiteNode cdfile : track.files) {
				String fname = cdfile.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
				MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
						"Now preprocessing " + fname + "...");
				
				WFile trg = new WFile();
				outlist.add(trg);
				trg.fileName = fname;
				trg.startSec = currentSec;
				
				//Check for lead-in
				String aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_LEADIN);
				if(aval != null) {
					trg.leadInSize = MyuCD.readSectorTimeString(aval);
					trg.startSec += trg.leadInSize;
					if(first) track.startSec = trg.startSec;
					currentSec = trg.startSec;
				}
				
				if(first) {
					if(fname.endsWith(".DA")) track.isAudio = true;
				}
				
				String addType = cdfile.attr.get(MyupkgConstants.XML_ATTR_CDFILETYPE);
				if(addType == null) addType = MyupkgConstants.XML_CDFILETYPE_FILE;
				if(addType.equals(MyupkgConstants.XML_CDFILETYPE_ARC)) {
					trg.filePath = ctx.build_dir + File.separator + fname;
				}
				else {
					trg.filePath = MyuArcCommon.unixRelPath2Local(ctx.wd, cdfile.value);
				}
				if(!FileBuffer.fileExists(trg.filePath)) {
					MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
							"ERROR: Import target \"" + trg.filePath + "\" does not exist!");
					return null;
				}
				
				trg.fileSize = FileBuffer.fileSize(trg.filePath);
				FileTime modtime = Files.getLastModifiedTime(Paths.get(trg.filePath));
				if(ctx.matchFlag && (checksumMap != null)) {
					String trgHash = checksumMap.get(fname);
					if(trgHash != null) {
						trgHash = trgHash.toLowerCase();
						MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
								"Calculating SHA256 to check match...");
						
						byte[] in_sha = FileBuffer.getFileHash("SHA-256", trg.filePath);
						trg.hash = FileUtils.bytes2str(in_sha);
						
						MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
								"\tImport file hash: " + trg.hash);
						MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
								"\tExpected file hash: " + trgHash);
						if(trgHash.equals(trg.hash)) {
							MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
									"\t[O]" + fname + " match OK!");
						}
						else {
							MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
									"\t[X]WARNING: Match failed for " + fname + "!");
						}
					}
					else {
						MyuPackagerLogger.logMessage("IsoBuild.prepFileInfo", 
								"WARNING: File could not be found in match hash table. Build will likely not match!");
					}
				}
				
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_FILEPERM);
				if(aval != null) {
					trg.perm = Integer.parseUnsignedInt(aval, 16);
				}
				
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_OWNERGROUP);
				if(aval != null) trg.groupId = Integer.parseInt(aval);
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_OWNERUSER);
				if(aval != null) trg.userId = Integer.parseInt(aval);
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_FILENO);
				if(aval != null) trg.fileNo = Integer.parseInt(aval);
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_FORM);
				if(aval != null && aval.equals("2")) trg.isForm2 = true;
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_INTERLEAVED);
				if(aval != null && aval.equals("True")) trg.interleaved = true;
				
				trg.timestamp = MyuCD.convertFileTime(modtime);
				if(ctx.matchFlag) {
					aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_FAKETIME);
					if(aval != null) trg.timestamp = MyuCD.readXMLTimestamp(aval);
				}

				//Calculate number of sectors to advance
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_EMBEDTYPE);
				if(aval != null) {
					if(aval.equals(MyupkgConstants.XML_EMBEDTYPE_XASTR)) {
						trg.fsizeSecs = ((int)trg.fileSize + (ISO.SECSIZE - 1)) / ISO.SECSIZE;
						trg.embedType = 'X';
					}
					else if(aval.equals(MyupkgConstants.XML_EMBEDTYPE_DA)) {
						trg.fsizeSecs = ((int)trg.fileSize + (ISO.SECSIZE - 1)) / ISO.SECSIZE;
						trg.embedType = 'A';
					}
					else trg.fsizeSecs = ((int)trg.fileSize + 0x7ff) >>> 11;
				}
				else trg.fsizeSecs = ((int)trg.fileSize + 0x7ff) >>> 11;
				currentSec += trg.fsizeSecs;
		
				//Lead out?
				aval = cdfile.attr.get(MyupkgConstants.XML_ATTR_LEADOUT);
				if(aval != null) {
					trg.leadOutSize = MyuCD.readSectorTimeString(aval);
					currentSec += trg.leadOutSize;
				}
				
				first = false;
			}
			trackNo++;
		}
		
		return outlist;
	}
	
	private static long writeDirEntry(FileBuffer rawSector, long cpos, WFile entry, boolean matchFlag) throws IOException {
		//Don't forget to tack file version (;1) to the end of each file name.
		String wname = entry.fileName + ";" + entry.ver;
		int nlen = wname.length();
		int rlen = 33 + 14 + nlen;
		if((nlen & 0x1) == 0) rlen++;
		
		rawSector.replaceByte((byte)rlen, cpos++);
		rawSector.replaceByte((byte)0x00, cpos++);
		rawSector.setEndian(false); rawSector.replaceInt(entry.startSec, cpos); cpos += 4;
		rawSector.setEndian(true); rawSector.replaceInt(entry.startSec, cpos); cpos += 4;
		
		int useSize = entry.fsizeSecs << 11; //Even if DA or XA apparently.
		if(entry.embedType == 'S') {
			useSize = (int)entry.fileSize;
		}
		rawSector.setEndian(false); rawSector.replaceInt(useSize, cpos); cpos += 4;
		rawSector.setEndian(true); rawSector.replaceInt(useSize, cpos); cpos += 4;
		byte[] tbuff = MyuCD.serializeDateTimeBin(entry.timestamp);
		for(int j = 0; j < 7; j++) rawSector.replaceByte(tbuff[j], cpos++);
		
		rawSector.replaceByte((byte)0x00, cpos++);
		rawSector.replaceByte((byte)0x00, cpos++);
		rawSector.replaceByte((byte)0x00, cpos++);
		rawSector.setEndian(false); rawSector.replaceShort((short)1, cpos); cpos += 2;
		rawSector.setEndian(true); rawSector.replaceShort((short)1, cpos); cpos += 2;
		rawSector.replaceByte((byte)nlen, cpos++);
		for(int j = 0; j < nlen; j++) rawSector.replaceByte((byte)wname.charAt(j), cpos++);
		if((nlen & 0x1) == 0) rawSector.replaceByte((byte)0x00, cpos++); //Pad
		
		rawSector.replaceShort((short)entry.groupId, cpos); cpos += 2;
		rawSector.replaceShort((short)entry.userId, cpos); cpos += 2;
		int attr = entry.perm;
		if(entry.isForm2) attr |= 0x1000;
		if(entry.embedType != 'A') attr |= 0x0800;
		if(entry.interleaved) attr |= 0x2000;
		if(entry.embedType == 'A') attr |= 0x4000;
		if(matchFlag && entry.isForm2) {
			attr &= ~0x0800;
		}
		
		rawSector.replaceShort((short)attr, cpos); cpos += 2;
		
		rawSector.replaceByte((byte)'X', cpos++);
		rawSector.replaceByte((byte)'A', cpos++);
		rawSector.replaceByte((byte)entry.fileNo, cpos++);
		for(int j = 0; j < 5; j++) rawSector.replaceByte((byte)0x00, cpos++);
		
		return rlen;
	}
	
	private static boolean writeFileToStream(CDBuildContext ctx, WFile entry, LiteNode node) throws IOException {
		MyuPackagerLogger.logMessage("IsoBuild.writeFileToStream", 
				"Writing \"" + entry.fileName + "\"...");
		
		byte[] bufferSec = null;
		
		//Write any lead-in
		if(entry.leadInSize > 0) {
			boolean doEmpty = true;
			if(ctx.matchFlag) {
				//Do we have an existing garbage file?
				String gpath = node.attr.get(MyupkgConstants.XML_ATTR_LEADINGARBAGE);
				if(gpath != null) {
					gpath = MyuArcCommon.unixRelPath2Local(ctx.wd, gpath);
					if(FileBuffer.fileExists(gpath)) {
						FileBuffer trash = FileBuffer.createBuffer(gpath);
						if(entry.embedType == 'A') {
							//Can just dump as-is
							trash.writeToStream(ctx.out);
							ctx.currentSecAbs += entry.leadInSize;
						}
						else {
							//Have to update sector number and checksums
							long cpos = 0;
							for(int s = 0; s < entry.leadInSize; s++) {
								byte[] sec = trash.getBytes(cpos, cpos + ISO.SECSIZE);
								
								MyuCD.updateSectorNumber(sec, ctx.currentSecAbs);
								if(entry.isForm2) ISOUtils.updateSectorChecksumsM2F2(sec);
								else ISOUtils.updateSectorChecksumsM2F1(sec);
								ctx.out.write(sec);
								cpos += ISO.SECSIZE;
								ctx.currentSecAbs++;
							}	
						}
						trash.dispose();
						doEmpty = false;
					}
					else {
						MyuPackagerLogger.logMessage("IsoBuild.writeFileToStream", 
								"Lead-in garbage data from \"" + gpath + "\" not found! Results may not match!");
					}
				}
			}
			
			if(doEmpty) {
				//Write empty sectors in style of incoming file
				if(entry.embedType == 'A') {
					//Just zeros.
					for(int s = 0; s < entry.leadInSize; s++) {
						for(int j = 0; j < ISO.SECSIZE; j++) ctx.out.write(0);
						ctx.currentSecAbs++;
					}
				}
				else {
					if(entry.isForm2) bufferSec = MyuCD.genDummySecBase_M2F2();
					else bufferSec = MyuCD.genDummySecBaseI_M2F1();
					
					for(int s = 0; s < entry.leadInSize; s++) {
						MyuCD.updateSectorNumber(bufferSec, ctx.currentSecAbs);
						if(entry.isForm2) ISOUtils.updateSectorChecksumsM2F2(bufferSec);
						else ISOUtils.updateSectorChecksumsM2F1(bufferSec);
						ctx.out.write(bufferSec);
						ctx.currentSecAbs++;
					}
				}
			}
		}
		
		//Load and copy file into stream
		if(entry.embedType == 'S') {
			//Data loaded into buffer
			FileBuffer buffer = FileBuffer.createBuffer(entry.filePath);
			ctx.currentSecAbs += MyuCD.writeDataFileToCDStream_M2F1(ctx.out, buffer, ctx.currentSecAbs, (byte)0x00, true);
		}
		else if(entry.embedType == 'X') {
			//Checksums and sector heads need to be updated
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(entry.filePath));
			ctx.currentSecAbs += MyuCD.copyXAStreamToCD(ctx.out, bis, ctx.currentSecAbs);
			bis.close();
		}
		else if(entry.embedType == 'A') {
			//Can just copy as-is to output stream
			int spos = 0;
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(entry.filePath));
			int b = -1;
			while((b = bis.read()) != -1) {
				ctx.out.write(b);
				if(++spos >= ISO.SECSIZE) {
					spos = 0;
					ctx.currentSecAbs++;
				}
			}
			bis.close();
			if(spos > 0) {
				while(spos++ < ISO.SECSIZE) ctx.out.write(0);
			}
		}
		else {
			MyuPackagerLogger.logMessage("IsoBuild.writeFileToStream", 
					"ERROR: Did not recognize embed type " + entry.embedType);
			return false;
		}
		
		
		//Write any lead-out
		if(entry.leadOutSize > 0) {
			if(entry.embedType == 'A') {
				//Just zeros.
				for(int s = 0; s < entry.leadOutSize; s++) {
					for(int j = 0; j < ISO.SECSIZE; j++) ctx.out.write(0);
					ctx.currentSecAbs++;
				}
			}
			else {
				if(entry.isForm2) bufferSec = MyuCD.genDummySecBase_M2F2();
				else bufferSec = MyuCD.genDummySecBaseI_M2F1();
				for(int j = 0x10; j < ISO.SECSIZE; j++) bufferSec[j] = 0;
				
				for(int s = 0; s < entry.leadOutSize; s++) {
					MyuCD.updateSectorNumber(bufferSec, ctx.currentSecAbs);
					//if(entry.isForm2) ISOUtils.updateSectorChecksumsM2F2(bufferSec);
					//else ISOUtils.updateSectorChecksumsM2F1(bufferSec);
					ctx.out.write(bufferSec);
					ctx.currentSecAbs++;
				}
			}
		}
		
		return true;
	}
	
	private static void writeCueSheet(CDBuildContext ctx) throws IOException {
		String cuePath = null;
		int dot = ctx.output_iso.lastIndexOf('.');
		if(dot >= 0) {
			cuePath = ctx.output_iso.substring(0, dot) + ".cue";
		}
		else cuePath = ctx.output_iso + ".cue";
		
		int lastslash = ctx.output_iso.lastIndexOf(File.separator);
		String imgname = ctx.output_iso;
		if(lastslash >= 0) imgname = ctx.output_iso.substring(lastslash + 1);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(cuePath));
		bw.write("FILE \"");
		bw.write(imgname);
		bw.write("\"BINARY\n");
		int tno = 1;
		for(WTrack track : ctx.tracks) {
			bw.write(String.format("\tTRACK %02d ", tno));
			if(track.isAudio) bw.write("AUDIO\n");
			else bw.write("MODE2/2352\n");
			bw.write("\t\tINDEX 01 ");
			bw.write(MyuCD.sectorNumberToTimeString(track.startSec));
			bw.write("\n");
		}
		bw.write("\n");
		bw.close();
		
	}
	
	private static boolean writeCDImage(CDBuildContext ctx) throws IOException, NoSuchAlgorithmException {
		ctx.out = new BufferedOutputStream(new FileOutputStream(ctx.output_iso));
		
		//Sectors 0-3 ("Empty", though J region have a weird 0x30 pattern)
		if(ctx.region == 'J') ctx.currentSecAbs += MyuCD.writeDummySecsJToCDStream(ctx.out, ctx.currentSecAbs, 4);
		else ctx.currentSecAbs += MyuCD.writeDummySecsIToCDStream(ctx.out, ctx.currentSecAbs, 4);
		
		//Sector 4 (Sony tag string)
		//Can just reuse previous dummy buffer.
		byte[] secbuff = null;
		if(ctx.region == 'J') secbuff = MyuCD.genDummySecBaseJ_M2F1();
		else secbuff = MyuCD.genDummySecBaseI_M2F1();
		
		MyuCD.updateSectorNumber(secbuff, ctx.currentSecAbs);
		String sonyString = null;
		switch(ctx.region) {
		case 'J': sonyString = MyuCD.getSec4String_RegionJ(); break;
		case 'U': sonyString = MyuCD.getSec4String_RegionU(); break;
		case 'E': sonyString = MyuCD.getSec4String_RegionE(); break;
		}
		
		int cpos = 0x18;
		int strlen = sonyString.length();
		for(int i = 0; i < strlen; i++) {
			secbuff[cpos++] = (byte)sonyString.charAt(i);
		}
		ISOUtils.updateSectorChecksumsM2F1(secbuff);
		ctx.out.write(secbuff);
		ctx.currentSecAbs++;
		
		//PSLogo (Sectors 5-11)
		if(ctx.psLogoNode != null) {
			String binpath = ctx.wd + File.separator + ctx.psLogoNode.value;
			if(!FileBuffer.fileExists(binpath)) {
				MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
						"PSLogo file \"" + binpath + "\" could not be found. Exiting!");
				return false;
			}
			FileBuffer data = FileBuffer.createBuffer(binpath, false);
			ctx.currentSecAbs += MyuCD.writeDataFileToCDStream_M2F1(ctx.out, data, ctx.currentSecAbs, (byte)0xff, false);
		}
		else {
			//Just write 7 F2 dummy sectors
			ctx.currentSecAbs += MyuCD.writeDummySecsF2ToCDStream(ctx.out, ctx.currentSecAbs, 7);
		}
		
		//Form 2 Dummy sectors (12-15)
		ctx.currentSecAbs += MyuCD.writeDummySecsF2ToCDStream(ctx.out, ctx.currentSecAbs, 4);
		
		//-------- Prepare file entries
		
		//Load checksum table (if match)
		String[][] checksumTable = null;
		Map<String, String> checksumMap = null;
		if(ctx.matchFlag && FileBuffer.fileExists(ctx.checksum_path)) {
			MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
					"Loading checksum table...");
			checksumTable = MyuArcCommon.loadChecksumTable(ctx.checksum_path);
			checksumMap = checksumTable2Map(checksumTable);
		}
		ctx.totalSectors = ctx.baseDirSec + 1;
		List<WFile> fileList = prepFileInfo(ctx, checksumMap);
		Map<String, WFile> dirmap = new HashMap<String, WFile>(); //For sorting alphabetically
		for(WFile wf : fileList) {
			dirmap.put(wf.fileName, wf);
			ctx.totalSectors += wf.leadInSize;
			ctx.totalSectors += wf.leadOutSize;
			ctx.totalSectors += wf.fsizeSecs;
		}
		List<String> fileNameList = new ArrayList<String>(dirmap.size());
		fileNameList.addAll(dirmap.keySet());
		Collections.sort(fileNameList);
		
		//Volume descriptor and terminator (16-17)
		FileBuffer secbuff_f = serializeVolDescSector(ctx);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		
		MyuCD.resetSectorBufferM2F1I(secbuff);
		MyuCD.updateSectorNumber(secbuff, ctx.currentSecAbs);
		secbuff[0x12] = (byte)0x89;
		secbuff[0x16] = (byte)0x89;
		secbuff[0x18] = (byte)0xFF;
		cpos = 0x19;
		strlen = CD_IDSTR.length();
		for(int i = 0; i < strlen; i++) {
			secbuff[cpos++] = (byte)CD_IDSTR.charAt(i);
		}
		secbuff[cpos++] = 0x01;
		while(cpos < 0x818) secbuff[cpos++] = 0x00;
		ISOUtils.updateSectorChecksumsM2F1(secbuff);
		ctx.out.write(secbuff);
		ctx.currentSecAbs++;	
		
		//Path tables (18-21)
		MyuCD.resetSectorBufferM2F1I(secbuff_f);
		MyuCD.updateSectorNumber(secbuff_f, ctx.currentSecAbs);
		secbuff_f.replaceByte((byte)0x89, 0x12L);
		secbuff_f.replaceByte((byte)0x89, 0x16L);
		cpos = 0x18;
		secbuff_f.setEndian(false);
		secbuff_f.replaceByte((byte)0x01, cpos++);
		secbuff_f.replaceByte((byte)0x00, cpos++);
		secbuff_f.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
		secbuff_f.replaceShort((short)1, cpos); cpos += 2; //Remainder should be 0\
		ISOUtils.updateSectorChecksumsM2F1(secbuff_f);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		MyuCD.updateSectorNumber(secbuff_f, ctx.currentSecAbs);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		
		MyuCD.resetSectorBufferM2F1I(secbuff_f);
		MyuCD.updateSectorNumber(secbuff_f, ctx.currentSecAbs);
		secbuff_f.replaceByte((byte)0x89, 0x12L);
		secbuff_f.replaceByte((byte)0x89, 0x16L);
		cpos = 0x18;
		secbuff_f.setEndian(true);
		secbuff_f.replaceByte((byte)0x01, cpos++);
		secbuff_f.replaceByte((byte)0x00, cpos++);
		secbuff_f.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
		secbuff_f.replaceShort((short)1, cpos); cpos += 2;
		ISOUtils.updateSectorChecksumsM2F1(secbuff_f);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		MyuCD.updateSectorNumber(secbuff_f, ctx.currentSecAbs);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		
		//Base directory (22)
			// First two entries are basically . and ..
			// Their names are just 0x00 and 0x01 (non ASCII)
			// What even is .. for a root?
		MyuCD.resetSectorBufferM2F1I(secbuff_f);
		MyuCD.updateSectorNumber(secbuff_f, ctx.currentSecAbs);
		secbuff_f.replaceByte((byte)0x89, 0x12L);
		secbuff_f.replaceByte((byte)0x89, 0x16L);
		cpos = 0x18;
		for(int i = 0; i < 2; i++) {
			secbuff_f.replaceByte((byte)0x30, cpos++);
			secbuff_f.replaceByte((byte)0x00, cpos++);
			secbuff_f.setEndian(false); secbuff_f.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
			secbuff_f.setEndian(true); secbuff_f.replaceInt(ctx.baseDirSec, cpos); cpos += 4;
			secbuff_f.setEndian(false); secbuff_f.replaceInt(ISO.F1SIZE, cpos); cpos += 4;
			secbuff_f.setEndian(true); secbuff_f.replaceInt(ISO.F1SIZE, cpos); cpos += 4;
			byte[] tbuff = MyuCD.serializeDateTimeBin(ctx.volumeTimestamp);
			tbuff[0] -= 100;
			for(int j = 0; j < 7; j++) secbuff_f.replaceByte(tbuff[j], cpos++);
			secbuff_f.replaceByte((byte)0x02, cpos++);
			secbuff_f.replaceByte((byte)0x00, cpos++);
			secbuff_f.replaceByte((byte)0x00, cpos++);
			secbuff_f.setEndian(false); secbuff_f.replaceShort((short)1, cpos); cpos += 2;
			secbuff_f.setEndian(true); secbuff_f.replaceShort((short)1, cpos); cpos += 2;
			secbuff_f.replaceByte((byte)0x01, cpos++);
			secbuff_f.replaceByte((byte)i, cpos++);
			secbuff_f.replaceShort((short)0, cpos); cpos += 2;
			secbuff_f.replaceShort((short)0, cpos); cpos += 2;
			secbuff_f.replaceShort((short)0x8d55, cpos); cpos += 2;
			secbuff_f.replaceByte((byte)'X', cpos++);
			secbuff_f.replaceByte((byte)'A', cpos++);
			for(int j = 0; j < 6; j++) secbuff_f.replaceByte((byte)0x00, cpos++);
		}
		for(String fileName : fileNameList) {
			WFile fileInfo = dirmap.get(fileName);
			cpos += writeDirEntry(secbuff_f, cpos, fileInfo, ctx.matchFlag);
		}
		while(cpos < 0x818) secbuff_f.replaceByte((byte)0x00, cpos++);
		ISOUtils.updateSectorChecksumsM2F1(secbuff_f);
		secbuff_f.writeToStream(ctx.out);
		ctx.currentSecAbs++;
		
		//Files (23+)
		for(WTrack track : ctx.tracks) {
			for(LiteNode fnode : track.files) {
				String fileName = fnode.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
				WFile fileInfo = dirmap.get(fileName);
				if(!writeFileToStream(ctx, fileInfo, fnode)) {
					MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
							"ERROR: Failed to write \"" + fileName + "\" to output!");
					return false;
				}
			}
		}
		
		ctx.out.close();
		ctx.out = null;
		
		//Run checksum on output
		MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
				"Calculating SHA256 to check match...");
		
		byte[] in_sha = FileBuffer.getFileHash("SHA-256", ctx.output_iso);
		String fullhash = FileUtils.bytes2str(in_sha);
		
		MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
				"\tOutput image hash: " + fullhash);
		
		if(ctx.matchFlag && (checksumMap != null)) {
			String trghash = checksumMap.get("SLPM87176.iso");
			if(trghash != null) {
				trghash = trghash.toLowerCase();
				MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
						"\tTarget hash: " + trghash);
				if(trghash.equals(fullhash)) {
					MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
							"\t[O] Image hash matches!");
				}
				else {
					//TODO Check against other possible images? Like SLPM87178?
					MyuPackagerLogger.logMessage("IsoBuild.writeCDImage", 
							"\t[X] CD image match failed...");
				}
			}
		}
		
		return true;
	}
	
	public static void main_isoPack(Map<String, String> args) throws IOException, UnsupportedFileTypeException{
		CDBuildContext ctx = new CDBuildContext();
		if (!checkArgs(ctx, args)) {
			System.exit(1);
		}
		
		try {
			LiteNode specRoot = MyuArcCommon.readXML(ctx.input_xml);
			ctx.isoInfo = specRoot;
			
			String regionstr = specRoot.attr.get(MyupkgConstants.XML_ATTR_REGION);
			if(regionstr != null) {
				ctx.region = regionstr.charAt(0);
			}
			else ctx.region = 'U';
			
			String input_dir = MyuArcCommon.getContainingDir(ctx.input_xml);
			if(ctx.build_dir == null) {
				ctx.build_dir = input_dir + File.separator + "cd";
			}
			MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
					"Input directory: " + input_dir);
			
			//Find CD files
			//These have been moved into CdTrack nodes. Update.
			for(LiteNode child : specRoot.children) {
				if(child.name == null) continue;
				if(child.name.equals(MyupkgConstants.XML_NODENAME_CDTRACK)) {
					WTrack track = new WTrack();
					ctx.tracks.add(track);
					track.startSec = -1;
					for(LiteNode gchild : child.children) {
						if(gchild.name.equals(MyupkgConstants.XML_NODENAME_CDFILE)) {
							track.files.add(gchild);
						}
						else if(gchild.name.equals(MyupkgConstants.XML_NODENAME_PSLOGO)) {
							if(ctx.psLogoNode == null) ctx.psLogoNode = gchild;
						}
					}
				}
			}
			
			if(ctx.psLogoNode == null) {
				MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
						"WARNING: PS logo node was not specified in build. Build will continue, but resulting image may not run on a PlayStation or emulator.");
			}
			
			//Calculate path table sizes and base dir location
			//For now, we do not allow nested directories, so the path table is just the root dir.
			ctx.ptSize = 10;
			for(int i = 0; i < 4; i++) ctx.ptSecs[i] = 18 + i;
			ctx.baseDirSec = 22;
			ctx.totalSectors = ctx.baseDirSec;
			
			//Calculate total size. Do arc builds if needed.
			//Don't forget to check file name compatibility.
			for(WTrack track : ctx.tracks) {
				for(LiteNode node : track.files) {
					String abspath = MyuArcCommon.unixRelPath2Local(ctx.wd, node.value);
					
					//Check file name validity
					String fname = node.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
					if(fname == null) {
						int lastslash = node.value.lastIndexOf('/');
						if(lastslash >= 0) {
							fname = node.value.substring(lastslash+1);
						}
						else fname = node.value;
					}
					
					String[] fnsplit = fname.split("\\.");
					if(fnsplit.length >= 2) {
						if(fnsplit[0].length() > 8) {
							fnsplit[0] = fnsplit[0].substring(0, 8).toUpperCase();
						}
						if(fnsplit[1].length() > 3) {
							fnsplit[1] = fnsplit[1].substring(0, 3).toUpperCase();
						}
						fname = fnsplit[0] + "." + fnsplit[1];
					}
					else {
						//Presumably no extension
						if(fnsplit[0].length() > 8) {
							fnsplit[0] = fnsplit[0].substring(0, 8).toUpperCase();
						}
						fname = fnsplit[0];
					}
					node.attr.put(MyupkgConstants.XML_ATTR_FILENAME, fname);

					//Build archive, if needed.
					String intype = node.attr.get(MyupkgConstants.XML_ATTR_CDFILETYPE);
					if(intype == null) intype = MyupkgConstants.XML_CDFILETYPE_FILE;
					if(intype.equals(MyupkgConstants.XML_CDFILETYPE_ARC)) {
						String trgpath = ctx.build_dir + File.separator + fname;
						if(ctx.buildAllFlag || !FileBuffer.fileExists(trgpath)) {
							String arcname = fname.replace("D_", "").toLowerCase().trim();
							int dot = arcname.lastIndexOf('.');
							if(dot >= 0) arcname = arcname.substring(0, dot);
							arcname = StringUtils.capitalize(arcname);
							
							Map<String, String> arcArgs = new HashMap<String, String>();
							arcArgs.put("arcspec", abspath);
							arcArgs.put("output", trgpath);
							arcArgs.put("hout", ctx.incl_dir + File.separator + "Res" + arcname + ".h");
							arcArgs.put("cout", ctx.src_dir + File.separator + "Res" + arcname + ".c");
							if(ctx.matchFlag) arcArgs.put("match", "true");
							
							MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
									"Building \"" + trgpath + "\"...");
							ArcBuild.main_arcPack(arcArgs);
							
							if(!FileBuffer.fileExists(trgpath)) {
								MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
										"Included archive \"" + trgpath + "\" failed to build. Exiting!");
								System.exit(1);
							}
						}
					}
					else {
						if(!ctx.arcOnlyFlag && !FileBuffer.fileExists(abspath)) {
							MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
									"Included file \"" + abspath + "\" could not be found. Exiting!");
							System.exit(1);
						}
					}
				}	
			}
			
			if(ctx.arcOnlyFlag) return;
			
			//Start writing to the CD output
			if(!writeCDImage(ctx)) {
				MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
						"ISO build failed! See log for details.");
				if(ctx.out != null) ctx.out.close();
				System.exit(1);
			}
			else {
				MyuPackagerLogger.logMessage("IsoBuild.main_isoPack", 
						"ISO build completed without any caught errors!");
			}
			
			writeCueSheet(ctx);
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
