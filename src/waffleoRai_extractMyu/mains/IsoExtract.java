package waffleoRai_extractMyu.mains;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Containers.CDTable.CDInvalidRecordException;
import waffleoRai_Containers.ISO;
import waffleoRai_Containers.XATable;
import waffleoRai_Containers.XATable.XAEntry;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileUtils;
import waffleoRai_extractMyu.CDExtractContext;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuCD;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.MyupkgConstants;
import waffleoRai_extractMyu.mains.ArcExtract.ArcExtractContext;

public class IsoExtract {
	
	public static class RFile implements Comparable<RFile>{
		public String name;
		public LiteNode infoNode;
		
		public int startSector;
		public long rawLength;
		public int secLen;
		
		public boolean equals(Object o) {
			return (this == o);
		}
		
		public int compareTo(RFile o) {
			if(o == null) return 1;
			return startSector - o.startSector;
		}
	}
	
	private static boolean checkArgs(Map<String, String> args, CDExtractContext ctx) throws IOException {
		ctx.isoPath = args.get("iso");
		ctx.cdOutputDir = args.get("cdout");
		ctx.assetOutputDir = args.get("assetout");
		ctx.arcSpecDir = args.get("arcspec"); //Arcspec dir
		ctx.xmlPath = args.get("xmlout");
		ctx.checksumPath = args.get("checksums");
		
		if(ctx.isoPath == null) {
			//Check for cue sheet instead
			ctx.cuePath = args.get("cue");
			if(ctx.cuePath == null){
				MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
						"Input file argument is required!");
				return false;
			}
			if(!FileBuffer.fileExists(ctx.cuePath)){
				MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
						"Input file \"" + ctx.cuePath + "\" does not exist!");
				return false;
			}
		}
		else {
			if(!FileBuffer.fileExists(ctx.isoPath)) {
				MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
						"Input file \"" + ctx.isoPath + "\" does not exist!");
			}
		}
		
		if(ctx.cuePath != null) {
			MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
					"Attempting to find image binary from CUE file \"" + ctx.cuePath + "\"");
			ctx.isoPath = MyuArcCommon.findBIN_fromCUE(ctx.cuePath);
			if(ctx.isoPath == null) {
				MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
						"Image BIN could not be found! Returning...");
				return false;
			}
		}
		
		if(ctx.cuePath != null) {
			ctx.wd = MyuArcCommon.getContainingDir(ctx.cuePath);
		}
		else {
			ctx.wd = MyuArcCommon.getContainingDir(ctx.isoPath);
		}
		
		
		if(ctx.cdOutputDir == null) {
			ctx.cdOutputDir = ctx.wd + File.separator + "cd";
			MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
					"CD files directory was not provided. Creating directory in input folder (" + ctx.cdOutputDir + ")");
		}
		if(ctx.assetOutputDir == null) {
			ctx.assetOutputDir = ctx.wd + File.separator + "assets";
			MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
					"Assets directory was not provided. Creating directory in input folder (" + ctx.assetOutputDir + ")");
		}
		
		if(!FileBuffer.directoryExists(ctx.cdOutputDir)) {
			Files.createDirectories(Paths.get(ctx.cdOutputDir));
		}
		if(!FileBuffer.directoryExists(ctx.assetOutputDir)) {
			Files.createDirectories(Paths.get(ctx.assetOutputDir));
		}
		
		if((ctx.checksumPath == null) || (!FileBuffer.fileExists(ctx.checksumPath))) {
			ctx.checksumPath = ctx.wd + File.separator + "checksums.csv";
			MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
					"Checksum table path was not provided, or table does not exist. Setting to " + ctx.checksumPath);
		}

		if(ctx.xmlPath == null) {
			ctx.xmlPath = ctx.wd + File.separator + "buildiso.xml";
			MyuPackagerLogger.logMessage("IsoExtract.checkArgs", 
					"Output XML path was not provided. Setting to \"" + ctx.xmlPath + "\"");
		}
		else ctx.wd = MyuArcCommon.getContainingDir(ctx.xmlPath);

		return true;
	}
	
	private static void runChecksum(CDExtractContext ctx) throws IOException {
		String[][] checksums = MyuArcCommon.loadChecksumTable(ctx.checksumPath); //Returns null if file doesn't exist.
		if(checksums != null) {
			try {
				MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
						"Calculating SHA-256 of input image...");
				byte[] in_sha = FileBuffer.getFileHash("SHA-256", ctx.isoPath);
				String shastr = FileUtils.bytes2str(in_sha);
				shastr = shastr.toUpperCase();
				MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
						"Hash: " + shastr);
				
				//Check for match
				boolean found = false;
				for(int i = 0; i < checksums.length; i++) {
					if(checksums[i][0] == null) continue;
					if(checksums[i][2] == null) continue;
					if(!checksums[i][0].equalsIgnoreCase("ISO")) continue;
					if(checksums[i][2].equals(shastr)) {
						MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
								"Image match found: " + checksums[i][1]);
						found = true;
						break;
					}
				}
				
				if(!found) {
					MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
							"WARNING: No match was found to known image. Unpack will continue, but be aware that the results will be unpredictable.");
				}
				
			} catch (NoSuchAlgorithmException e) {
				MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
						"Input file hash failed: internal error (see stack trace)");
				e.printStackTrace();
			} catch (IOException e) {
				MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
						"Input file hash failed: I/O error (see stack trace)");
				e.printStackTrace();
			}
		}
		else {
			MyuPackagerLogger.logMessage("IsoExtract.runChecksum", 
					"Checksum table could not be loaded. Skipping input image checksum...");
		}
	}
	
	private static void extractVolumeInfo(CDExtractContext ctx) {
		ctx.cdDirRel = MyuArcCommon.localPath2UnixRel(ctx.xmlPath, ctx.cdOutputDir);
		ctx.assetDirRel = MyuArcCommon.localPath2UnixRel(ctx.xmlPath, ctx.assetOutputDir);
		
		//Get metadata from CD
		ctx.infoRoot.attr.put(MyupkgConstants.XML_ATTR_VOLUMEID, ctx.cdImage.getVolumeIdent().trim());
		ctx.infoRoot.attr.put(MyupkgConstants.XML_ATTR_PUBID, ctx.cdImage.getPublisherIdent().trim());
		ctx.infoRoot.attr.put(MyupkgConstants.XML_ATTR_REGION, "J");
		//cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_MATCHMODE, "True");
		ctx.infoRoot.attr.put(MyupkgConstants.XML_ATTR_FAKETIME, MyuArcCommon.datetime2XMLVal(ctx.cdImage.getDateCreated()));
	}
	
	public static boolean extractFileTo(String cdPath, RFile fileInfo, String dumpPath) throws IOException {
		Files.deleteIfExists(Paths.get(dumpPath));
		long stPos = (long)fileInfo.startSector * ISO.SECSIZE;
		long edPos = stPos + (long)fileInfo.secLen * ISO.SECSIZE;
		FileBuffer buff = FileBuffer.createBuffer(cdPath, stPos, edPos);
		if(fileInfo.name.equals("MOVIE.BIN") || fileInfo.name.endsWith(".STR")) {
			//Sector coords and checksums must be CLEANSED
			//VOICE.STR is Form 2
			//MOVIE.BIN is Form1/2 hybrid because sony hates you
			long cpos = 0;
			for(int s = 0; s < fileInfo.secLen; s++) {
				for(int j = 0xc; j <= 0xe; j++) buff.replaceByte((byte)0, cpos+j);
				int submode = Byte.toUnsignedInt(buff.getByte(cpos+0x12));
				if((submode & 0x20) != 0) {
					for(int j = 0x92c; j < ISO.SECSIZE; j++) buff.replaceByte((byte)0, cpos+j);
				}
				else {
					for(int j = 0x818; j < ISO.SECSIZE; j++) buff.replaceByte((byte)0, cpos+j);
				}
				cpos += ISO.SECSIZE;
			}
			buff.writeFile(dumpPath); buff.dispose();
			return true;
		}
		else if(fileInfo.name.endsWith(".DA")) {
			buff.writeFile(dumpPath); buff.dispose();
			return true;
		}
		else {
			//I could use the FileNode... I guess. Or I could do it the hard way :)
			long wCount = 0;
			buff.setCurrentPosition(0L);
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dumpPath));
			while(wCount < fileInfo.rawLength) {
				buff.skipBytes(0x18);
				for(int i = 0; i < 0x800; i++) {
					if(wCount >= fileInfo.rawLength) break;
					bos.write(Byte.toUnsignedInt(buff.nextByte()));
					wCount++;
				}
				buff.skipBytes(0x118);
			}
			bos.close();
			return true;
		}
	}
	
	private static void importFileTable(CDExtractContext ctx) {
		XATable cd_table = ctx.cdImage.getXATable();
		List<XAEntry> cd_table_entries = new LinkedList<XAEntry>();
		cd_table_entries.addAll(cd_table.getXAEntries());
		Collections.sort(cd_table_entries);
		ctx.files = new ArrayList<RFile>(cd_table_entries.size()+1);
		for(XAEntry e : cd_table_entries) {
			if(e.isRawFile()) continue;
			String fname = e.getName();
			RFile trg = new RFile();
			ctx.files.add(trg);
			
			trg.name = fname;
			if(fname.equals("MYUVOICE.DA")) {
				//Spwn new parent track
				LiteNode tnode = ctx.infoRoot.newChild(MyupkgConstants.XML_NODENAME_CDTRACK);
				trg.infoNode = tnode.newChild(MyupkgConstants.XML_NODENAME_CDFILE);
			}
			else {
				trg.infoNode = ctx.currentTrack.newChild(MyupkgConstants.XML_NODENAME_CDFILE);
			}
			
			boolean is_arc = false;
			if(fname.startsWith("D_") || fname.endsWith(".STR") || fname.equals("MOVIE.BIN")) {
				is_arc = true;
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_ARC);
			}
			else trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_FILE);
			
			trg.startSector = e.getStartBlock();
			trg.rawLength = e.getFileSize();
			
			//Determine embed type and length in sectors
			trg.secLen = (int)trg.rawLength + 0x7ff >>> 11;
			if(fname.equals("MOVIE.BIN")) {
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_EMBEDTYPE, MyupkgConstants.XML_EMBEDTYPE_XASTR);
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FILENO, Integer.toString(e.getFileNumber()));
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_MODE, "2");
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FORM, "1");
			}
			else if(fname.endsWith(".STR")) {
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_EMBEDTYPE, MyupkgConstants.XML_EMBEDTYPE_XASTR);
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FILENO, Integer.toString(e.getFileNumber()));
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_MODE, "2");
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FORM, "2");
			}
			else if(fname.endsWith(".DA")) {
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_EMBEDTYPE, MyupkgConstants.XML_EMBEDTYPE_DA);
			}
			else {
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_EMBEDTYPE, MyupkgConstants.XML_EMBEDTYPE_STD);
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_MODE, "2");
				trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FORM, "1");
			}
			
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FILENAME, fname);
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(trg.startSector));
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FAKETIME, MyuArcCommon.datetime2XMLVal(e.getDate()));
			
			//Perm (write is always unset)
			int perm_u = 0;
			if(e.ownerRead()) perm_u |= 0x4;
			if(e.ownerExecute()) perm_u |= 0x1;
			
			int perm_g = 0;
			if(e.groupRead()) perm_g |= 0x4;
			if(e.groupExecute()) perm_g |= 0x1;
			
			int perm_a = 0;
			if(e.AllRead()) perm_a |= 0x4;
			if(e.AllExecute()) perm_a |= 0x1;
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_FILEPERM, String.format("%d%d%d", perm_u, perm_g, perm_a));
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_OWNERGROUP, Integer.toString(e.getOwnerGroupID()));
			trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_OWNERUSER, Integer.toString(e.getOwnerUserID()));
			if(e.isInterleaved()) trg.infoNode.attr.put(MyupkgConstants.XML_ATTR_INTERLEAVED, "True");
			
			//Value
			if(is_arc) {
				//determine name of xml file
				String xmlname = fname;
				int cidx = xmlname.lastIndexOf('.');
				if(cidx >= 0) {
					xmlname = xmlname.substring(0, cidx);
				}
				xmlname = xmlname.replace("D_", "");
				xmlname = xmlname.toLowerCase();
				
				trg.infoNode.value = ctx.assetDirRel + "/" + xmlname + ".xml";
			}
			else {
				//Put the exe and cfg in the cd dir. 
				//Anything else goes in the asset dir, but the only thing that should include is the cd da stream.
				if(fname.endsWith(".CNF") || fname.startsWith("SLPM")) {
					trg.infoNode.value = ctx.cdDirRel + "/" + fname;
				}
				else trg.infoNode.value = ctx.assetDirRel + "/" + fname;
			}
		}
		Collections.sort(ctx.files);
	}

	private static void extractFiles(CDExtractContext ctx) throws IOException {
		int lastEndSec = -1;
		int ldiSec = -1;
		RFile lastFile = null;
		for(RFile fileInfo : ctx.files) {
			MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
					"Working on \"" + fileInfo.name + "\"...");
			
			//Look for gaps. Only seem to be between tracks?
			String ldiGarbagePath = null;
			if(lastFile != null) {
				//Check for leadout and leadin
				int dist = fileInfo.startSector - lastEndSec;
				int leadOut = dist/2;
				if(leadOut > 0) {
					lastFile.infoNode.attr.put(MyupkgConstants.XML_ATTR_LEADOUT, MyuCD.sectorNumberToTimeString(leadOut));
					int leadIn = dist - leadOut;
					fileInfo.infoNode.attr.put(MyupkgConstants.XML_ATTR_LEADIN, MyuCD.sectorNumberToTimeString(leadIn));
					if(fileInfo.name.equals("MYUVOICE.DA")) {
						ldiSec = fileInfo.startSector - leadIn;
						ldiGarbagePath = ctx.cdOutputDir + File.separator + "s" + ldiSec + ".bin";
						fileInfo.infoNode.attr.put(MyupkgConstants.XML_ATTR_LEADINGARBAGE, ctx.cdDirRel + "/s" + ldiSec + ".bin");
					}
				}
			}
			
			//First extract from CD image to CD staging directory
			String dumpPath = ctx.cdOutputDir + File.separator + fileInfo.name;
			if(!extractFileTo(ctx.isoPath, fileInfo, dumpPath)) {
				MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
						"ERROR -- Failed to retrieve data for file \"" + fileInfo.name + "\"! Terminating...");
				return;
			}	
			
			//If there is an arcspec, break down archive.
			String ftype = fileInfo.infoNode.attr.get(MyupkgConstants.XML_ATTR_CDFILETYPE);
			if(ftype == null) {
				ftype = MyupkgConstants.XML_CDFILETYPE_FILE;
				fileInfo.infoNode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, ftype);
			}
			if(ftype.equals(MyupkgConstants.XML_CDFILETYPE_ARC)) {
				//If there is no arcspec, but it is an archive, copy to asset dir
				//Look for arcspec
				String specName = fileInfo.name.replace("D_", "").toLowerCase().trim();
				int dot = specName.lastIndexOf('.');
				if(dot >= 0) specName = specName.substring(0, dot);
				String specFilePath = ctx.arcSpecDir + File.separator + specName + ".xml";
				if(FileBuffer.fileExists(specFilePath)) {
					MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
							"Arc spec file found! Attempting unpack...");
					
					ArcExtractContext aectx = new ArcExtractContext();
					aectx.input_path = dumpPath;
					aectx.output_dir = ctx.assetOutputDir + File.separator + specName;
					aectx.rel_path = "./" + specName;
					aectx.spec_path = specFilePath;
					aectx.xml_path = ctx.assetOutputDir + File.separator + specName + ".xml";
					
					ArcExtract.unpackArchive(aectx);
					
					MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
							"Archive extraction complete. Deleting " + dumpPath + "...");
					Files.deleteIfExists(Paths.get(dumpPath));
				}
				else {
					MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
							"WARNING -- File \"" + fileInfo.name + "\" marked as archive, but spec xml was not found. Dumping whole...");
					fileInfo.infoNode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_FILE);
					String assetPath = ctx.assetOutputDir + File.separator + fileInfo.name;
					if(FileBuffer.fileExists(dumpPath)) {
						Files.deleteIfExists(Paths.get(assetPath));
						Files.copy(Paths.get(dumpPath), Paths.get(assetPath));
						Files.deleteIfExists(Paths.get(dumpPath));
					}
					fileInfo.infoNode.value = ctx.assetDirRel + "/" + fileInfo.name;
				}
			}
			else {
				//If it's not the executable or config file, move to asset dir.
				if(!fileInfo.name.endsWith(".CNF") && !fileInfo.name.startsWith("SLPM")) {
					String assetPath = ctx.assetOutputDir + File.separator + fileInfo.name;
					if(FileBuffer.fileExists(dumpPath)) {
						Files.deleteIfExists(Paths.get(assetPath));
						Files.copy(Paths.get(dumpPath), Paths.get(assetPath));
						Files.deleteIfExists(Paths.get(dumpPath));
					}
				}
			}
			
			if(ldiGarbagePath != null) {
				MyuPackagerLogger.logMessage("IsoExtract.extractFiles", 
						"Extracting Track 02 leadin garbage to " + ldiGarbagePath + "...");
				//Extract leadin garbage
				long stPos = (long)ldiSec * ISO.SECSIZE;
				long edPos = (long)fileInfo.startSector * ISO.SECSIZE;
				FileBuffer buffer = FileBuffer.createBuffer(ctx.isoPath, stPos, edPos);
				buffer.writeFile(ldiGarbagePath); buffer.dispose();
			}
			
			lastEndSec = fileInfo.startSector + fileInfo.secLen;
			lastFile = fileInfo;
		}
		
	}
	
	public static void main_isoUnpack(Map<String, String> args) throws IOException, CDInvalidRecordException, UnsupportedFileTypeException{
		CDExtractContext ctx = new CDExtractContext();
		
		if(!checkArgs(args, ctx)) return;
	
		runChecksum(ctx);
		
		//Attempt to read CD image
		ctx.cdImage = MyuArcCommon.readISOFile(ctx.isoPath);
		
		//start output XML for CD build
		ctx.infoRoot = new LiteNode();
		ctx.infoRoot.name = MyupkgConstants.XML_NODENAME_ISOBUILD;
		extractVolumeInfo(ctx);
		ctx.currentTrack = ctx.infoRoot.newChild(MyupkgConstants.XML_NODENAME_CDTRACK); //Track 1
		
		//Extract PSLogo sectors (5-11)
		String pslogo_path = ctx.cdOutputDir + File.separator + MyupkgConstants.FILENAME_PSLOGO;
		MyuCD.extractPSLogo(ctx.cdImage, pslogo_path);
		LiteNode childnode = ctx.currentTrack.newChild(MyupkgConstants.XML_NODENAME_PSLOGO);
		childnode.value = ctx.cdDirRel + "/" + MyupkgConstants.FILENAME_PSLOGO;
		
		//Get file table from CD (need to annotate file locations and metadata)
		importFileTable(ctx);
		
		//Extract sectors that are non-zero and not used by the filesystem(?)
		//Render file tree from CD and extract those files to cd out dir (note that track 2 might not be where it's supposed to be - CUE sheet can specify if present)
		//Run the arc extractors on the archives and streams. (Exe disassembly is handled externally)
		//Clean up ARC bins in cddir that are successfully broken down
		extractFiles(ctx);
		
		MyuArcCommon.writeXML(ctx.xmlPath, ctx.infoRoot);
		
	}
	
}
